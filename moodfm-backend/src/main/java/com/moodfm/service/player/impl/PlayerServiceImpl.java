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
import com.moodfm.service.player.impl.playlist.PlaylistRadioHelper;
import com.moodfm.service.player.impl.queue.RadioQueueMaintenanceService;
import com.moodfm.service.player.impl.queue.RadioQueueStore;
import com.moodfm.service.player.impl.recall.CandidateRecallService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
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
    private final PlaylistRadioHelper playlistRadioHelper;
    private final RadioQueueMaintenanceService radioQueueMaintenanceService;

    /** Virtual Thread executor for background tasks (Feature 2: queue auto-refill) */
    private final java.util.concurrent.ExecutorService bgExecutor = Executors.newVirtualThreadPerTaskExecutor();

    @PreDestroy
    public void shutdown() {
        bgExecutor.close();
    }

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

        // 4. 6 路并行召回 + 过滤链 + 用户偏好
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

        // 5. 6 路并行召回（复用现有管道，关键词已偏向种子歌曲）
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

        MoodParams moodParams = playlistRadioHelper.buildMoodParamsFromPlaylist(playlist, playlistTracks);

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
        List<SongVO> merged = playlistRadioHelper.mergePlaylistWithRecalled(playlistTracks, recalled);

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

    // ===================== getNextBatch (Feature 2: 消费 + 自动补充) =====================

    @Override
    public List<SongVO> getNextBatch(Long userId, Long sessionId) {
        // 原子弹出：每首歌逐个消费，避免并发问题
        List<SongVO> result = radioQueueStore.popBatch(userId, 5);

        if (result.isEmpty()) return result;

        radioQueueMaintenanceService.reRankIfNeeded(userId, sessionId, bgExecutor);

        Long remaining = radioQueueStore.size(userId);
        if (remaining != null && remaining < radioQueueMaintenanceService.refillThreshold()) {
            radioQueueMaintenanceService.refillIfBelowThreshold(userId, bgExecutor);
        }

        return result;
    }

    @Override
    public String getSongUrl(Long userId, String platform, String songId) {
        return playUrlService.getSongUrl(userId, platform, songId);
    }

    @Override
    public void reRankIfNeeded(Long userId, Long sessionId) {
        radioQueueMaintenanceService.reRankIfNeeded(userId, sessionId, bgExecutor);
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
