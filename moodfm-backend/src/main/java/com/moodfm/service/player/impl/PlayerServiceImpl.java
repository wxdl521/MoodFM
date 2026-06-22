package com.moodfm.service.player.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodfm.ai.model.MoodParams;
import com.moodfm.common.exception.BizException;
import com.moodfm.common.result.ResultCode;
import com.moodfm.common.util.AesUtil;
import com.moodfm.domain.dto.radio.MoodInputRequest;
import com.moodfm.domain.entity.*;
import com.moodfm.domain.vo.RadioQueueVO;
import com.moodfm.domain.vo.SessionSummaryVO;
import com.moodfm.domain.vo.SongVO;
import com.moodfm.mapper.*;
import com.moodfm.service.ai.MoodAnalysisService;
import com.moodfm.service.player.impl.ranking.AiRankingService;
import com.moodfm.service.platform.PlatformBindingService;
import com.moodfm.service.playlist.PlaylistService;
import com.moodfm.service.player.PlayerService;
import com.moodfm.domain.vo.PlaylistVO;
import com.moodfm.service.player.impl.catalog.SongCatalogService;
import com.moodfm.service.player.impl.playurl.PlayUrlService;
import com.moodfm.service.player.impl.queue.RadioQueueStore;
import com.moodfm.service.player.impl.recall.CandidateRecallService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerServiceImpl implements PlayerService {

    private final MoodAnalysisService moodAnalysisService;
    private final PlatformBindingService platformBindingService;
    private final MoodSessionMapper sessionMapper;
    private final SongMapper songMapper;
    private final PlatformSongMappingMapper platformSongMappingMapper;
    private final AesUtil aesUtil;
    private final ObjectMapper objectMapper;
    private final AiRankingService aiRankingService;
    private final CandidateRecallService candidateRecallService;
    private final PlayUrlService playUrlService;
    private final SongCatalogService songCatalogService;
    private final RadioQueueStore radioQueueStore;
    private final PlaylistService playlistService;

    /** Virtual Thread executor for background tasks (Feature 2: queue auto-refill) */
    private final java.util.concurrent.ExecutorService bgExecutor = Executors.newVirtualThreadPerTaskExecutor();

    @PreDestroy
    public void shutdown() {
        bgExecutor.close();
    }

    private static final int RERANK_EVERY = 3;
    private static final int REFILL_THRESHOLD = 5;
    private static final int RERANK_NEXT_N = 5;

    // ===================== getRecentSessions =====================

    @Override
    public List<SessionSummaryVO> getRecentSessions(Long userId, int limit) {
        List<Map<String, Object>> rows = sessionMapper.selectRecentSessions(userId, Math.min(limit, 20));
        return rows.stream().map(r -> SessionSummaryVO.builder()
                .id(toLong(r.get("id")))
                .rawInput((String) r.get("rawInput"))
                .scene((String) r.get("scene"))
                .startedAt(r.get("startedAt") instanceof LocalDateTime ldt ? ldt : null)
                .build()).toList();
    }

    // ===================== startRadio =====================

    @Override
    public RadioQueueVO startRadio(Long userId, MoodInputRequest request) {
        // 1. AI 心情分析
        MoodParams moodParams = moodAnalysisService.analyze(request);

        // 2. 创建 MoodSession
        MoodSession session = new MoodSession();
        session.setUserId(userId);
        session.setRawInput(request.getText());
        session.setScene(request.getScene() != null ? request.getScene() : moodParams.getSceneInferred());
        try { session.setMoodParams(objectMapper.writeValueAsString(moodParams)); } catch (Exception ignored) {}
        session.setDurationMinutes(request.getDurationMinutes());
        sessionMapper.insert(session);

        // 2.5 存储会话时长 TTL
        // durationMinutes == null  → "无限"，封顶 24h TTL（保留 marker，让 isSessionExpired 在 24h 内返回 false）
        // durationMinutes != null  → 设置对应秒数的 TTL
        Integer durationMinutes = request.getDurationMinutes();
        if (durationMinutes == null) {
            // 无限时长：marker 封顶 24 小时，避免 Redis 中永不过期的 key 无限堆积。
            // 行为变化：连续播放超过 24h 的会话会被判定过期，需重新开台（可接受）。
            radioQueueStore.markSessionTtlInfinite(session.getId());
        } else {
            radioQueueStore.markSessionTtlSeconds(session.getId(), durationMinutes * 60);
        }

        // 3. 获取默认绑定平台 + cookie
        PlatformBinding binding = platformBindingService.getDefaultBinding(userId);
        String cookie = aesUtil.decrypt(binding.getCookieEncrypted());
        String platform = binding.getPlatform();

        // 4. 5 路并行召回 + 反馈过滤 + 用户偏好
        List<SongVO> candidates = candidateRecallService.recallSongs(platform, cookie, moodParams, userId);

        // 5. AI 重排 + 生成推荐理由
        List<SongVO> ranked = aiRankingService.rank(candidates, moodParams);

        // 5.5 批量获取播放地址
        playUrlService.enrichWithPlayUrls(ranked, platform, cookie);

        // 6. 持久化歌曲 + 平台映射（Feature 3 前置）
        songCatalogService.persistSongs(ranked, platform);

        // 7. 存入 Redis 队列（原子替换，避免 delete + rightPushAll 之间的竞态窗口）
        radioQueueStore.replaceQueue(userId, ranked);

        // 8. 重置重排计数器
        radioQueueStore.resetRerankCounter(userId);

        String moodSummary = aiRankingService.buildMoodSummary(moodParams);

        return RadioQueueVO.builder()
                .sessionId(session.getId())
                .scene(session.getScene())
                .moodSummary(moodSummary)
                .songs(ranked)
                .totalCount(ranked.size())
                .build();
    }

    // ===================== startRadioFromSong =====================

    @Override
    public RadioQueueVO startRadioFromSong(Long userId, Long songId) {
        // 1. 从 DB 获取歌曲信息
        Song seedSong = songMapper.selectById(songId);
        if (seedSong == null) {
            throw new BizException(ResultCode.RECALL_FAILED, "歌曲不存在");
        }

        // 2. 用歌曲的 artist + title 构造搜索关键词，作为 MoodParams 的偏好
        String keywords = (seedSong.getArtist() != null ? seedSong.getArtist() : "")
                + " " + (seedSong.getTitle() != null ? seedSong.getTitle() : "");

        MoodParams moodParams = MoodParams.defaultParams();
        List<String> vibeKw = new ArrayList<>();
        if (seedSong.getArtist() != null) vibeKw.add(seedSong.getArtist());
        moodParams.setVibeKeywords(vibeKw);
        List<String> genreKw = new ArrayList<>();
        // 尝试从 features JSON 中提取 genre
        if (seedSong.getFeatures() != null && !seedSong.getFeatures().isBlank()) {
            try {
                JsonNode features = objectMapper.readTree(seedSong.getFeatures());
                String genre = features.path("genre").asText(null);
                if (genre != null && !genre.isBlank()) genreKw.add(genre);
            } catch (Exception ignored) {}
        }
        if (genreKw.isEmpty()) genreKw.add(seedSong.getTitle() != null ? seedSong.getTitle() : "music");
        moodParams.setPreferredGenres(genreKw);

        // 3. 创建 MoodSession
        MoodSession session = new MoodSession();
        session.setUserId(userId);
        session.setRawInput("基于歌曲: " + keywords.trim());
        session.setScene("song-seed");
        try { session.setMoodParams(objectMapper.writeValueAsString(moodParams)); } catch (Exception ignored) {}
        session.setDurationMinutes(30);
        sessionMapper.insert(session);

        // 3.5 存储会话时长 TTL（默认 30 分钟）
        radioQueueStore.markSessionTtlSeconds(session.getId(), 30 * 60);

        // 4. 获取默认绑定平台 + cookie
        PlatformBinding binding = platformBindingService.getDefaultBinding(userId);
        String cookie = aesUtil.decrypt(binding.getCookieEncrypted());
        String platform = binding.getPlatform();

        // 5. 5 路并行召回（复用现有管道，关键词已偏向种子歌曲）
        List<SongVO> candidates = candidateRecallService.recallSongs(platform, cookie, moodParams, userId);

        // 6. 过滤掉种子歌曲本身
        String seedPlatformId = null;
        PlatformSongMapping seedMapping = platformSongMappingMapper.selectOne(
                new LambdaQueryWrapper<PlatformSongMapping>()
                        .eq(PlatformSongMapping::getSongId, songId)
                        .last("LIMIT 1"));
        if (seedMapping != null) {
            seedPlatformId = seedMapping.getPlatformSongId();
        }
        final String excludeId = seedPlatformId;
        if (excludeId != null) {
            candidates = candidates.stream()
                    .filter(s -> !excludeId.equals(s.getPlatformSongId()))
                    .collect(Collectors.toList());
        }

        // 7. AI 重排
        List<SongVO> ranked = aiRankingService.rank(candidates, moodParams);

        // 7.5 批量获取播放地址
        playUrlService.enrichWithPlayUrls(ranked, platform, cookie);

        // 8. 持久化 + 存入 Redis 队列
        songCatalogService.persistSongs(ranked, platform);

        radioQueueStore.replaceQueue(userId, ranked);

        radioQueueStore.resetRerankCounter(userId);

        return RadioQueueVO.builder()
                .sessionId(session.getId())
                .scene("song-seed")
                .moodSummary("基于「" + seedSong.getTitle() + " · " + seedSong.getArtist() + "」生成")
                .songs(ranked)
                .totalCount(ranked.size())
                .build();
    }

    // ===================== startRadioFromPlaylist =====================

    @Override
    public RadioQueueVO startRadioFromPlaylist(Long userId, String playlistId, Integer durationMinutes) {
        PlaylistVO playlist = playlistService.getPlaylist(userId, playlistId);
        List<SongVO> playlistTracks = playlist.getTracks();
        if (playlistTracks == null || playlistTracks.isEmpty()) {
            throw new BizException(ResultCode.RECALL_FAILED, "歌单为空或无法加载曲目");
        }

        String platform = playlist.getPlatform();
        if (platform == null || platform.isBlank()) {
            String[] parts = playlistId.split(":", 2);
            platform = parts.length == 2 ? parts[0] : null;
        }
        if (platform == null || platform.isBlank()) {
            throw new BizException(ResultCode.RECALL_FAILED, "无法识别歌单所属平台");
        }

        MoodParams moodParams = buildMoodParamsFromPlaylist(playlist, playlistTracks);

        MoodSession session = new MoodSession();
        session.setUserId(userId);
        session.setRawInput("基于歌单: " + (playlist.getName() != null ? playlist.getName() : playlistId));
        session.setScene("playlist");
        try { session.setMoodParams(objectMapper.writeValueAsString(moodParams)); } catch (Exception ignored) {}
        session.setDurationMinutes(durationMinutes != null ? durationMinutes : 30);
        sessionMapper.insert(session);

        if (durationMinutes == null) {
            radioQueueStore.markSessionTtlInfinite(session.getId());
        } else {
            radioQueueStore.markSessionTtlSeconds(session.getId(), durationMinutes * 60);
        }

        PlatformBinding binding = platformBindingService.getValidBinding(userId, platform);
        String cookie = aesUtil.decrypt(binding.getCookieEncrypted());

        List<SongVO> recalled = candidateRecallService.recallSongs(platform, cookie, moodParams, userId);
        List<SongVO> merged = mergePlaylistWithRecalled(playlistTracks, recalled);

        List<SongVO> ranked = aiRankingService.rank(merged, moodParams);
        playUrlService.enrichWithPlayUrls(ranked, platform, cookie);
        songCatalogService.persistSongs(ranked, platform);

        radioQueueStore.replaceQueue(userId, ranked);
        radioQueueStore.resetRerankCounter(userId);

        String playlistName = playlist.getName() != null ? playlist.getName() : "歌单";
        return RadioQueueVO.builder()
                .sessionId(session.getId())
                .scene("playlist")
                .moodSummary("基于歌单「" + playlistName + "」生成")
                .songs(ranked)
                .totalCount(ranked.size())
                .build();
    }

    private MoodParams buildMoodParamsFromPlaylist(PlaylistVO playlist, List<SongVO> tracks) {
        MoodParams moodParams = MoodParams.defaultParams();
        moodParams.setSceneInferred("playlist");

        LinkedHashSet<String> artists = new LinkedHashSet<>();
        LinkedHashSet<String> genres = new LinkedHashSet<>();
        double valenceSum = 0;
        double energySum = 0;
        int featureCount = 0;

        int limit = Math.min(tracks.size(), 15);
        for (int i = 0; i < limit; i++) {
            SongVO track = tracks.get(i);
            if (track.getArtist() != null && !track.getArtist().isBlank()) {
                artists.add(track.getArtist().trim());
            }
            if (track.getId() != null) {
                Song dbSong = songMapper.selectById(track.getId());
                if (dbSong != null && dbSong.getFeatures() != null && !dbSong.getFeatures().isBlank()) {
                    try {
                        JsonNode features = objectMapper.readTree(dbSong.getFeatures());
                        String genre = features.path("genre").asText(null);
                        if (genre != null && !genre.isBlank()) genres.add(genre);
                        if (features.has("valence")) {
                            valenceSum += features.path("valence").asDouble(0.5);
                            energySum += features.path("energy").asDouble(0.5);
                            featureCount++;
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        if (genres.isEmpty() && playlist.getName() != null && !playlist.getName().isBlank()) {
            genres.add(playlist.getName().trim());
        }
        if (genres.isEmpty()) genres.add("歌单");

        moodParams.setVibeKeywords(artists.stream().limit(6).collect(Collectors.toList()));
        moodParams.setPreferredGenres(genres.stream().limit(5).collect(Collectors.toList()));

        if (featureCount > 0 && moodParams.getMood() != null) {
            moodParams.getMood().setValence(valenceSum / featureCount);
            moodParams.getMood().setEnergy(energySum / featureCount);
        }

        return moodParams;
    }

    private List<SongVO> mergePlaylistWithRecalled(List<SongVO> playlistTracks, List<SongVO> recalled) {
        LinkedHashMap<String, SongVO> byKey = new LinkedHashMap<>();
        for (SongVO track : playlistTracks) {
            String key = songDedupeKey(track);
            if (key != null) byKey.putIfAbsent(key, track);
        }
        for (SongVO song : recalled) {
            String key = songDedupeKey(song);
            if (key != null) byKey.putIfAbsent(key, song);
        }
        return new ArrayList<>(byKey.values());
    }

    private String songDedupeKey(SongVO song) {
        if (song.getPlatformSongId() != null && !song.getPlatformSongId().isBlank()) {
            String p = song.getPlatform() != null ? song.getPlatform() : "";
            return p + ":" + song.getPlatformSongId();
        }
        if (song.getTitle() != null && song.getArtist() != null) {
            return song.getTitle() + "|" + song.getArtist();
        }
        return null;
    }

    // ===================== getNextBatch (Feature 2: 消费 + 自动补充) =====================

    @Override
    public List<SongVO> getNextBatch(Long userId, Long sessionId) {
        // 原子弹出：每首歌逐个消费，避免并发问题
        List<SongVO> result = radioQueueStore.popBatch(userId, 5);

        if (result.isEmpty()) return result;

        // Feature 1: 更新重排计数器，检查是否需要重排
        reRankIfNeeded(userId, sessionId);

        // Feature 2: 队列长度 < 阈值 → 后台补充
        Long remaining = radioQueueStore.size(userId);
        if (remaining != null && remaining < REFILL_THRESHOLD) {
            CompletableFuture.runAsync(() -> refillQueue(userId), bgExecutor);
        }

        return result;
    }

    @Override
    public String getSongUrl(Long userId, String platform, String songId) {
        return playUrlService.getSongUrl(userId, platform, songId);
    }

    // ===================== Feature 1: 动态重排 =====================

    /**
     * 每消费 RERANK_EVERY 首歌，触发一次重排：重新召回 + AI 排序 next RERANK_NEXT_N 首。
     */
    @Override
    public void reRankIfNeeded(Long userId, Long sessionId) {
        if (userId == null) return;
        long count = radioQueueStore.incrementAndExpireRerankCounter(userId);
        if (count >= RERANK_EVERY) {
            radioQueueStore.resetRerankCounter(userId);
            // 异步重排，不阻塞当前请求
            CompletableFuture.runAsync(() -> doReRank(userId, sessionId), bgExecutor);
            log.info("Triggered re-rank for user {} after {} songs", userId, count);
        }
    }

    private void doReRank(Long userId, Long sessionId) {
        try {
            PlatformBinding binding = platformBindingService.getDefaultBinding(userId);
            String cookie = aesUtil.decrypt(binding.getCookieEncrypted());
            String platform = binding.getPlatform();

            // 从 DB 加载当前 session 的 MoodParams
            MoodParams mood = loadMoodFromSession(sessionId);
            if (mood == null) {
                log.warn("Cannot re-rank: session {} mood params not found", sessionId);
                return;
            }

            // 召回候选 + 反馈过滤 + 用户偏好
            List<SongVO> candidates = candidateRecallService.recallSongs(platform, cookie, mood, userId);

            // 去掉已在队列中的歌曲
            Set<String> existingIds = radioQueueStore.peekIds(userId);
            List<SongVO> fresh = candidates.stream()
                    .filter(s -> s.getPlatformSongId() != null && !existingIds.contains(s.getPlatformSongId()))
                    .collect(Collectors.toList());

            if (fresh.isEmpty()) {
                log.info("Re-rank: no new candidates for user {}", userId);
                return;
            }

            // AI 重排
            List<SongVO> ranked = aiRankingService.rank(fresh, mood);
            List<SongVO> nextBatch = ranked.stream().limit(RERANK_NEXT_N).collect(Collectors.toList());

            // 批量获取播放地址
            playUrlService.enrichWithPlayUrls(nextBatch, platform, cookie);

            // 持久化新歌曲
            songCatalogService.persistSongs(nextBatch, platform);

            // 插入到队列最前面（优先播放重排结果）
            radioQueueStore.prependBatch(userId, nextBatch);

            log.info("Re-ranked {} songs prepended to queue for user {}", nextBatch.size(), userId);
        } catch (Exception e) {
            log.error("Re-rank failed for user {}", userId, e);
        }
    }

    // ===================== Feature 2: 队列自动补充 =====================

    private void refillQueue(Long userId) {
        try {
            Long currentSize = radioQueueStore.size(userId);
            if (currentSize != null && currentSize >= REFILL_THRESHOLD) return; // 并发安全：二次检查

            PlatformBinding binding = platformBindingService.getDefaultBinding(userId);
            String cookie = aesUtil.decrypt(binding.getCookieEncrypted());
            String platform = binding.getPlatform();

            MoodParams mood = loadLatestMood(userId);
            List<SongVO> candidates = candidateRecallService.recallSongs(platform, cookie, mood, userId);

            // 去掉已在队列中的
            Set<String> existingIds = radioQueueStore.peekIds(userId);
            List<SongVO> fresh = candidates.stream()
                    .filter(s -> s.getPlatformSongId() != null && !existingIds.contains(s.getPlatformSongId()))
                    .limit(20)
                    .collect(Collectors.toList());

            if (fresh.isEmpty()) return;

            // 批量获取播放地址
            playUrlService.enrichWithPlayUrls(fresh, platform, cookie);

            songCatalogService.persistSongs(fresh, platform);

            radioQueueStore.appendBatch(userId, fresh);

            log.info("Refilled queue with {} songs for user {}", fresh.size(), userId);
        } catch (Exception e) {
            log.warn("Queue refill failed for user {}", userId, e);
        }
    }

    // ===================== 辅助方法 =====================

    private MoodParams loadMoodFromSession(Long sessionId) {
        if (sessionId == null) return null;
        try {
            MoodSession session = sessionMapper.selectById(sessionId);
            if (session != null && session.getMoodParams() != null) {
                return objectMapper.readValue(session.getMoodParams(), MoodParams.class);
            }
        } catch (Exception e) {
            log.warn("Failed to load mood from session {}", sessionId, e);
        }
        return null;
    }

    private MoodParams loadLatestMood(Long userId) {
        try {
            MoodSession session = sessionMapper.selectOne(
                    new LambdaQueryWrapper<MoodSession>()
                            .eq(MoodSession::getUserId, userId)
                            .isNotNull(MoodSession::getMoodParams)
                            .orderByDesc(MoodSession::getStartedAt)
                            .last("LIMIT 1"));
            if (session != null && session.getMoodParams() != null) {
                return objectMapper.readValue(session.getMoodParams(), MoodParams.class);
            }
        } catch (Exception e) {
            log.warn("Failed to load latest mood for user {}", userId, e);
        }
        return MoodParams.defaultParams();
    }

    private Long toLong(Object val) {
        if (val == null) return null;
        if (val instanceof Long l) return l;
        if (val instanceof Number n) return n.longValue();
        try { return Long.parseLong(val.toString()); } catch (Exception e) { return null; }
    }

    // ===================== Session Duration Control =====================

    @Override
    public void setSessionDuration(Long sessionId, int minutes) {
        if (sessionId == null || minutes < 1) return;
        int seconds = minutes * 60;
        radioQueueStore.markSessionTtlSeconds(sessionId, seconds);
        log.info("Updated session {} duration to {} minutes", sessionId, minutes);
    }

    @Override
    public boolean isSessionExpired(Long sessionId) {
        if (sessionId == null) return false;
        // key 不存在 = 已过期或未设置
        return radioQueueStore.isExpired(sessionId);
    }
}
