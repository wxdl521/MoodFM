package com.moodfm.service.player.impl.ranking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodfm.ai.model.MoodParams;
import com.moodfm.domain.vo.SongVO;
import com.moodfm.service.ai.LlmClient;
import com.moodfm.service.ai.LlmFallbackMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class AiRankingService {

    private final LlmClient llmClient;
    private final LlmFallbackMetrics llmFallbackMetrics;
    private final ObjectMapper objectMapper;

    public List<SongVO> rank(List<SongVO> candidates, MoodParams mood) {
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

    /**
     * Format a single candidate line for the rerank prompt.
     * Format: 编号|歌名|艺人|valence|energy|genre|mood_tags
     * Any missing/unparseable feature field falls back to "未知".
     * Package-visible for direct unit testing.
     */
    String formatCandidateLine(int idx, SongVO s) {
        String valence  = "未知";
        String energy   = "未知";
        String genre    = "未知";
        String moodTags = "未知";

        String featuresJson = s.getFeatures();
        if (featuresJson != null && !featuresJson.isBlank()) {
            try {
                JsonNode node = objectMapper.readTree(featuresJson);
                valence  = node.path("valence").asText("未知");
                energy   = node.path("energy").asText("未知");
                genre    = node.path("genre").asText("未知");

                JsonNode tagsNode = node.path("mood_tags");
                if (tagsNode.isArray() && tagsNode.size() > 0) {
                    StringBuilder tags = new StringBuilder();
                    for (JsonNode tag : tagsNode) {
                        if (tags.length() > 0) tags.append(",");
                        tags.append(tag.asText(""));
                    }
                    String tagsStr = tags.toString();
                    moodTags = tagsStr.isEmpty() ? "未知" : tagsStr;
                }
            } catch (Exception ignored) {
                // parse failure → all fields remain "未知"
            }
        }

        return idx + "|" + s.getTitle() + "|" + s.getArtist()
                + "|" + valence + "|" + energy + "|" + genre + "|" + moodTags + "\n";
    }

    private String buildRankingPrompt(List<SongVO> candidates, MoodParams mood) {
        try {
            String template = new String(
                    new ClassPathResource("prompts/song-ranking.txt").getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);

            StringBuilder songList = new StringBuilder();
            for (int i = 0; i < candidates.size(); i++) {
                songList.append(formatCandidateLine(i + 1, candidates.get(i)));
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

    public String buildMoodSummary(MoodParams mood) {
        String scene = mood.getSceneInferred() != null ? mood.getSceneInferred() : "";
        List<String> vibes = mood.getVibeKeywords();
        if (vibes == null || vibes.isEmpty()) return scene;
        return scene + " · " + String.join(" ", vibes.subList(0, Math.min(3, vibes.size())));
    }
}
