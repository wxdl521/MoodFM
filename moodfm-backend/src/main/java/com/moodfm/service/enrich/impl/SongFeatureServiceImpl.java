package com.moodfm.service.enrich.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodfm.service.ai.LlmClient;
import com.moodfm.service.ai.LlmFallbackMetrics;
import com.moodfm.service.enrich.SongFeatureService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Enriches a song with structured 8-field emotion features via LLM inference.
 *
 * <p>Results are cached by (title, artist) so each distinct song is enriched
 * at most once per JVM lifetime (up to the Caffeine size limit).  Fallback
 * results (source=fallback) are excluded from the cache via the {@code unless}
 * attribute so they can be retried on the next call.
 *
 * <p>This service NEVER throws. Any failure — LLM exception, network error,
 * invalid/incomplete JSON — is silently degraded to a "fallback" feature set,
 * the fallback metric is incremented, and a WARN is emitted.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SongFeatureServiceImpl implements SongFeatureService {

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final LlmFallbackMetrics llmFallbackMetrics;

    @Value("classpath:prompts/song-feature.txt")
    private Resource promptTemplateResource;

    /** Eagerly loaded at startup so the Resource is not re-opened on every call. */
    private String promptTemplate;

    /** Matches CJK Unified Ideographs, Hiragana, Katakana, Hangul. */
    private static final Pattern CJK_PATTERN = Pattern.compile(
            "[\\u4E00-\\u9FFF\\u3040-\\u309F\\u30A0-\\u30FF\\uAC00-\\uD7AF]");

    @PostConstruct
    void loadTemplate() throws Exception {
        this.promptTemplate = new String(
                promptTemplateResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        log.info("Loaded song-feature prompt template ({} chars)", promptTemplate.length());
    }

    @Override
    @Cacheable(
            value  = "songFeatures",
            key    = "#title + '|' + #artist",
            unless = "#result == null || #result.contains('\"source\":\"fallback\"')"
    )
    public String enrich(String title, String artist, String album) {
        try {
            String filledPrompt = promptTemplate
                    .replace("{{title}}",  title  != null ? title  : "")
                    .replace("{{artist}}", artist != null ? artist : "")
                    .replace("{{album}}",  album  != null ? album  : "");

            String raw = llmClient.complete(null, filledPrompt);
            String json = extractJson(raw);

            // Validate: must be parseable JSON with the four required core fields
            JsonNode node = objectMapper.readTree(json);
            if (!hasRequiredFields(node)) {
                throw new IllegalStateException("LLM response missing required feature fields: " + json);
            }
            return json;

        } catch (Exception e) {
            llmFallbackMetrics.songFeatureFallback();
            log.warn("song feature fallback: title='{}', artist='{}', reason={}",
                    title, artist, e.getMessage());
            return buildFallbackJson(title, artist);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Strips optional markdown code fences and extracts the outermost JSON object.
     */
    private String extractJson(String response) {
        if (response == null) return "{}";
        response = response.trim();
        if (response.startsWith("```")) {
            int start = response.indexOf('{');
            int end   = response.lastIndexOf('}');
            if (start >= 0 && end > start) {
                return response.substring(start, end + 1);
            }
        }
        int start = response.indexOf('{');
        int end   = response.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        return response;
    }

    /** Validates that all four minimum required fields are present. */
    private boolean hasRequiredFields(JsonNode node) {
        return node.has("valence")
                && node.has("energy")
                && node.has("genre")
                && node.has("language");
    }

    @Override
    public String fallbackFeatures(String title, String artist, String album) {
        return buildFallbackJson(title, artist);
    }

    /**
     * Builds a safe fallback feature JSON.
     *
     * <p>Language guess: if title or artist contains CJK/Hiragana/Katakana/Hangul → "zh",
     * otherwise → "en".
     */
    private String buildFallbackJson(String title, String artist) {
        String lang = guessLanguage(title, artist);
        try {
            return objectMapper.writeValueAsString(java.util.Map.of(
                    "valence",      0.5,
                    "energy",       0.5,
                    "genre",        "未知",
                    "language",     lang,
                    "tempo_bucket", "mid",
                    "mood_tags",    java.util.List.of(),
                    "source",       "fallback",
                    "version",      1
            ));
        } catch (Exception ex) {
            // Absolute last resort — hand-crafted literal so we truly never throw
            return "{\"valence\":0.5,\"energy\":0.5,\"genre\":\"未知\",\"language\":\"" + lang
                    + "\",\"tempo_bucket\":\"mid\",\"mood_tags\":[],\"source\":\"fallback\",\"version\":1}";
        }
    }

    private String guessLanguage(String title, String artist) {
        String combined = (title != null ? title : "") + (artist != null ? artist : "");
        return CJK_PATTERN.matcher(combined).find() ? "zh" : "en";
    }
}
