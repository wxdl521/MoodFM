package com.moodfm.service.player.impl.recall;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodfm.ai.model.MoodParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds the text strings used for vector embedding on both the query side
 * ({@link #buildVectorQueryText}) and the catalog/index side
 * ({@link #buildSongEmbeddingText}).
 * <p>
 * Extracted from {@code PlayerServiceImpl} as a leaf collaborator shared by
 * later recall and catalog task classes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SongEmbeddingTextBuilder {

    private final ObjectMapper objectMapper;

    /**
     * Build a text query for vector embedding from mood params + user preferences.
     */
    public String buildVectorQueryText(MoodParams mood, List<String> genres, List<String> vibes) {
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
     * Build the song-side embedding text from a features JSON.
     * The resulting text is structurally aligned with the query-side {@link #buildVectorQueryText}:
     * both use genre + mood/vibe words + energy-level word + language word in Chinese.
     * <p>
     * Format: {@code "华语流行 夜晚 松弛 低能量 中文"}
     * <p>
     * Returns empty string when features are null/blank/unparseable — callers must
     * skip indexing in that case (do NOT fall back to song-name text).
     */
    public String buildSongEmbeddingText(String featuresJson) {
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
}
