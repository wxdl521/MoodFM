package com.moodfm.service.player.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodfm.ai.model.MoodParams;
import com.moodfm.client.music.MusicApiClient;
import com.moodfm.common.constant.RedisKeys;
import com.moodfm.common.exception.BizException;
import com.moodfm.common.result.ResultCode;
import com.moodfm.common.util.AesUtil;
import com.moodfm.common.util.MusicResponseParser;
import com.moodfm.domain.dto.radio.MoodInputRequest;
import com.moodfm.domain.entity.*;
import com.moodfm.domain.vo.PreferencesVO;
import com.moodfm.domain.vo.RadioQueueVO;
import com.moodfm.domain.vo.SessionSummaryVO;
import com.moodfm.domain.vo.SongVO;
import com.moodfm.mapper.*;
import com.moodfm.service.ai.LlmClient;
import com.moodfm.service.ai.LlmFallbackMetrics;
import com.moodfm.service.ai.MoodAnalysisService;
import com.moodfm.service.embedding.EmbeddingService;
import com.moodfm.service.enrich.SongFeatureService;
import com.moodfm.service.platform.PlatformBindingService;
import com.moodfm.service.player.PlayerService;
import com.moodfm.service.user.UserService;
import com.moodfm.service.vector.QdrantService;
import com.moodfm.service.vector.VectorRecallMetrics;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerServiceImpl implements PlayerService {

    private final MoodAnalysisService moodAnalysisService;
    private final PlatformBindingService platformBindingService;
    private final MusicApiClient musicApiClient;
    private final MoodSessionMapper sessionMapper;
    private final FeedbackEventMapper feedbackEventMapper;
    private final SongMapper songMapper;
    private final PlatformSongMappingMapper platformSongMappingMapper;
    private final UserProfileMapper userProfileMapper;
    private final UserService userService;
    private final AesUtil aesUtil;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final LlmClient llmClient;
    private final LlmFallbackMetrics llmFallbackMetrics;
    private final EmbeddingService embeddingService;
    private final QdrantService qdrantService;
    private final VectorRecallMetrics vectorRecallMetrics;
    private final GlobalBlacklistMapper globalBlacklistMapper;
    private final SongFeatureService songFeatureService;

    /** Configurable total timeout (seconds) for concurrent song-feature enrichment in persistSongs. */
    @Value("${song.feature.enrich.timeout-seconds:8}")
    private int enrichTimeoutSeconds;

    /** Virtual Thread executor for background tasks (Feature 2: queue auto-refill) */
    private final java.util.concurrent.ExecutorService bgExecutor = Executors.newVirtualThreadPerTaskExecutor();

    @PreDestroy
    public void shutdown() {
        bgExecutor.close();
    }

    private static final int RERANK_EVERY = 3;
    private static final int REFILL_THRESHOLD = 5;
    private static final int RERANK_NEXT_N = 5;

    // ===================== Recall scoring constants (T1-1 + T1-2) =====================

    // Source weights — adjust here to tune recall blend
    private static final double W_LIKED     = 1.0;
    private static final double W_VECTOR    = 0.9;
    private static final double W_RECOMMEND = 0.8;
    private static final double W_GENRE     = 0.6;
    private static final double W_VIBE      = 0.6;
    private static final double W_EXPLORE   = 0.4;

    // Emotion-match score constants
    /** Score returned when features are null/unparseable — neutral, neither reward nor penalise */
    static final double UNKNOWN_EMOTION    = 0.5;
    private static final double GENRE_BONUS     = 0.3;
    private static final double LANGUAGE_BONUS  = 0.2;
    private static final double AVOID_PENALTY   = 10.0;
    /** Small random jitter to break exact ties without overriding structural order */
    private static final double JITTER          = 0.05;

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
        String ttlKey = RedisKeys.format(RedisKeys.SESSION_TTL, session.getId());
        Integer durationMinutes = request.getDurationMinutes();
        if (durationMinutes == null) {
            // 无限时长：marker 封顶 24 小时，避免 Redis 中永不过期的 key 无限堆积。
            // 行为变化：连续播放超过 24h 的会话会被判定过期，需重新开台（可接受）。
            redisTemplate.opsForValue().set(ttlKey, "infinite", Duration.ofHours(24));
        } else {
            redisTemplate.opsForValue().set(ttlKey, String.valueOf(durationMinutes * 60), Duration.ofSeconds(durationMinutes * 60L));
        }

        // 3. 获取默认绑定平台 + cookie
        PlatformBinding binding = platformBindingService.getDefaultBinding(userId);
        String cookie = aesUtil.decrypt(binding.getCookieEncrypted());
        String platform = binding.getPlatform();

        // 4. 5 路并行召回 + 反馈过滤 + 用户偏好
        List<SongVO> candidates = recallSongs(platform, cookie, moodParams, userId);

        // 5. AI 重排 + 生成推荐理由
        List<SongVO> ranked = rankWithAI(candidates, moodParams);

        // 5.5 批量获取播放地址
        enrichWithPlayUrls(ranked, platform, cookie);

        // 6. 持久化歌曲 + 平台映射（Feature 3 前置）
        persistSongs(ranked, platform);

        // 7. 存入 Redis 队列（原子替换，避免 delete + rightPushAll 之间的竞态窗口）
        String queueKey = RedisKeys.format(RedisKeys.USER_QUEUE, userId);
        try {
            if (!ranked.isEmpty()) {
                List<String> serialized = new ArrayList<>();
                for (SongVO s : ranked) {
                    serialized.add(objectMapper.writeValueAsString(s));
                }
                // Use Redis transaction to atomically replace the queue
                List<Object> txResults = redisTemplate.execute(new SessionCallback<>() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public List<Object> execute(RedisOperations operations) {
                        operations.multi();
                        operations.delete(queueKey);
                        operations.opsForList().rightPushAll(queueKey, serialized);
                        return operations.exec();
                    }
                });
                redisTemplate.expire(queueKey, Duration.ofHours(2));
            } else {
                redisTemplate.delete(queueKey);
            }
        } catch (Exception e) {
            log.warn("Failed to cache queue", e);
        }

        // 8. 重置重排计数器
        redisTemplate.delete(RedisKeys.format(RedisKeys.QUEUE_RERANK, userId));

        String moodSummary = buildMoodSummary(moodParams);

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
        String ttlKey = RedisKeys.format(RedisKeys.SESSION_TTL, session.getId());
        redisTemplate.opsForValue().set(ttlKey, String.valueOf(30 * 60), Duration.ofSeconds(30 * 60L));

        // 4. 获取默认绑定平台 + cookie
        PlatformBinding binding = platformBindingService.getDefaultBinding(userId);
        String cookie = aesUtil.decrypt(binding.getCookieEncrypted());
        String platform = binding.getPlatform();

        // 5. 5 路并行召回（复用现有管道，关键词已偏向种子歌曲）
        List<SongVO> candidates = recallSongs(platform, cookie, moodParams, userId);

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
        List<SongVO> ranked = rankWithAI(candidates, moodParams);

        // 7.5 批量获取播放地址
        enrichWithPlayUrls(ranked, platform, cookie);

        // 8. 持久化 + 存入 Redis 队列
        persistSongs(ranked, platform);

        String queueKey = RedisKeys.format(RedisKeys.USER_QUEUE, userId);
        try {
            if (!ranked.isEmpty()) {
                List<String> serialized = new ArrayList<>();
                for (SongVO s : ranked) {
                    serialized.add(objectMapper.writeValueAsString(s));
                }
                // Use Redis transaction to atomically replace the queue
                redisTemplate.execute(new SessionCallback<>() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public List<Object> execute(RedisOperations operations) {
                        operations.multi();
                        operations.delete(queueKey);
                        operations.opsForList().rightPushAll(queueKey, serialized);
                        return operations.exec();
                    }
                });
                redisTemplate.expire(queueKey, Duration.ofHours(2));
            } else {
                redisTemplate.delete(queueKey);
            }
        } catch (Exception e) {
            log.warn("Failed to cache queue", e);
        }

        redisTemplate.delete(RedisKeys.format(RedisKeys.QUEUE_RERANK, userId));

        return RadioQueueVO.builder()
                .sessionId(session.getId())
                .scene("song-seed")
                .moodSummary("基于「" + seedSong.getTitle() + " · " + seedSong.getArtist() + "」生成")
                .songs(ranked)
                .totalCount(ranked.size())
                .build();
    }

    // ===================== getNextBatch (Feature 2: 消费 + 自动补充) =====================

    @Override
    public List<SongVO> getNextBatch(Long userId, Long sessionId) {
        String queueKey = RedisKeys.format(RedisKeys.USER_QUEUE, userId);

        // 原子弹出：每首歌逐个消费，避免并发问题
        List<SongVO> result = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            String raw = redisTemplate.opsForList().leftPop(queueKey);
            if (raw == null) break;
            try {
                result.add(objectMapper.readValue(raw, SongVO.class));
            } catch (Exception e) {
                log.warn("Failed to deserialize queued song", e);
            }
        }

        if (result.isEmpty()) return result;

        // Feature 1: 更新重排计数器，检查是否需要重排
        reRankIfNeeded(userId, sessionId);

        // Feature 2: 队列长度 < 阈值 → 后台补充
        Long remaining = redisTemplate.opsForList().size(queueKey);
        if (remaining != null && remaining < REFILL_THRESHOLD) {
            CompletableFuture.runAsync(() -> refillQueue(userId), bgExecutor);
        }

        return result;
    }

    @Override
    public String getSongUrl(Long userId, String platform, String songId) {
        PlatformBinding binding = platformBindingService.getValidBinding(userId, platform);
        String cookie = aesUtil.decrypt(binding.getCookieEncrypted());
        String url = musicApiClient.getSongUrl(platform, songId, cookie);
        if (url != null && !url.isBlank()) return url;

        // Fallback: look up the song in DB and try Netease
        if ("qqmusic".equals(platform)) {
            url = fallbackToNetease(songId, null, null, null);
        }
        if (url == null || url.isBlank()) throw new BizException(ResultCode.RECALL_FAILED, "获取播放地址失败");
        return url;
    }

    /**
     * Try to get a play URL from Netease by searching the song title + artist.
     * Returns null if the fallback also fails.
     */
    private String fallbackToNetease(String qqSongId, String title, String artist, String cookie) {
        try {
            // If title/artist not provided, look up from DB via platform mapping
            if ((title == null || title.isBlank()) && qqSongId != null) {
                PlatformSongMapping mapping = platformSongMappingMapper.selectOne(
                        new LambdaQueryWrapper<PlatformSongMapping>()
                                .eq(PlatformSongMapping::getPlatformSongId, qqSongId)
                                .eq(PlatformSongMapping::getPlatform, "qqmusic")
                                .last("LIMIT 1"));
                if (mapping != null) {
                    Song song = songMapper.selectById(mapping.getSongId());
                    if (song != null) {
                        title = song.getTitle();
                        artist = song.getArtist();
                    }
                }
            }
            if (title == null || title.isBlank()) return null;

            String query = title + (artist != null && !artist.isBlank() ? " " + artist : "");
            List<SongVO> hits = fetchSearch("netease", query, 5);
            if (hits.isEmpty()) return null;

            String neteaseId = hits.get(0).getPlatformSongId();
            if (neteaseId == null) return null;

            return musicApiClient.getSongUrl("netease", neteaseId, null);
        } catch (Exception e) {
            log.warn("Netease fallback failed for QQ song {}", qqSongId, e);
            return null;
        }
    }

    // ===================== Feature 1: 动态重排 =====================

    /**
     * 每消费 RERANK_EVERY 首歌，触发一次重排：重新召回 + AI 排序 next RERANK_NEXT_N 首。
     */
    @Override
    public void reRankIfNeeded(Long userId, Long sessionId) {
        if (userId == null) return;
        String counterKey = RedisKeys.format(RedisKeys.QUEUE_RERANK, userId);
        Long count = redisTemplate.opsForValue().increment(counterKey);
        if (count == null) count = 1L;
        redisTemplate.expire(counterKey, Duration.ofHours(2));

        if (count != null && count >= RERANK_EVERY) {
            redisTemplate.delete(counterKey);
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
            List<SongVO> candidates = recallSongs(platform, cookie, mood, userId);

            // 去掉已在队列中的歌曲
            String queueKey = RedisKeys.format(RedisKeys.USER_QUEUE, userId);
            Set<String> existingIds = peekQueueIds(userId);
            List<SongVO> fresh = candidates.stream()
                    .filter(s -> s.getPlatformSongId() != null && !existingIds.contains(s.getPlatformSongId()))
                    .collect(Collectors.toList());

            if (fresh.isEmpty()) {
                log.info("Re-rank: no new candidates for user {}", userId);
                return;
            }

            // AI 重排
            List<SongVO> ranked = rankWithAI(fresh, mood);
            List<SongVO> nextBatch = ranked.stream().limit(RERANK_NEXT_N).collect(Collectors.toList());

            // 批量获取播放地址
            enrichWithPlayUrls(nextBatch, platform, cookie);

            // 持久化新歌曲
            persistSongs(nextBatch, platform);

            // 插入到队列最前面（优先播放重排结果）
            // Reverse so the best-ranked song ends up at the list head after leftPushAll
            List<String> serialized = new ArrayList<>();
            for (SongVO s : nextBatch) {
                serialized.add(objectMapper.writeValueAsString(s));
            }
            Collections.reverse(serialized);
            redisTemplate.opsForList().leftPushAll(queueKey, serialized);

            log.info("Re-ranked {} songs prepended to queue for user {}", nextBatch.size(), userId);
        } catch (Exception e) {
            log.error("Re-rank failed for user {}", userId, e);
        }
    }

    // ===================== Feature 2: 队列自动补充 =====================

    private void refillQueue(Long userId) {
        try {
            String queueKey = RedisKeys.format(RedisKeys.USER_QUEUE, userId);
            Long currentSize = redisTemplate.opsForList().size(queueKey);
            if (currentSize != null && currentSize >= REFILL_THRESHOLD) return; // 并发安全：二次检查

            PlatformBinding binding = platformBindingService.getDefaultBinding(userId);
            String cookie = aesUtil.decrypt(binding.getCookieEncrypted());
            String platform = binding.getPlatform();

            MoodParams mood = loadLatestMood(userId);
            List<SongVO> candidates = recallSongs(platform, cookie, mood, userId);

            // 去掉已在队列中的
            Set<String> existingIds = peekQueueIds(userId);
            List<SongVO> fresh = candidates.stream()
                    .filter(s -> s.getPlatformSongId() != null && !existingIds.contains(s.getPlatformSongId()))
                    .limit(20)
                    .collect(Collectors.toList());

            if (fresh.isEmpty()) return;

            // 批量获取播放地址
            enrichWithPlayUrls(fresh, platform, cookie);

            persistSongs(fresh, platform);

            List<String> serialized = new ArrayList<>();
            for (SongVO s : fresh) {
                serialized.add(objectMapper.writeValueAsString(s));
            }
            redisTemplate.opsForList().rightPushAll(queueKey, serialized);
            redisTemplate.expire(queueKey, Duration.ofHours(2));

            log.info("Refilled queue with {} songs for user {}", fresh.size(), userId);
        } catch (Exception e) {
            log.warn("Queue refill failed for user {}", userId, e);
        }
    }

    // ===================== 召回 (Feature 4 + 5 增强) =====================

    private List<SongVO> recallSongs(String platform, String cookie, MoodParams mood) {
        return recallSongs(platform, cookie, mood, null);
    }

    /**
     * 6 路并行召回 + 来源加权 + 情绪匹配打分 + 反馈过滤(Feature 4) + 用户偏好(Feature 5) + 向量召回(Feature 6)
     * T1-1: 来源加权（替换 Collections.shuffle）
     * T1-2: 情绪匹配打分
     */
    private List<SongVO> recallSongs(String platform, String cookie, MoodParams mood, Long userId) {
        // Feature 5: 合并用户偏好到搜索关键词
        List<String> genres = mood.getPreferredGenres() != null ? new ArrayList<>(mood.getPreferredGenres()) : new ArrayList<>();
        List<String> vibes  = mood.getVibeKeywords()    != null ? new ArrayList<>(mood.getVibeKeywords())    : new ArrayList<>();
        if (userId != null) {
            mergeUserPreferences(userId, genres, vibes);
        }

        String genreKw   = genres.isEmpty() ? "music" : String.join(" ", genres.subList(0, Math.min(2, genres.size())));
        String vibeKw    = vibes.isEmpty()  ? "popular" : String.join(" ", vibes.subList(0, Math.min(2, vibes.size())));
        String sceneKw   = (mood.getSceneInferred() != null ? mood.getSceneInferred() : "放松") + " 音乐";
        String exploreKw = (genres.isEmpty() ? "indie" : genres.get(0)) + " 新歌";

        // Build vector recall query text from mood keywords
        String vectorQueryText = buildVectorQueryText(mood, genres, vibes);

        // T1-1: 去重表 + 来源权重表（同一首歌出现在多路时取最大权重）
        LinkedHashMap<String, SongVO> dedup = new LinkedHashMap<>();
        Map<String, Double> sourceWeight = new LinkedHashMap<>();

        // 6 路并行（Virtual Thread） — 原 5 路 + 向量召回
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CompletableFuture<List<SongVO>> likedFut     = CompletableFuture.supplyAsync(() -> fetchLiked(platform, cookie), executor);
            CompletableFuture<List<SongVO>> recommendFut = CompletableFuture.supplyAsync(() -> fetchRecommend(platform, cookie), executor);
            CompletableFuture<List<SongVO>> genreFut     = CompletableFuture.supplyAsync(() -> fetchSearch(platform, genreKw, 25), executor);
            CompletableFuture<List<SongVO>> vibeFut      = CompletableFuture.supplyAsync(() -> fetchSearch(platform, vibeKw, 25), executor);
            CompletableFuture<List<SongVO>> exploreFut   = CompletableFuture.supplyAsync(() -> fetchSearch(platform, exploreKw, 15), executor);
            CompletableFuture<List<SongVO>> vectorFut    = CompletableFuture.supplyAsync(() -> fetchVectorSimilar(vectorQueryText), executor);
            try {
                CompletableFuture.allOf(likedFut, recommendFut, genreFut, vibeFut, exploreFut, vectorFut)
                        .get(12, TimeUnit.SECONDS);
                addSource(dedup, sourceWeight, safeGet(likedFut,     "liked"),         W_LIKED);
                addSource(dedup, sourceWeight, safeGet(recommendFut, "recommend"),     W_RECOMMEND);
                addSource(dedup, sourceWeight, safeGet(genreFut,     "genre-search"),  W_GENRE);
                addSource(dedup, sourceWeight, safeGet(vibeFut,      "vibe-search"),   W_VIBE);
                addSource(dedup, sourceWeight, safeGet(exploreFut,   "explore-search"),W_EXPLORE);
                addSource(dedup, sourceWeight, safeGet(vectorFut,    "vector-search"), W_VECTOR);
            } catch (Exception e) {
                log.warn("Parallel recall timeout/error, using partial results", e);
                addSource(dedup, sourceWeight, safeGet(likedFut,     "liked"),         W_LIKED);
                addSource(dedup, sourceWeight, safeGet(recommendFut, "recommend"),     W_RECOMMEND);
                addSource(dedup, sourceWeight, safeGet(genreFut,     "genre"),         W_GENRE);
                addSource(dedup, sourceWeight, safeGet(vectorFut,    "vector-search"), W_VECTOR);
            }
        }

        // Step B: 批量回填已入库歌曲的 features（候选本身不带特征）
        backfillFeatures(dedup, platform);

        // T1-1 + T1-2: 打分排序，替换 Collections.shuffle
        List<SongVO> deduped = scoreAndSort(dedup, sourceWeight, mood);

        // Feature 4: 反馈评分过滤
        if (userId != null) {
            deduped = filterNegativeFeedback(userId, deduped);
        }

        // Feature 4b: 黑名单关键词过滤
        if (userId != null) {
            deduped = filterBlacklistKeywords(userId, deduped);
        }

        // Feature 4c: 全局黑名单过滤（管理员设置，对所有用户生效）
        deduped = filterGlobalBlacklist(deduped);

        return deduped.stream().limit(60).collect(Collectors.toList());
    }

    /**
     * 将 list 中每首歌按来源权重登记到 dedup + sourceWeight。
     * 首次出现时写入 dedup；多次出现时取最大权重（merge with Math::max）。
     */
    private void addSource(LinkedHashMap<String, SongVO> dedup,
                           Map<String, Double> sourceWeight,
                           List<SongVO> list, double weight) {
        for (SongVO s : list) {
            if (s.getPlatformSongId() == null) continue;
            dedup.putIfAbsent(s.getPlatformSongId(), s);
            sourceWeight.merge(s.getPlatformSongId(), weight, Math::max);
        }
    }

    /**
     * Step B: 批量把已入库歌曲的 features 回填到对应候选 SongVO。
     * 查询路径：platformSongId IN (...) → platform_song_mapping → song.features
     * 查不到的候选 features 保持 null（新歌，下次入库后有特征）。
     */
    private void backfillFeatures(LinkedHashMap<String, SongVO> dedup, String platform) {
        if (dedup.isEmpty()) return;
        try {
            List<String> platformSongIds = new ArrayList<>(dedup.keySet());
            List<PlatformSongMapping> mappings = platformSongMappingMapper.selectList(
                    new LambdaQueryWrapper<PlatformSongMapping>()
                            .eq(PlatformSongMapping::getPlatform, platform)
                            .in(PlatformSongMapping::getPlatformSongId, platformSongIds));
            if (mappings.isEmpty()) return;

            // platformSongId → songId
            Map<String, Long> pidToSongId = new HashMap<>();
            for (PlatformSongMapping m : mappings) {
                pidToSongId.put(m.getPlatformSongId(), m.getSongId());
            }

            List<Long> songIds = new ArrayList<>(new HashSet<>(pidToSongId.values()));
            List<Song> songs = songMapper.selectBatchIds(songIds);
            if (songs.isEmpty()) return;

            // songId → features JSON
            Map<Long, String> songFeatureMap = new HashMap<>();
            for (Song s : songs) {
                if (s.getFeatures() != null && !s.getFeatures().isBlank()) {
                    songFeatureMap.put(s.getId(), s.getFeatures());
                }
            }

            for (Map.Entry<String, SongVO> entry : dedup.entrySet()) {
                Long songId = pidToSongId.get(entry.getKey());
                if (songId == null) continue;
                String featuresJson = songFeatureMap.get(songId);
                if (featuresJson != null) {
                    entry.getValue().setFeatures(featuresJson);
                }
            }
        } catch (Exception e) {
            log.warn("Features backfill failed, continuing without features", e);
        }
    }

    /**
     * T1-1 + T1-2: 对候选按（来源权重 + 情绪匹配分 − avoid惩罚 + 小抖动）降序排序。
     * Package-visible for direct unit testing in RecallScoringTest.
     */
    List<SongVO> scoreAndSort(LinkedHashMap<String, SongVO> dedup,
                              Map<String, Double> sourceWeight,
                              MoodParams mood) {
        List<SongVO> candidates = new ArrayList<>(dedup.values());
        // T1-1 fix: compute each candidate's total score exactly once, then sort by
        // the cached value.  Computing inside the comparator caused a fresh Math.random()
        // jitter on every pairwise comparison, making the comparator inconsistent and
        // triggering "Comparison method violates its general contract!" on >= ~18 candidates.
        // dedup is keyed by platformSongId (non-null, addSource skips nulls), so there
        // are no null-key entries here.
        Map<String, Double> scoreCache = new HashMap<>();
        for (SongVO s : candidates) {
            scoreCache.put(s.getPlatformSongId(), computeTotal(s, sourceWeight, mood));
        }
        candidates.sort((a, b) -> Double.compare(
                scoreCache.get(b.getPlatformSongId()),
                scoreCache.get(a.getPlatformSongId())));
        return candidates;
    }

    private double computeTotal(SongVO song, Map<String, Double> sourceWeight, MoodParams mood) {
        String id = song.getPlatformSongId();
        double sw = sourceWeight.getOrDefault(id, 0.0);
        double em = emotionMatchScore(song.getFeatures(), mood);
        double penalty = hitsAvoid(song, mood) ? AVOID_PENALTY : 0.0;
        double jitter = Math.random() * JITTER;
        return sw + em - penalty + jitter;
    }

    /**
     * T1-2: 计算歌曲与当前心情的情绪匹配分。
     * features 为 null/解析失败 → 返回中性基线 UNKNOWN_EMOTION=0.5。
     * 距离 = sqrt((moodValence-songValence)^2 + (moodEnergy-songEnergy)^2)，
     * 归一化 emotion = 1 - dist/sqrt(2)，夹到 [0,1]。
     * genre 命中 +GENRE_BONUS，language 命中 +LANGUAGE_BONUS（可叠加）。
     * Package-visible for direct unit testing.
     */
    double emotionMatchScore(String featuresJson, MoodParams mood) {
        if (featuresJson == null || featuresJson.isBlank()) return UNKNOWN_EMOTION;
        if (mood == null || mood.getMood() == null) return UNKNOWN_EMOTION;
        try {
            JsonNode node = objectMapper.readTree(featuresJson);
            double songValence = node.path("valence").asDouble(0.5);
            double songEnergy  = node.path("energy").asDouble(0.5);

            MoodParams.MoodVector mv = mood.getMood();
            double dv = mv.getValence() - songValence;
            double de = mv.getEnergy()  - songEnergy;
            double dist = Math.sqrt(dv * dv + de * de);
            double emotion = Math.max(0.0, Math.min(1.0, 1.0 - dist / Math.sqrt(2.0)));

            double bonus = 0.0;
            // Genre bonus
            String songGenre = node.path("genre").asText(null);
            List<String> preferredGenres = mood.getPreferredGenres();
            if (songGenre != null && preferredGenres != null && preferredGenres.contains(songGenre)) {
                bonus += GENRE_BONUS;
            }
            // Language bonus
            String songLang = node.path("language").asText(null);
            List<String> preferredLanguages = mood.getPreferredLanguages();
            if (songLang != null && preferredLanguages != null && preferredLanguages.contains(songLang)) {
                bonus += LANGUAGE_BONUS;
            }

            return emotion + bonus;
        } catch (Exception e) {
            return UNKNOWN_EMOTION;
        }
    }

    /**
     * T1-2: 判断歌曲是否命中 avoidKeywords（歌名/歌手名/mood_tags 任一子串匹配，大小写不敏感）。
     * 命中的候选在总分里扣 AVOID_PENALTY=10.0（直接沉底，等效剔除但保留以防候选耗尽）。
     * Package-visible for direct unit testing.
     */
    boolean hitsAvoid(SongVO song, MoodParams mood) {
        List<String> avoidKeywords = mood != null ? mood.getAvoidKeywords() : null;
        if (avoidKeywords == null || avoidKeywords.isEmpty()) return false;

        String title  = song.getTitle()  != null ? song.getTitle().toLowerCase()  : "";
        String artist = song.getArtist() != null ? song.getArtist().toLowerCase() : "";

        // Also check mood_tags from features JSON
        List<String> moodTags = new ArrayList<>();
        if (song.getFeatures() != null && !song.getFeatures().isBlank()) {
            try {
                JsonNode node = objectMapper.readTree(song.getFeatures());
                JsonNode tagsNode = node.path("mood_tags");
                if (tagsNode.isArray()) {
                    for (JsonNode tag : tagsNode) {
                        moodTags.add(tag.asText("").toLowerCase());
                    }
                }
            } catch (Exception ignored) {}
        }

        for (String kw : avoidKeywords) {
            if (kw == null || kw.isBlank()) continue;
            String lkw = kw.toLowerCase();
            if (title.contains(lkw) || artist.contains(lkw)) return true;
            for (String tag : moodTags) {
                if (tag.contains(lkw)) return true;
            }
        }
        return false;
    }

    // ===================== Feature 4: 反馈评分过滤 =====================

    /**
     * 查询用户近期反馈，计算每首歌的得分，过滤掉严重负分歌曲。
     * 信号权重: completed=+1, skip(playedSeconds<30)=-3, like=+5, volume_up=+1 (scaled 0.5)
     * 使用 2x 整数缩放以支持 0.5 权重。
     */
    private List<SongVO> filterNegativeFeedback(Long userId, List<SongVO> candidates) {
        try {
            List<FeedbackEvent> recentEvents = feedbackEventMapper.selectList(
                    new LambdaQueryWrapper<FeedbackEvent>()
                            .eq(FeedbackEvent::getUserId, userId)
                            .in(FeedbackEvent::getEventType, "completed", "skip", "like", "volume_up")
                            .orderByDesc(FeedbackEvent::getCreatedAt)
                            .last("LIMIT 200"));

            if (recentEvents.isEmpty()) return candidates;

            // 计算每首歌的反馈得分（2x 缩放：volume_up=1 代表 0.5）
            Map<Long, Integer> songScores = new HashMap<>();
            for (FeedbackEvent evt : recentEvents) {
                if (evt.getSongId() == null) continue;
                int delta = switch (evt.getEventType()) {
                    case "completed" -> 2;
                    case "like" -> 10;
                    case "volume_up" -> 1;  // +0.5 scaled
                    case "skip" -> {
                        int played = parsePlayedSeconds(evt.getEventData());
                        yield played < 30 ? -6 : 0;
                    }
                    default -> 0;
                };
                songScores.merge(evt.getSongId(), delta, Integer::sum);
            }

            // 从候选中移除严重负分歌曲（score < -10, 即 2x 缩放后等价于 -5）
            // Batch lookup: collect all negative songIds, single DB query instead of N+1
            Set<String> negativeIds = new HashSet<>();
            List<Long> negativeSongIds = songScores.entrySet().stream()
                    .filter(e -> e.getValue() < -10)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
            if (!negativeSongIds.isEmpty()) {
                List<PlatformSongMapping> mappings = platformSongMappingMapper.selectList(
                        new LambdaQueryWrapper<PlatformSongMapping>()
                                .in(PlatformSongMapping::getSongId, negativeSongIds));
                for (PlatformSongMapping m : mappings) {
                    negativeIds.add(m.getPlatformSongId());
                }
            }

            if (!negativeIds.isEmpty()) {
                int beforeSize = candidates.size();
                candidates = candidates.stream()
                        .filter(s -> s.getPlatformSongId() == null || !negativeIds.contains(s.getPlatformSongId()))
                        .collect(Collectors.toList());
                log.info("Feedback filter: removed {} negative songs (was {})", beforeSize - candidates.size(), beforeSize);
            }
        } catch (Exception e) {
            log.warn("Feedback filtering failed, skipping", e);
        }
        return candidates;
    }

    // ===================== Feature 4b: 黑名单关键词过滤 =====================

    /**
     * 从用户 UserProfile 获取黑名单关键词，过滤掉标题或歌手名包含关键词的歌曲。
     * 简单的大小写不敏感子串匹配。
     */
    private List<SongVO> filterBlacklistKeywords(Long userId, List<SongVO> candidates) {
        try {
            UserProfile profile = userProfileMapper.selectByUserId(userId);
            if (profile == null || profile.getBlacklistKeywords() == null || profile.getBlacklistKeywords().isBlank()) {
                return candidates;
            }

            // Parse JSON array of keywords
            List<String> keywords = objectMapper.readValue(profile.getBlacklistKeywords(),
                    new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
            if (keywords.isEmpty()) return candidates;

            List<String> lowerKeywords = keywords.stream()
                    .filter(k -> k != null && !k.isBlank())
                    .map(String::toLowerCase)
                    .collect(Collectors.toList());
            if (lowerKeywords.isEmpty()) return candidates;

            int beforeSize = candidates.size();
            candidates = candidates.stream()
                    .filter(song -> {
                        String title = song.getTitle() != null ? song.getTitle().toLowerCase() : "";
                        String artist = song.getArtist() != null ? song.getArtist().toLowerCase() : "";
                        for (String kw : lowerKeywords) {
                            if (title.contains(kw) || artist.contains(kw)) {
                                return false;
                            }
                        }
                        return true;
                    })
                    .collect(Collectors.toList());

            int removed = beforeSize - candidates.size();
            if (removed > 0) {
                log.info("Blacklist keyword filter: removed {} songs for user {} (was {})", removed, userId, beforeSize);
            }
        } catch (Exception e) {
            log.warn("Blacklist keyword filtering failed for user {}, skipping", userId, e);
        }
        return candidates;
    }

    // ===================== Feature 4c: 全局黑名单过滤 =====================

    /**
     * 从 global_blacklist 表加载管理员设置的黑名单，过滤掉命中的歌曲。
     * 支持三种类型：artist（精确艺术家名）、song（精确歌曲标题）、keyword（子串匹配）。
     * 对所有用户生效，无需 Redis 缓存（表数据量小，type 列有索引）。
     */
    private List<SongVO> filterGlobalBlacklist(List<SongVO> candidates) {
        try {
            List<GlobalBlacklist> blacklist = globalBlacklistMapper.selectList(null);
            if (blacklist.isEmpty()) return candidates;

            Set<String> bannedArtists  = new HashSet<>();
            Set<String> bannedSongs    = new HashSet<>();
            List<String> bannedKeywords = new ArrayList<>();

            for (GlobalBlacklist entry : blacklist) {
                String v = entry.getValue() != null ? entry.getValue().toLowerCase() : "";
                switch (entry.getType()) {
                    case "artist"  -> bannedArtists.add(v);
                    case "song"    -> bannedSongs.add(v);
                    case "keyword" -> { if (!v.isBlank()) bannedKeywords.add(v); }
                }
            }

            int before = candidates.size();
            candidates = candidates.stream().filter(song -> {
                String title  = song.getTitle()  != null ? song.getTitle().toLowerCase()  : "";
                String artist = song.getArtist() != null ? song.getArtist().toLowerCase() : "";

                if (bannedArtists.contains(artist)) return false;
                if (bannedSongs.contains(title))    return false;
                for (String kw : bannedKeywords) {
                    if (title.contains(kw) || artist.contains(kw)) return false;
                }
                return true;
            }).collect(Collectors.toList());

            int removed = before - candidates.size();
            if (removed > 0) {
                log.info("Global blacklist filter: removed {} songs (was {})", removed, before);
            }
        } catch (Exception e) {
            log.warn("Global blacklist filtering failed, skipping", e);
        }
        return candidates;
    }

    private int parsePlayedSeconds(String eventData) {
        if (eventData == null || eventData.isBlank()) return 0;
        try {
            JsonNode node = objectMapper.readTree(eventData);
            return node.path("playedSeconds").asInt(0);
        } catch (Exception e) {
            return 0;
        }
    }

    // ===================== Feature 5: 用户偏好合并 =====================

    /**
     * 将用户保存的 genre/language 偏好合并到召回搜索关键词中。
     */
    private void mergeUserPreferences(Long userId, List<String> genres, List<String> vibes) {
        try {
            PreferencesVO prefs = userService.getPreferences(userId);
            if (prefs == null) return;

            // 合并 genre 偏好：用户偏好优先
            if (prefs.getGenres() != null && !prefs.getGenres().isEmpty()) {
                for (String g : prefs.getGenres()) {
                    if (!genres.contains(g)) {
                        genres.add(g);
                    }
                }
            }

            // 合并语言偏好到 vibe 关键词
            if (prefs.getLanguages() != null && !prefs.getLanguages().isEmpty()) {
                for (String lang : prefs.getLanguages()) {
                    if (!vibes.contains(lang)) {
                        vibes.add(lang);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load user preferences for {}", userId, e);
        }
    }

    // ===================== 辅助方法 =====================

    /**
     * 持久化歌曲到 DB + 创建 platform_song_mapping（Feature 3 前置）
     */
    private void persistSongs(List<SongVO> songs, String platform) {
        if (songs.isEmpty()) return;

        // Batch lookup: fetch all existing songs in one query instead of N queries
        List<SongVO> needPersist = songs.stream()
                .filter(s -> s.getTitle() != null && s.getArtist() != null)
                .collect(Collectors.toList());
        if (needPersist.isEmpty()) return;

        // Split into two groups: with external (platform, platformSongId) and title/artist-only fallback.
        // The (platform, platformSongId) pair is the authoritative external ID and the unique key on
        // platform_song_mapping — using it first avoids creating duplicate Song rows when the same
        // external track has minor title variations (whitespace, version tags, simplified/traditional).
        List<SongVO> byPlatformId = new ArrayList<>();
        List<SongVO> byTitleArtistOnly = new ArrayList<>();
        for (SongVO vo : needPersist) {
            if (vo.getPlatformSongId() != null) {
                byPlatformId.add(vo);
            } else {
                byTitleArtistOnly.add(vo);
            }
        }

        // Step 1: single query against platform_song_mapping by (platform, platformSongId IN ...)
        Map<String, Long> existingByExternalId = new HashMap<>();
        if (!byPlatformId.isEmpty()) {
            List<String> platformSongIds = byPlatformId.stream()
                    .map(SongVO::getPlatformSongId)
                    .distinct()
                    .collect(Collectors.toList());
            List<PlatformSongMapping> existingMappings = platformSongMappingMapper.selectList(
                    new LambdaQueryWrapper<PlatformSongMapping>()
                            .eq(PlatformSongMapping::getPlatform, platform)
                            .in(PlatformSongMapping::getPlatformSongId, platformSongIds));
            for (PlatformSongMapping m : existingMappings) {
                existingByExternalId.put(m.getPlatformSongId(), m.getSongId());
            }
        }

        // Step 2: walk byPlatformId — mapping hits reuse song id and skip song insert + mapping insert.
        // Misses fall through to the (title, artist) batch path below alongside byTitleArtistOnly.
        Map<Long, SongVO> newSongsById = new HashMap<>();
        List<SongVO> fallbackToTitleArtist = new ArrayList<>(byTitleArtistOnly);

        for (SongVO vo : byPlatformId) {
            Long existingSongId = existingByExternalId.get(vo.getPlatformSongId());
            if (existingSongId != null) {
                try {
                    vo.setId(existingSongId);
                    // Re-index into Qdrant using features (may be null for old songs — skip is acceptable).
                    indexSongForVectorSearch(existingSongId, vo.getFeatures());
                } catch (Exception e) {
                    log.warn("Failed to re-index existing song {}: {} - {}", existingSongId, vo.getTitle(), vo.getArtist(), e);
                }
            } else {
                fallbackToTitleArtist.add(vo);
            }
        }

        // Step 3: original (title, artist) batch path for both fallback groups (true new + no-external-id).
        if (!fallbackToTitleArtist.isEmpty()) {
            var titleArtistPairs = fallbackToTitleArtist.stream()
                    .map(s -> new String[]{s.getTitle(), s.getArtist()})
                    .collect(Collectors.toList());

            LambdaQueryWrapper<Song> songQuery = new LambdaQueryWrapper<>();
            for (int i = 0; i < titleArtistPairs.size(); i++) {
                String[] pair = titleArtistPairs.get(i);
                if (i > 0) songQuery.or();
                songQuery.and(w -> w.eq(Song::getTitle, pair[0]).eq(Song::getArtist, pair[1]));
            }
            List<Song> existingSongs = songMapper.selectList(songQuery);
            Map<String, Song> existingByKey = new HashMap<>();
            for (Song s : existingSongs) {
                existingByKey.put(s.getTitle() + "\0" + s.getArtist(), s);
            }

            // Concurrently enrich only truly-new songs (not already in DB).
            // Use a virtual-thread executor with an 8-second total timeout.
            List<SongVO> trulyNewVos = fallbackToTitleArtist.stream()
                    .filter(vo -> !existingByKey.containsKey(vo.getTitle() + "\0" + vo.getArtist()))
                    .toList();
            Map<String, CompletableFuture<String>> enrichFutures = new HashMap<>();
            try (var enrichExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (SongVO vo : trulyNewVos) {
                    String featureKey = vo.getTitle() + "\0" + vo.getArtist();
                    enrichFutures.put(featureKey, CompletableFuture.supplyAsync(
                            () -> songFeatureService.enrich(vo.getTitle(), vo.getArtist(), vo.getAlbum()),
                            enrichExecutor));
                }
                try {
                    CompletableFuture.allOf(enrichFutures.values().toArray(new CompletableFuture[0]))
                            .get(enrichTimeoutSeconds, TimeUnit.SECONDS);
                } catch (Exception timeout) {
                    long notDone = enrichFutures.values().stream().filter(f -> !f.isDone()).count();
                    log.warn("Song feature enrichment timed out after {}s; {} song(s) fell back to local fallback",
                            enrichTimeoutSeconds, notDone);
                    // Interrupt straggler virtual threads so close() returns promptly
                    enrichExecutor.shutdownNow();
                }
            }

            for (SongVO vo : fallbackToTitleArtist) {
                try {
                    String key = vo.getTitle() + "\0" + vo.getArtist();
                    Song existing = existingByKey.get(key);

                    Long songId;
                    String newFeaturesJson = null; // populated only for truly-new songs
                    if (existing != null) {
                        songId = existing.getId();
                    } else {
                        Song song = new Song();
                        song.setTitle(vo.getTitle());
                        song.setArtist(vo.getArtist());
                        song.setAlbum(vo.getAlbum());
                        song.setDurationSeconds(vo.getDurationSeconds());
                        song.setCoverUrl(vo.getCoverUrl());
                        // Set features: use enrich result if done, else local non-blocking fallback
                        CompletableFuture<String> fut = enrichFutures.get(key);
                        if (fut != null && fut.isDone() && !fut.isCompletedExceptionally()) {
                            newFeaturesJson = fut.get();
                        } else {
                            // Future is null, still running, or completed exceptionally —
                            // use non-blocking local fallback (no LLM call, no metric increment)
                            newFeaturesJson = songFeatureService.fallbackFeatures(
                                    vo.getTitle(), vo.getArtist(), vo.getAlbum());
                        }
                        song.setFeatures(newFeaturesJson);
                        songMapper.insert(song);
                        songId = song.getId();
                        newSongsById.put(songId, vo);
                    }
                    // Replace Netease platform ID with the DB auto-increment ID so that
                    // all downstream references (feedback, liked, history) use the correct key.
                    vo.setId(songId);

                    // Auto-index into Qdrant using features aligned with query-side embeddings.
                    // existing-by-title branch: use DB features (may be null → skip accepted).
                    // new-song branch: use freshly-computed featuresJson.
                    String featuresToIndex = (existing != null) ? existing.getFeatures() : newFeaturesJson;
                    indexSongForVectorSearch(songId, featuresToIndex);
                } catch (Exception e) {
                    log.warn("Failed to persist song: {} - {}", vo.getTitle(), vo.getArtist(), e);
                }
            }
        }

        // Batch create platform mappings: single query to find existing, then insert missing.
        // newSongsById only contains true-new songs whose (platform, platformSongId) was confirmed
        // absent in Step 1, so the unique-key collision can no longer occur.
        if (!newSongsById.isEmpty()) {
            List<Long> newSongIds = new ArrayList<>(newSongsById.keySet());
            List<PlatformSongMapping> existingMappings = platformSongMappingMapper.selectList(
                    new LambdaQueryWrapper<PlatformSongMapping>()
                            .in(PlatformSongMapping::getSongId, newSongIds));
            Map<Long, List<PlatformSongMapping>> mappingsBySongId = existingMappings.stream()
                    .collect(Collectors.groupingBy(PlatformSongMapping::getSongId));

            for (Map.Entry<Long, SongVO> entry : newSongsById.entrySet()) {
                try {
                    Long songId = entry.getKey();
                    SongVO vo = entry.getValue();
                    if (vo.getPlatformSongId() == null) continue;
                    List<PlatformSongMapping> existing = mappingsBySongId.getOrDefault(songId, List.of());
                    boolean hasMapping = existing.stream()
                            .anyMatch(m -> platform.equals(m.getPlatform())
                                    && vo.getPlatformSongId().equals(m.getPlatformSongId()));
                    if (!hasMapping) {
                        PlatformSongMapping mapping = new PlatformSongMapping();
                        mapping.setSongId(songId);
                        mapping.setPlatform(platform);
                        mapping.setPlatformSongId(vo.getPlatformSongId());
                        platformSongMappingMapper.insert(mapping);
                    }
                } catch (Exception e) {
                    log.warn("Failed to create platform mapping for song {}: {}", entry.getKey(), e.getMessage());
                }
            }
        }
    }

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

    private Set<String> peekQueueIds(Long userId) {
        String queueKey = RedisKeys.format(RedisKeys.USER_QUEUE, userId);
        List<String> rawList = redisTemplate.opsForList().range(queueKey, 0, -1);
        if (rawList == null) return Set.of();
        return rawList.stream().map(raw -> {
            try {
                return objectMapper.readValue(raw, SongVO.class).getPlatformSongId();
            } catch (Exception e) {
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private List<SongVO> safeGet(CompletableFuture<List<SongVO>> f, String label) {
        try { return f.isDone() ? f.get() : List.of(); }
        catch (Exception e) { log.warn("Recall path {} failed", label, e); return List.of(); }
    }

    private List<SongVO> fetchLiked(String platform, String cookie) {
        try { return MusicResponseParser.parseSongs(musicApiClient.getUserLikedSongs(platform, cookie), platform); }
        catch (Exception e) { log.warn("fetchLiked failed", e); return List.of(); }
    }

    private List<SongVO> fetchRecommend(String platform, String cookie) {
        try { return MusicResponseParser.parseSongs(musicApiClient.getRecommendSongs(platform, cookie), platform); }
        catch (Exception e) { log.warn("fetchRecommend failed", e); return List.of(); }
    }

    private List<SongVO> fetchSearch(String platform, String keywords, int limit) {
        try { return MusicResponseParser.parseSongs(musicApiClient.searchSongs(platform, keywords, limit, null), platform); }
        catch (Exception e) { log.warn("fetchSearch failed: {}", keywords, e); return List.of(); }
    }

    // ===================== Vector Recall (6th path) =====================

    /**
     * Build a text query for vector embedding from mood params + user preferences.
     */
    private String buildVectorQueryText(MoodParams mood, List<String> genres, List<String> vibes) {
        StringBuilder sb = new StringBuilder();
        if (mood.getSceneInferred() != null && !mood.getSceneInferred().isBlank()) {
            sb.append(mood.getSceneInferred()).append(" ");
        }
        if (genres != null && !genres.isEmpty()) {
            sb.append(String.join(" ", genres.subList(0, Math.min(3, genres.size())))).append(" ");
        }
        if (vibes != null && !vibes.isEmpty()) {
            sb.append(String.join(" ", vibes.subList(0, Math.min(3, vibes.size()))));
        }
        String text = sb.toString().trim();
        return text.isEmpty() ? "music" : text;
    }

    /**
     * 6th recall path: vector similarity search via Qdrant.
     * Gracefully returns empty list if Qdrant is unavailable.
     */
    private List<SongVO> fetchVectorSimilar(String queryText) {
        try {
            float[] embedding = embeddingService.embed(queryText);
            List<Long> songIds = qdrantService.searchSimilar(embedding, 20);
            if (songIds.isEmpty()) return List.of();

            // Batch query songs from DB
            List<Song> songs = songMapper.selectBatchIds(songIds);
            if (songs.isEmpty()) return List.of();

            // Preserve similarity order
            Map<Long, Song> songMap = new LinkedHashMap<>();
            for (Long id : songIds) {
                songs.stream().filter(s -> s.getId().equals(id)).findFirst().ifPresent(s -> songMap.put(id, s));
            }

            // Batch convert with single mapping query (no N+1)
            return songsToVOs(new ArrayList<>(songMap.values()));
        } catch (Exception e) {
            log.warn("Vector recall failed: {}", e.getMessage());
            vectorRecallMetrics.recallFailure();
            return List.of();
        }
    }

    /**
     * Build the song-side embedding text from a features JSON.
     * The resulting text is structurally aligned with the query-side {@link #buildVectorQueryText}:
     * both use genre + mood/vibe words + energy-level word + language word in Chinese.
     * <p>
     * Format: {@code "华语流行 夜晚 松弛 低能量 中文"}
     * <p>
     * Returns empty string when features are null/blank/unparseable — callers must
     * skip indexing in that case (do NOT fall back to song-name text).
     * <p>
     * Package-visible for unit testing.
     */
    String buildSongEmbeddingText(String featuresJson) {
        if (featuresJson == null || featuresJson.isBlank()) return "";
        try {
            JsonNode node = objectMapper.readTree(featuresJson);

            StringBuilder sb = new StringBuilder();

            // genre
            String genre = node.path("genre").asText(null);
            if (genre != null && !genre.isBlank()) {
                sb.append(genre).append(" ");
            }

            // mood_tags (array of strings)
            JsonNode tagsNode = node.path("mood_tags");
            if (tagsNode.isArray()) {
                for (JsonNode tag : tagsNode) {
                    String t = tag.asText("").trim();
                    if (!t.isEmpty()) sb.append(t).append(" ");
                }
            }

            // energy-level word derived from energy value (0‥1) or tempo_bucket
            String energyWord = resolveEnergyWord(node);
            if (energyWord != null) sb.append(energyWord).append(" ");

            // language word
            String langWord = resolveLanguageWord(node.path("language").asText(null));
            if (langWord != null) sb.append(langWord).append(" ");

            return sb.toString().trim();
        } catch (Exception e) {
            log.debug("buildSongEmbeddingText parse failed, skipping index: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Derive an energy-level Chinese word from features.
     * Uses the numeric {@code energy} field if present; falls back to {@code tempo_bucket}.
     */
    private String resolveEnergyWord(JsonNode node) {
        // Try numeric energy first (0..1 scale)
        JsonNode energyNode = node.path("energy");
        if (!energyNode.isMissingNode() && energyNode.isNumber()) {
            double energy = energyNode.asDouble();
            if (energy >= 0.7) return "高能量";
            if (energy >= 0.4) return "中能量";
            return "低能量";
        }
        // Fallback: tempo_bucket
        String bucket = node.path("tempo_bucket").asText(null);
        if (bucket == null || bucket.isBlank()) return null;
        return switch (bucket.toLowerCase()) {
            case "high", "fast" -> "高能量";
            case "low", "slow" -> "低能量";
            default -> "中能量"; // mid, moderate, etc.
        };
    }

    /**
     * Map a language code to a Chinese language word.
     */
    private String resolveLanguageWord(String lang) {
        if (lang == null || lang.isBlank()) return null;
        return switch (lang.toLowerCase()) {
            case "zh", "zh-cn", "zh-tw" -> "中文";
            case "en" -> "英文";
            case "ja" -> "日文";
            case "ko" -> "韩文";
            case "instrumental" -> "器乐";
            default -> null;
        };
    }

    /**
     * Index a song into Qdrant for vector-based recall.
     * Generates an embedding from the song's emotion/features description text
     * (structurally aligned with the query side) and upserts it.
     * <p>
     * If {@code featuresJson} is null/blank/unparseable (i.e. {@link #buildSongEmbeddingText}
     * returns empty), indexing is skipped — we never fall back to song-name text.
     * Wrapped in try-catch — failure here does not affect song persistence.
     *
     * @param songId      the DB song ID
     * @param featuresJson the Task 2 features JSON (genre/mood_tags/energy/language/…)
     */
    private void indexSongForVectorSearch(Long songId, String featuresJson) {
        try {
            String text = buildSongEmbeddingText(featuresJson);
            if (text.isEmpty()) return; // no features → skip, do NOT index song-name text

            float[] embedding = embeddingService.embed(text);

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("songId", songId);

            qdrantService.upsertSong(songId, embedding, metadata);
        } catch (Exception e) {
            log.warn("Vector indexing failed for song {}: {}", songId, e.getMessage());
            vectorRecallMetrics.indexFailure();
        }
    }

    /**
     * Batch-convert Song entities to SongVOs with a single mapping query.
     */
    private List<SongVO> songsToVOs(List<Song> songs) {
        if (songs.isEmpty()) return List.of();
        List<Long> songIds = songs.stream().map(Song::getId).collect(Collectors.toList());
        List<PlatformSongMapping> allMappings = platformSongMappingMapper.selectList(
                new LambdaQueryWrapper<PlatformSongMapping>()
                        .in(PlatformSongMapping::getSongId, songIds));
        Map<Long, List<PlatformSongMapping>> mappingMap = allMappings.stream()
                .collect(Collectors.groupingBy(PlatformSongMapping::getSongId));

        List<SongVO> result = new ArrayList<>(songs.size());
        for (Song song : songs) {
            List<PlatformSongMapping> mappings = mappingMap.getOrDefault(song.getId(), List.of());
            PlatformSongMapping mapping = mappings.isEmpty() ? null : mappings.get(0);
            result.add(SongVO.builder()
                    .id(song.getId())
                    .title(song.getTitle())
                    .artist(song.getArtist())
                    .album(song.getAlbum())
                    .durationSeconds(song.getDurationSeconds())
                    .coverUrl(song.getCoverUrl())
                    .platform(mapping != null ? mapping.getPlatform() : null)
                    .platformSongId(mapping != null ? mapping.getPlatformSongId() : null)
                    .features(song.getFeatures())
                    .build());
        }
        return result;
    }

    /**
     * Convert a single Song entity to SongVO (delegates to batch method for mapping lookup).
     */
    private SongVO songToVO(Song song) {
        return songsToVOs(List.of(song)).get(0);
    }

    // ===================== Play URL 批量获取 =====================

    /**
     * Batch-fetch play URLs from the music adapter and set them on SongVOs.
     * Uses a single API call with comma-separated IDs to avoid N+1 requests.
     * For QQ Music, songs without a URL are retried via Netease fallback.
     */
    private void enrichWithPlayUrls(List<SongVO> songs, String platform, String cookie) {
        if (songs == null || songs.isEmpty()) return;
        List<String> ids = songs.stream()
                .map(SongVO::getPlatformSongId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .collect(Collectors.toList());
        if (ids.isEmpty()) return;
        try {
            Map<String, String> urlMap = musicApiClient.getSongUrls(platform, ids, cookie);
            int enriched = 0;
            for (SongVO song : songs) {
                String url = urlMap.get(song.getPlatformSongId());
                if (url != null && !url.isBlank()) {
                    song.setPlayUrl(url);
                    song.setUrlSource(platform);
                    enriched++;
                }
            }
            log.info("Enriched {}/{} songs with play URLs from {}", enriched, songs.size(), platform);

            // For QQ Music: try Netease fallback for songs that still have no URL
            if ("qqmusic".equals(platform)) {
                List<SongVO> missing = songs.stream()
                        .filter(s -> s.getPlayUrl() == null || s.getPlayUrl().isBlank())
                        .collect(Collectors.toList());
                if (!missing.isEmpty()) {
                    fillFallbackUrls(missing);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to enrich songs with play URLs, continuing without them", e);
        }
    }

    private void fillFallbackUrls(List<SongVO> songs) {
        int fallbackCount = 0;
        int failedCount = 0;
        for (SongVO song : songs) {
            try {
                String neteaseUrl = fallbackToNetease(
                        song.getPlatformSongId(), song.getTitle(), song.getArtist(), null);
                if (neteaseUrl != null && !neteaseUrl.isBlank()) {
                    song.setPlayUrl(neteaseUrl);
                    song.setUrlSource("netease_fallback");
                    fallbackCount++;
                } else {
                    failedCount++;
                    log.debug("Netease fallback returned null for: {} - {}", song.getTitle(), song.getArtist());
                }
            } catch (Exception e) {
                failedCount++;
                log.debug("Netease fallback error for {}: {}", song.getTitle(), e.getMessage());
            }
        }
        if (fallbackCount > 0 || failedCount > 0) {
            log.info("Netease fallback: {}/{} QQ Music songs got URLs ({} failed)",
                    fallbackCount, songs.size(), failedCount);
        }
    }

    // ===================== AI 重排 + 理由 =====================

    private List<SongVO> rankWithAI(List<SongVO> candidates, MoodParams mood) {
        if (candidates.isEmpty()) return candidates;
        try {
            String prompt = buildRankingPrompt(candidates, mood);
            String response = llmClient.complete(null, prompt);
            return applyRanking(response, candidates, mood);
        } catch (Exception e) {
            llmFallbackMetrics.songRankingFallback();
            log.warn("song ranking fallback: reason={}, candidates={}", e.getMessage(), candidates.size());
            return candidates.stream().limit(20).collect(Collectors.toList());
        }
    }

    private String buildRankingPrompt(List<SongVO> candidates, MoodParams mood) {
        try {
            String template = new String(
                    new ClassPathResource("prompts/song-ranking.txt").getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);

            StringBuilder songList = new StringBuilder();
            for (int i = 0; i < candidates.size(); i++) {
                SongVO s = candidates.get(i);
                songList.append(i + 1).append("|").append(s.getTitle()).append("|").append(s.getArtist()).append("\n");
            }

            MoodParams.MoodVector v = mood.getMood() != null ? mood.getMood() : new MoodParams.MoodVector();
            return template
                    .replace("{{valence}}", String.format("%.2f", v.getValence()))
                    .replace("{{energy}}", String.format("%.2f", v.getEnergy()))
                    .replace("{{vibeKeywords}}", mood.getVibeKeywords() != null ? String.join(", ", mood.getVibeKeywords()) : "")
                    .replace("{{scene}}", mood.getSceneInferred() != null ? mood.getSceneInferred() : "")
                    .replace("{{genres}}", mood.getPreferredGenres() != null ? String.join(", ", mood.getPreferredGenres()) : "")
                    .replace("{{energyCurve}}", mood.getEnergyCurve() != null ? mood.getEnergyCurve() : "flat")
                    .replace("{{avoidKeywords}}", mood.getAvoidKeywords() != null ? String.join(", ", mood.getAvoidKeywords()) : "")
                    .replace("{{songList}}", songList.toString());
        } catch (Exception e) {
            log.error("Failed to build ranking prompt", e);
            return "";
        }
    }

    private List<SongVO> applyRanking(String aiResponse, List<SongVO> candidates, MoodParams mood) {
        try {
            // 提取 JSON 数组
            String json = aiResponse.trim();
            int start = json.indexOf('[');
            int end   = json.lastIndexOf(']');
            if (start < 0 || end < 0) throw new IllegalArgumentException("No JSON array found");
            json = json.substring(start, end + 1);

            JsonNode arr = objectMapper.readTree(json);
            List<SongVO> ranked = new ArrayList<>();

            for (JsonNode item : arr) {
                int idx = item.path("index").asInt(0) - 1; // 1-based to 0-based
                if (idx < 0 || idx >= candidates.size()) continue;
                SongVO song = candidates.get(idx);
                String reason = item.path("reason").asText("");
                // 拷贝并注入 reason（保留 features 供下游使用）
                ranked.add(SongVO.builder()
                        .id(song.getId())
                        .title(song.getTitle())
                        .artist(song.getArtist())
                        .album(song.getAlbum())
                        .durationSeconds(song.getDurationSeconds())
                        .coverUrl(song.getCoverUrl())
                        .platform(song.getPlatform())
                        .platformSongId(song.getPlatformSongId())
                        .recommendReason(reason)
                        .features(song.getFeatures())
                        .build());
            }

            // 补齐：如果 AI 返回少于 15 首，把剩余候选追加到末尾
            Set<String> usedIds = ranked.stream()
                    .map(SongVO::getPlatformSongId)
                    .collect(Collectors.toSet());
            for (SongVO s : candidates) {
                if (ranked.size() >= 20) break;
                if (!usedIds.contains(s.getPlatformSongId())) {
                    ranked.add(s);
                    usedIds.add(s.getPlatformSongId());
                }
            }

            log.info("AI ranked {} songs from {} candidates", ranked.size(), candidates.size());
            return ranked;
        } catch (Exception e) {
            log.warn("Failed to parse AI ranking response: {}", aiResponse, e);
            return candidates.stream().limit(20).collect(Collectors.toList());
        }
    }

    private String buildMoodSummary(MoodParams mood) {
        String scene = mood.getSceneInferred() != null ? mood.getSceneInferred() : "";
        List<String> vibes = mood.getVibeKeywords();
        if (vibes == null || vibes.isEmpty()) return scene;
        return scene + " · " + String.join(" ", vibes.subList(0, Math.min(3, vibes.size())));
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
        String ttlKey = RedisKeys.format(RedisKeys.SESSION_TTL, sessionId);
        int seconds = minutes * 60;
        redisTemplate.opsForValue().set(ttlKey, String.valueOf(seconds), Duration.ofSeconds(seconds));
        log.info("Updated session {} duration to {} minutes", sessionId, minutes);
    }

    @Override
    public boolean isSessionExpired(Long sessionId) {
        if (sessionId == null) return false;
        String ttlKey = RedisKeys.format(RedisKeys.SESSION_TTL, sessionId);
        // key 不存在 = 已过期或未设置
        return !Boolean.TRUE.equals(redisTemplate.hasKey(ttlKey));
    }
}
