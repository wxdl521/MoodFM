package com.moodfm.service.player.impl.recall;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodfm.ai.model.MoodParams;
import com.moodfm.domain.entity.*;
import com.moodfm.domain.vo.PreferencesVO;
import com.moodfm.domain.vo.SongVO;
import com.moodfm.mapper.*;
import com.moodfm.service.player.impl.recall.filter.CandidateFilter;
import com.moodfm.service.player.impl.recall.source.RecallSource;
import com.moodfm.service.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Candidate recall pipeline orchestrator: 6-path parallel recall + dedup +
 * source-weighted scoring + emotion match + 3 filters + user-preference merge.
 * <p>
 * Each recall path is a pluggable {@link RecallSource} Spring bean, injected as
 * a {@code List<RecallSource>} sorted by {@code @Order} ascending
 * (liked→recommend→genre→vibe→explore→vector).
 * <p>
 * Each filter stage is a pluggable {@link CandidateFilter} Spring bean, injected
 * as a {@code List<CandidateFilter>} sorted by {@code @Order} ascending and
 * applied as an ordered pipeline (negative-feedback → blacklist-keyword →
 * global-blacklist).
 * <p>
 * Timeout fallback (§4 behavior fix): on timeout or partial failure the pipeline
 * now merges ALL completed sources — the old code dropped vibe-search and
 * explore-search unconditionally from the timeout catch branch.
 * <p>
 * Extracted from {@code PlayerServiceImpl} (T3-1 Task 5); recall paths made
 * pluggable beans in T3-2 Task 1; filters made pluggable beans in T3-2 Task 2.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CandidateRecallService {

    private final List<RecallSource> recallSources;
    private final List<CandidateFilter> candidateFilters;
    private final SongMapper songMapper;
    private final PlatformSongMappingMapper platformSongMappingMapper;
    private final UserService userService;
    private final ObjectMapper objectMapper;
    private final SongEmbeddingTextBuilder songEmbeddingTextBuilder;

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

        RecallContext ctx = new RecallContext(platform, cookie, mood, userId,
                genreKw, vibeKw, exploreKw, vectorQueryText);

        // T1-1: 去重表 + 来源权重表（同一首歌出现在多路时取最大权重）
        LinkedHashMap<String, SongVO> dedup = new LinkedHashMap<>();
        Map<String, Double> sourceWeight = new LinkedHashMap<>();

        // 6 路并行（Virtual Thread） — pluggable RecallSource beans
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Map<RecallSource, CompletableFuture<List<SongVO>>> futures = new LinkedHashMap<>();
            for (RecallSource s : recallSources) {
                futures.put(s, CompletableFuture.supplyAsync(() -> s.recall(ctx), executor));
            }
            try {
                CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0]))
                        .get(12, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("Parallel recall timeout/error, merging completed sources", e);
            }
            // ⚠️ §4 behavior improvement: merge ALL completed sources (was: a fixed 4-source subset
            // that dropped vibe-search and explore-search on timeout)
            for (var entry : futures.entrySet()) {
                RecallSource s = entry.getKey();
                CompletableFuture<List<SongVO>> f = entry.getValue();
                if (f.isDone() && !f.isCompletedExceptionally()) {
                    addSource(dedup, sourceWeight, safeGet(f, s.sourceName()), s.weight());
                }
            }
        }

        // Step B: 批量回填已入库歌曲的 features（候选本身不带特征）
        backfillFeatures(dedup, ctx.platform());

        // T1-1 + T1-2: 打分排序，替换 Collections.shuffle
        List<SongVO> deduped = scoreAndSort(dedup, sourceWeight, ctx.mood());

        // Feature 4/4b/4c: 有序过滤流水线（@Order: 负反馈→黑名单关键词→全局黑名单）。
        // 每个过滤器是一个 CandidateFilter bean；userId==null 时用户级过滤器内部短路。
        for (CandidateFilter filter : candidateFilters) {
            deduped = filter.filter(userId, deduped);
        }

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
}
