package com.moodfm.service.player.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodfm.ai.model.MoodParams;
import com.moodfm.client.music.MusicApiClient;
import com.moodfm.common.util.AesUtil;
import com.moodfm.mapper.FeedbackEventMapper;
import com.moodfm.mapper.GlobalBlacklistMapper;
import com.moodfm.mapper.MoodSessionMapper;
import com.moodfm.mapper.PlatformSongMappingMapper;
import com.moodfm.mapper.SongMapper;
import com.moodfm.mapper.UserProfileMapper;
import com.moodfm.service.ai.LlmClient;
import com.moodfm.service.ai.LlmFallbackMetrics;
import com.moodfm.service.ai.MoodAnalysisService;
import com.moodfm.service.embedding.EmbeddingService;
import com.moodfm.service.enrich.SongFeatureService;
import com.moodfm.service.platform.PlatformBindingService;
import com.moodfm.service.user.UserService;
import com.moodfm.service.vector.QdrantService;
import com.moodfm.service.vector.VectorRecallMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PlayerServiceImpl#buildSongEmbeddingText(String)}.
 * The method is package-visible so we test it directly within the same package.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BuildSongEmbeddingTextTest {

    // All mocks required by @RequiredArgsConstructor on PlayerServiceImpl
    @Mock private MoodAnalysisService moodAnalysisService;
    @Mock private PlatformBindingService platformBindingService;
    @Mock private MusicApiClient musicApiClient;
    @Mock private MoodSessionMapper sessionMapper;
    @Mock private FeedbackEventMapper feedbackEventMapper;
    @Mock private SongMapper songMapper;
    @Mock private PlatformSongMappingMapper platformSongMappingMapper;
    @Mock private UserProfileMapper userProfileMapper;
    @Mock private UserService userService;
    @Mock private AesUtil aesUtil;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private LlmClient llmClient;
    @Mock private LlmFallbackMetrics llmFallbackMetrics;
    @Mock private EmbeddingService embeddingService;
    @Mock private QdrantService qdrantService;
    @Mock private VectorRecallMetrics vectorRecallMetrics;
    @Mock private GlobalBlacklistMapper globalBlacklistMapper;
    @Mock private SongFeatureService songFeatureService;

    @InjectMocks
    private PlayerServiceImpl playerService;

    @BeforeEach
    void setUp() throws Exception {
        var field = PlayerServiceImpl.class.getDeclaredField("objectMapper");
        field.setAccessible(true);
        field.set(playerService, new ObjectMapper());
        ReflectionTestUtils.setField(playerService, "enrichTimeoutSeconds", 8);
    }

    // -----------------------------------------------------------------------
    // Null / blank / invalid inputs → empty string
    // -----------------------------------------------------------------------

    @Test
    void nullFeatures_returnsEmpty() {
        assertEquals("", playerService.buildSongEmbeddingText(null));
    }

    @Test
    void blankFeatures_returnsEmpty() {
        assertEquals("", playerService.buildSongEmbeddingText("   "));
    }

    @Test
    void invalidJson_returnsEmpty() {
        assertEquals("", playerService.buildSongEmbeddingText("{not-json}"));
    }

    @Test
    void emptyJsonObject_returnsEmpty() {
        // No genre, no mood_tags, no energy, no language → all parts blank → empty
        assertEquals("", playerService.buildSongEmbeddingText("{}"));
    }

    // -----------------------------------------------------------------------
    // Full feature set: genre + mood_tags + energy + language
    // -----------------------------------------------------------------------

    @Test
    void fullFeatures_containsAllParts() {
        String features = """
                {
                  "genre": "华语流行",
                  "mood_tags": ["夜晚", "松弛"],
                  "energy": 0.2,
                  "valence": 0.3,
                  "language": "zh",
                  "tempo_bucket": "low",
                  "source": "ai",
                  "version": 1
                }
                """;
        String result = playerService.buildSongEmbeddingText(features);

        assertFalse(result.isEmpty());
        assertTrue(result.contains("华语流行"), "Should contain genre");
        assertTrue(result.contains("夜晚"), "Should contain mood tag");
        assertTrue(result.contains("松弛"), "Should contain mood tag");
        assertTrue(result.contains("低能量"), "energy=0.2 → 低能量");
        assertTrue(result.contains("中文"), "language=zh → 中文");
    }

    // -----------------------------------------------------------------------
    // Energy thresholds
    // -----------------------------------------------------------------------

    @Test
    void highEnergy_producesHighEnergyWord() {
        String features = """
                {"genre":"电子","mood_tags":[],"energy":0.85,"language":"en"}
                """;
        String result = playerService.buildSongEmbeddingText(features);
        assertTrue(result.contains("高能量"), "energy=0.85 → 高能量; got: " + result);
    }

    @Test
    void midEnergy_producesMidEnergyWord() {
        String features = """
                {"genre":"流行","mood_tags":[],"energy":0.5,"language":"zh"}
                """;
        String result = playerService.buildSongEmbeddingText(features);
        assertTrue(result.contains("中能量"), "energy=0.5 → 中能量; got: " + result);
    }

    @Test
    void lowEnergy_producesLowEnergyWord() {
        String features = """
                {"genre":"民谣","mood_tags":[],"energy":0.1,"language":"zh"}
                """;
        String result = playerService.buildSongEmbeddingText(features);
        assertTrue(result.contains("低能量"), "energy=0.1 → 低能量; got: " + result);
    }

    @Test
    void energyExactly07_producesHighEnergyWord() {
        String features = """
                {"genre":"摇滚","mood_tags":[],"energy":0.7,"language":"en"}
                """;
        String result = playerService.buildSongEmbeddingText(features);
        assertTrue(result.contains("高能量"), "energy=0.7 → 高能量 (boundary); got: " + result);
    }

    @Test
    void energyExactly04_producesMidEnergyWord() {
        String features = """
                {"genre":"爵士","mood_tags":[],"energy":0.4,"language":"en"}
                """;
        String result = playerService.buildSongEmbeddingText(features);
        assertTrue(result.contains("中能量"), "energy=0.4 → 中能量 (boundary); got: " + result);
    }

    @Test
    void tempoBucketFallback_whenNoEnergyField() {
        // No "energy" field → fall back to tempo_bucket
        String features = """
                {"genre":"摇滚","mood_tags":[],"tempo_bucket":"high","language":"en"}
                """;
        String result = playerService.buildSongEmbeddingText(features);
        assertTrue(result.contains("高能量"), "tempo_bucket=high → 高能量; got: " + result);
    }

    // -----------------------------------------------------------------------
    // Language mapping
    // -----------------------------------------------------------------------

    @Test
    void languageEn_producesEnglishWord() {
        String features = """
                {"genre":"流行","mood_tags":[],"energy":0.5,"language":"en"}
                """;
        String result = playerService.buildSongEmbeddingText(features);
        assertTrue(result.contains("英文"), "language=en → 英文; got: " + result);
    }

    @Test
    void languageJa_producesJapaneseWord() {
        String features = """
                {"genre":"J-Pop","mood_tags":[],"energy":0.5,"language":"ja"}
                """;
        String result = playerService.buildSongEmbeddingText(features);
        assertTrue(result.contains("日文"), "language=ja → 日文; got: " + result);
    }

    @Test
    void languageKo_producesKoreanWord() {
        String features = """
                {"genre":"K-Pop","mood_tags":[],"energy":0.5,"language":"ko"}
                """;
        String result = playerService.buildSongEmbeddingText(features);
        assertTrue(result.contains("韩文"), "language=ko → 韩文; got: " + result);
    }

    @Test
    void languageInstrumental_producesInstrumentalWord() {
        String features = """
                {"genre":"古典","mood_tags":[],"energy":0.3,"language":"instrumental"}
                """;
        String result = playerService.buildSongEmbeddingText(features);
        assertTrue(result.contains("器乐"), "language=instrumental → 器乐; got: " + result);
    }

    @Test
    void unknownLanguage_omitsLanguagePart() {
        String features = """
                {"genre":"流行","mood_tags":[],"energy":0.5,"language":"xx"}
                """;
        String result = playerService.buildSongEmbeddingText(features);
        // Just should not throw, and should still have the other parts
        assertTrue(result.contains("流行"), "genre should still be present; got: " + result);
        assertFalse(result.contains("null"), "Should not contain 'null'");
    }
}
