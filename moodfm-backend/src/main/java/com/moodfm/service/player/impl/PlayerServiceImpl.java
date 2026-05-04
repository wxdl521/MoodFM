package com.moodfm.service.player.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodfm.ai.model.MoodParams;
import com.moodfm.client.music.MusicApiClient;
import com.moodfm.common.constant.RedisKeys;
import com.moodfm.common.exception.BizException;
import com.moodfm.common.result.ResultCode;
import com.moodfm.common.util.AesUtil;
import com.moodfm.domain.dto.radio.MoodInputRequest;
import com.moodfm.domain.entity.MoodSession;
import com.moodfm.domain.entity.PlatformBinding;
import com.moodfm.domain.vo.RadioQueueVO;
import com.moodfm.domain.vo.SongVO;
import com.moodfm.mapper.MoodSessionMapper;
import com.moodfm.service.ai.MoodAnalysisService;
import com.moodfm.service.platform.PlatformBindingService;
import com.moodfm.service.player.PlayerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
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
    private final AesUtil aesUtil;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final ChatClient chatClient;

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

        // 3. 获取默认绑定平台 + cookie
        PlatformBinding binding = platformBindingService.getDefaultBinding(userId);
        String cookie = aesUtil.decrypt(binding.getCookieEncrypted());
        String platform = binding.getPlatform();

        // 4. 5 路并行召回
        List<SongVO> candidates = recallSongs(platform, cookie, moodParams);

        // 5. AI 重排 + 生成推荐理由
        List<SongVO> ranked = rankWithAI(candidates, moodParams);

        // 6. 存入 Redis 队列
        try {
            String queueKey = RedisKeys.format(RedisKeys.USER_QUEUE, userId);
            redisTemplate.opsForValue().set(queueKey, objectMapper.writeValueAsString(ranked), Duration.ofHours(1));
        } catch (Exception e) {
            log.warn("Failed to cache queue", e);
        }

        String moodSummary = buildMoodSummary(moodParams);

        return RadioQueueVO.builder()
                .sessionId(session.getId())
                .scene(session.getScene())
                .moodSummary(moodSummary)
                .songs(ranked)
                .totalCount(ranked.size())
                .build();
    }

    @Override
    public List<SongVO> getNextBatch(Long userId, Long sessionId) {
        String queueKey = RedisKeys.format(RedisKeys.USER_QUEUE, userId);
        String cached = redisTemplate.opsForValue().get(queueKey);
        if (cached == null) return List.of();
        try {
            List<SongVO> all = objectMapper.readValue(cached,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, SongVO.class));
            return all.subList(0, Math.min(5, all.size()));
        } catch (Exception e) {
            return List.of();
        }
    }

    @Override
    public String getSongUrl(Long userId, String platform, String songId) {
        PlatformBinding binding = platformBindingService.getValidBinding(userId, platform);
        String cookie = aesUtil.decrypt(binding.getCookieEncrypted());
        String url = musicApiClient.getSongUrl(platform, songId, cookie);
        if (url == null || url.isBlank()) throw new BizException(ResultCode.RECALL_FAILED, "获取播放地址失败");
        return url;
    }

    // ===================== 召回 =====================

    private List<SongVO> recallSongs(String platform, String cookie, MoodParams mood) {
        List<String> genres = mood.getPreferredGenres() != null ? mood.getPreferredGenres() : List.of();
        List<String> vibes  = mood.getVibeKeywords()    != null ? mood.getVibeKeywords()    : List.of();

        String genreKw  = genres.isEmpty() ? "music" : String.join(" ", genres.subList(0, Math.min(2, genres.size())));
        String vibeKw   = vibes.isEmpty()  ? "popular" : String.join(" ", vibes.subList(0, Math.min(2, vibes.size())));
        String sceneKw  = (mood.getSceneInferred() != null ? mood.getSceneInferred() : "放松") + " 音乐";
        String exploreKw= (genres.isEmpty() ? "indie" : genres.get(0)) + " 新歌";

        // 5 路并行
        CompletableFuture<List<SongVO>> likedFut      = CompletableFuture.supplyAsync(() -> fetchLiked(platform, cookie));
        CompletableFuture<List<SongVO>> recommendFut  = CompletableFuture.supplyAsync(() -> fetchRecommend(platform, cookie));
        CompletableFuture<List<SongVO>> genreFut      = CompletableFuture.supplyAsync(() -> fetchSearch(platform, genreKw, 25));
        CompletableFuture<List<SongVO>> vibeFut        = CompletableFuture.supplyAsync(() -> fetchSearch(platform, vibeKw, 25));
        CompletableFuture<List<SongVO>> exploreFut    = CompletableFuture.supplyAsync(() -> fetchSearch(platform, exploreKw, 15));

        List<SongVO> all = new ArrayList<>();
        try {
            CompletableFuture.allOf(likedFut, recommendFut, genreFut, vibeFut, exploreFut)
                    .get(12, TimeUnit.SECONDS);
            all.addAll(safeGet(likedFut,     "liked"));
            all.addAll(safeGet(recommendFut, "recommend"));
            all.addAll(safeGet(genreFut,     "genre-search"));
            all.addAll(safeGet(vibeFut,       "vibe-search"));
            all.addAll(safeGet(exploreFut,   "explore-search"));
        } catch (Exception e) {
            log.warn("Parallel recall timeout/error, using partial results", e);
            // 使用已完成的 future 结果
            all.addAll(safeGet(likedFut, "liked"));
            all.addAll(safeGet(recommendFut, "recommend"));
            all.addAll(safeGet(genreFut, "genre"));
        }

        // 去重（按 platformSongId）+ 打乱 + 限制候选数
        Map<String, SongVO> dedup = new LinkedHashMap<>();
        for (SongVO s : all) {
            if (s.getPlatformSongId() != null && !dedup.containsKey(s.getPlatformSongId())) {
                dedup.put(s.getPlatformSongId(), s);
            }
        }
        List<SongVO> deduped = new ArrayList<>(dedup.values());
        Collections.shuffle(deduped);
        return deduped.stream().limit(60).collect(Collectors.toList());
    }

    private List<SongVO> safeGet(CompletableFuture<List<SongVO>> f, String label) {
        try { return f.isDone() ? f.get() : List.of(); }
        catch (Exception e) { log.warn("Recall path {} failed", label, e); return List.of(); }
    }

    private List<SongVO> fetchLiked(String platform, String cookie) {
        try { return parseSongs(musicApiClient.getUserLikedSongs(platform, cookie), platform); }
        catch (Exception e) { log.warn("fetchLiked failed", e); return List.of(); }
    }

    private List<SongVO> fetchRecommend(String platform, String cookie) {
        try { return parseSongs(musicApiClient.getRecommendSongs(platform, cookie), platform); }
        catch (Exception e) { log.warn("fetchRecommend failed", e); return List.of(); }
    }

    private List<SongVO> fetchSearch(String platform, String keywords, int limit) {
        try { return parseSongs(musicApiClient.searchSongs(platform, keywords, limit), platform); }
        catch (Exception e) { log.warn("fetchSearch failed: {}", keywords, e); return List.of(); }
    }

    private List<SongVO> parseSongs(JsonNode data, String platform) {
        List<SongVO> songs = new ArrayList<>();
        JsonNode arr = data.path("songs");
        if (!arr.isArray()) arr = data.path("data").path("songs");
        if (!arr.isArray()) return songs;

        for (JsonNode s : arr) {
            String artist = "";
            JsonNode ar = s.path("ar");
            if (ar.isArray() && !ar.isEmpty()) artist = ar.get(0).path("name").asText();
            if (artist.isEmpty()) artist = s.path("artists").path(0).path("name").asText("");
            if (artist.isEmpty()) artist = s.path("song").path("artists").path(0).path("name").asText("");

            long id = s.path("id").asLong();
            if (id == 0) id = s.path("song").path("id").asLong();
            if (id == 0) continue;

            String name = s.path("name").asText(s.path("song").path("name").asText(""));
            String cover = s.path("al").path("picUrl").asText(
                           s.path("album").path("picUrl").asText(
                           s.path("song").path("album").path("picUrl").asText("")));
            String album = s.path("al").path("name").asText(
                           s.path("album").path("name").asText(
                           s.path("song").path("album").path("name").asText("")));
            int dur = s.path("dt").asInt(s.path("duration").asInt(0)) / 1000;

            songs.add(SongVO.builder()
                    .id(id)
                    .title(name)
                    .artist(artist)
                    .album(album)
                    .durationSeconds(dur)
                    .coverUrl(cover)
                    .platform(platform)
                    .platformSongId(String.valueOf(id))
                    .build());
        }
        return songs;
    }

    // ===================== AI 重排 + 理由 =====================

    private List<SongVO> rankWithAI(List<SongVO> candidates, MoodParams mood) {
        if (candidates.isEmpty()) return candidates;
        try {
            String prompt = buildRankingPrompt(candidates, mood);
            String response = chatClient.prompt().user(prompt).call().content();
            return applyRanking(response, candidates, mood);
        } catch (Exception e) {
            log.warn("AI ranking failed, using shuffle", e);
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
                // 拷贝并注入 reason
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
}
