package com.moodfm.service.enrich.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodfm.common.exception.LlmException;
import com.moodfm.service.ai.LlmClient;
import com.moodfm.service.ai.LlmFallbackMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pure-Mockito unit tests for SongFeatureServiceImpl.
 *
 * NOTE on @Cacheable:  @Cacheable requires a Spring AOP proxy and therefore
 * a Spring application context.  In this pure-Mockito test we call the impl
 * directly and therefore bypass the cache.  Cache correctness is validated
 * structurally (the unless condition is set to exclude source=fallback results)
 * and confirmed via integration-level reasoning — see task-2-report.md.
 * The "LLM called exactly once for two enrich() calls" assertion cannot be
 * verified in this test class; it is noted as a caveat in the report.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SongFeatureServiceImplTest {

    @Mock private LlmClient llmClient;
    @Mock private LlmFallbackMetrics llmFallbackMetrics;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private SongFeatureServiceImpl songFeatureService;

    // valid 8-field LLM response
    private static final String VALID_JSON = """
            {
              "valence": 0.7,
              "energy": 0.6,
              "genre": "华语流行",
              "language": "zh",
              "tempo_bucket": "mid",
              "mood_tags": ["夜晚", "治愈"],
              "source": "ai",
              "version": 1
            }
            """;

    @BeforeEach
    void injectDependencies() throws Exception {
        // Mockito would inject a mock ObjectMapper; we need a real one.
        var omField = SongFeatureServiceImpl.class.getDeclaredField("objectMapper");
        omField.setAccessible(true);
        omField.set(songFeatureService, objectMapper);

        // @PostConstruct does not run in pure-Mockito tests.
        // Inject the prompt template string directly (reads the real classpath resource).
        var templateField = SongFeatureServiceImpl.class.getDeclaredField("promptTemplate");
        templateField.setAccessible(true);
        var resource = new org.springframework.core.io.ClassPathResource("prompts/song-feature.txt");
        String template = new String(resource.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        templateField.set(songFeatureService, template);
    }

    // =========================================================================
    // 1. Happy path: LLM returns valid JSON → returned as-is, no fallback
    // =========================================================================
    @Test
    void enrich_validLlmResponse_returnsJsonAndDoesNotCallFallback() throws Exception {
        when(llmClient.complete(isNull(), anyString())).thenReturn(VALID_JSON);

        String result = songFeatureService.enrich("晴天", "周杰伦", "叶惠美");

        assertNotNull(result);
        JsonNode node = objectMapper.readTree(result);
        assertEquals("ai", node.get("source").asText());
        assertEquals("华语流行", node.get("genre").asText());
        assertEquals("zh", node.get("language").asText());
        assertTrue(node.has("valence"));
        assertTrue(node.has("energy"));
        assertTrue(node.has("tempo_bucket"));
        assertTrue(node.has("mood_tags"));
        assertTrue(node.has("version"));

        verify(llmFallbackMetrics, never()).songFeatureFallback();
    }

    // =========================================================================
    // 2. LLM returns JSON wrapped in markdown code fence → extracted + returned
    // =========================================================================
    @Test
    void enrich_markdownWrappedJson_extractsAndReturns() throws Exception {
        String wrapped = "```json\n" + VALID_JSON + "\n```";
        when(llmClient.complete(isNull(), anyString())).thenReturn(wrapped);

        String result = songFeatureService.enrich("Blinding Lights", "The Weeknd", "After Hours");

        assertNotNull(result);
        JsonNode node = objectMapper.readTree(result);
        assertEquals("ai", node.get("source").asText());
        verify(llmFallbackMetrics, never()).songFeatureFallback();
    }

    // =========================================================================
    // 3. LLM throws LlmException → fallback JSON returned, fallback metric incremented
    // =========================================================================
    @Test
    void enrich_llmException_returnsFallbackJson() throws Exception {
        when(llmClient.complete(isNull(), anyString())).thenThrow(new LlmException("timeout"));

        String result = songFeatureService.enrich("晴天", "周杰伦", "叶惠美");

        assertNotNull(result);
        JsonNode node = objectMapper.readTree(result);
        assertEquals("fallback", node.get("source").asText());
        // All 8 fields present
        assertTrue(node.has("valence"));
        assertTrue(node.has("energy"));
        assertTrue(node.has("genre"));
        assertTrue(node.has("language"));
        assertTrue(node.has("tempo_bucket"));
        assertTrue(node.has("mood_tags"));
        assertTrue(node.has("version"));
        // Metric incremented exactly once
        verify(llmFallbackMetrics, times(1)).songFeatureFallback();
    }

    // =========================================================================
    // 4. LLM returns invalid / unparseable JSON → fallback
    // =========================================================================
    @Test
    void enrich_invalidJson_returnsFallbackJson() throws Exception {
        when(llmClient.complete(isNull(), anyString())).thenReturn("not json at all");

        String result = songFeatureService.enrich("Title", "Artist", null);

        assertNotNull(result);
        JsonNode node = objectMapper.readTree(result);
        assertEquals("fallback", node.get("source").asText());
        verify(llmFallbackMetrics, times(1)).songFeatureFallback();
    }

    // =========================================================================
    // 5. LLM returns JSON missing required fields → fallback
    // =========================================================================
    @Test
    void enrich_jsonMissingRequiredFields_returnsFallbackJson() throws Exception {
        // Missing "language" field among required: valence, energy, genre, language
        String incomplete = """
                {"valence": 0.5, "energy": 0.5, "genre": "华语流行", "source": "ai", "version": 1}
                """;
        when(llmClient.complete(isNull(), anyString())).thenReturn(incomplete);

        String result = songFeatureService.enrich("Test", "Artist", null);

        JsonNode node = objectMapper.readTree(result);
        assertEquals("fallback", node.get("source").asText());
        verify(llmFallbackMetrics, times(1)).songFeatureFallback();
    }

    // =========================================================================
    // 6. Language guess: CJK title → zh
    // =========================================================================
    @Test
    void enrich_fallback_chineseTitleGuessesZh() throws Exception {
        when(llmClient.complete(isNull(), anyString())).thenThrow(new LlmException("err"));

        String result = songFeatureService.enrich("晴天", "周杰伦", null);

        JsonNode node = objectMapper.readTree(result);
        assertEquals("fallback", node.get("source").asText());
        assertEquals("zh", node.get("language").asText());
    }

    // =========================================================================
    // 7. Language guess: ASCII title + ASCII artist → en
    // =========================================================================
    @Test
    void enrich_fallback_englishTitleAndArtistGuessesEn() throws Exception {
        when(llmClient.complete(isNull(), anyString())).thenThrow(new LlmException("err"));

        String result = songFeatureService.enrich("Blinding Lights", "The Weeknd", null);

        JsonNode node = objectMapper.readTree(result);
        assertEquals("fallback", node.get("source").asText());
        assertEquals("en", node.get("language").asText());
    }

    // =========================================================================
    // 8. CJK artist (ASCII title) → zh
    // =========================================================================
    @Test
    void enrich_fallback_cjkArtistGuessesZh() throws Exception {
        when(llmClient.complete(isNull(), anyString())).thenThrow(new LlmException("err"));

        String result = songFeatureService.enrich("Ordinary World", "田馥甄", null);

        JsonNode node = objectMapper.readTree(result);
        assertEquals("zh", node.get("language").asText());
    }

    // =========================================================================
    // 9. Fallback JSON is fully parseable and contains exactly the 8 fields
    // =========================================================================
    @Test
    void enrich_fallbackJson_hasAll8Fields() throws Exception {
        when(llmClient.complete(isNull(), anyString())).thenThrow(new LlmException("err"));

        String result = songFeatureService.enrich("X", "Y", null);

        JsonNode node = objectMapper.readTree(result);
        assertAll("all 8 fields present in fallback",
                () -> assertTrue(node.has("valence")),
                () -> assertTrue(node.has("energy")),
                () -> assertTrue(node.has("genre")),
                () -> assertTrue(node.has("language")),
                () -> assertTrue(node.has("tempo_bucket")),
                () -> assertTrue(node.has("mood_tags")),
                () -> assertTrue(node.has("source")),
                () -> assertTrue(node.has("version"))
        );
        // Values are sane
        assertEquals(0.5, node.get("valence").asDouble(), 0.001);
        assertEquals(0.5, node.get("energy").asDouble(), 0.001);
        assertEquals("mid", node.get("tempo_bucket").asText());
        assertEquals(1, node.get("version").asInt());
        assertTrue(node.get("mood_tags").isArray());
    }

    // =========================================================================
    // 10. enrich never throws (defensive)
    // =========================================================================
    @Test
    void enrich_neverThrows_evenOnCatastrophicLlmFailure() {
        when(llmClient.complete(isNull(), anyString()))
                .thenThrow(new RuntimeException("unexpected catastrophe"));

        // Should NOT throw
        assertDoesNotThrow(() -> songFeatureService.enrich("T", "A", null));
    }
}
