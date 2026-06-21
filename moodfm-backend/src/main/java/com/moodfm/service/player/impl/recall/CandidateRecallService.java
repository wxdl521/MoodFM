package com.moodfm.service.player.impl.recall;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodfm.ai.model.MoodParams;
import com.moodfm.client.music.MusicApiClient;
import com.moodfm.common.util.MusicResponseParser;
import com.moodfm.domain.entity.*;
import com.moodfm.domain.vo.PreferencesVO;
import com.moodfm.domain.vo.SongVO;
import com.moodfm.mapper.*;
import com.moodfm.service.embedding.EmbeddingService;
import com.moodfm.service.player.impl.catalog.SongCatalogService;
import com.moodfm.service.user.UserService;
import com.moodfm.service.vector.QdrantService;
import com.moodfm.service.vector.VectorRecallMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Candidate recall pipeline: 6-path parallel recall + dedup + source-weighted scoring
 * + emotion match + 3 filters + user-preference merge + vector recall.
 * <p>
 * Extracted from {@code PlayerServiceImpl} (T3-1 Task 5).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CandidateRecallService {

    private final MusicApiClient musicApiClient;
    private final SongMapper songMapper;
    private final PlatformSongMappingMapper platformSongMappingMapper;
    private final FeedbackEventMapper feedbackEventMapper;
    private final UserProfileMapper userProfileMapper;
    private final UserService userService;
    private final GlobalBlacklistMapper globalBlacklistMapper;
    private final EmbeddingService embeddingService;
    private final QdrantService qdrantService;
    private final VectorRecallMetrics vectorRecallMetrics;
    private final ObjectMapper objectMapper;
    private final SongEmbeddingTextBuilder songEmbeddingTextBuilder;
    private final SongCatalogService songCatalogService;

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

    private List<SongVO> recallSongs(String platform, String cookie, MoodParams mood) {
        return recallSongs(platform, cookie, mood, null);
    }

    /**
     * 6 路并行召回 + 来源加权 + 情绪匹配打分 + 反馈过滤(Feature 4) + 用户偏好(Feature 5) + 向量召回(Feature 6)
     * T1-1: 来源加权（替换 Collections.shuffle）
     * T1-2: 情绪匹配打分
     */
    public List<SongVO> recallSongs(String platform, String cookie, MoodParams mood, Long userId) {
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
        String vectorQueryText = songEmbeddingTextBuilder.buildVectorQueryText(mood, genres, vibes);

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
            // 有意使用二维距离（valence+energy）：歌曲特征侧没有 tension 维度，无法构成 spec 所述的三维距离。
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
            return songCatalogService.songsToVOs(new ArrayList<>(songMap.values()));
        } catch (Exception e) {
            log.warn("Vector recall failed: {}", e.getMessage());
            vectorRecallMetrics.recallFailure();
            return List.of();
        }
    }
}
