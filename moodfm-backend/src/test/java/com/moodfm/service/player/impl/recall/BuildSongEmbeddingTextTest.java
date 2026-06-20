package com.moodfm.service.player.impl.recall;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SongEmbeddingTextBuilder#buildSongEmbeddingText(String)}.
 */
class BuildSongEmbeddingTextTest {

    private SongEmbeddingTextBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new SongEmbeddingTextBuilder(new ObjectMapper());
    }

    // -----------------------------------------------------------------------
    // Null / blank / invalid inputs → empty string
    // -----------------------------------------------------------------------

    @Test
    void nullFeatures_returnsEmpty() {
        assertEquals("", builder.buildSongEmbeddingText(null));
    }

    @Test
    void blankFeatures_returnsEmpty() {
        assertEquals("", builder.buildSongEmbeddingText("   "));
    }

    @Test
    void invalidJson_returnsEmpty() {
        assertEquals("", builder.buildSongEmbeddingText("{not-json}"));
    }

    @Test
    void emptyJsonObject_returnsEmpty() {
        // No genre, no mood_tags, no energy, no language → all parts blank → empty
        assertEquals("", builder.buildSongEmbeddingText("{}"));
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
        String result = builder.buildSongEmbeddingText(features);

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
        String result = builder.buildSongEmbeddingText(features);
        assertTrue(result.contains("高能量"), "energy=0.85 → 高能量; got: " + result);
    }

    @Test
    void midEnergy_producesMidEnergyWord() {
        String features = """
                {"genre":"流行","mood_tags":[],"energy":0.5,"language":"zh"}
                """;
        String result = builder.buildSongEmbeddingText(features);
        assertTrue(result.contains("中能量"), "energy=0.5 → 中能量; got: " + result);
    }

    @Test
    void lowEnergy_producesLowEnergyWord() {
        String features = """
                {"genre":"民谣","mood_tags":[],"energy":0.1,"language":"zh"}
                """;
        String result = builder.buildSongEmbeddingText(features);
        assertTrue(result.contains("低能量"), "energy=0.1 → 低能量; got: " + result);
    }

    @Test
    void energyExactly07_producesHighEnergyWord() {
        String features = """
                {"genre":"摇滚","mood_tags":[],"energy":0.7,"language":"en"}
                """;
        String result = builder.buildSongEmbeddingText(features);
        assertTrue(result.contains("高能量"), "energy=0.7 → 高能量 (boundary); got: " + result);
    }

    @Test
    void energyExactly04_producesMidEnergyWord() {
        String features = """
                {"genre":"爵士","mood_tags":[],"energy":0.4,"language":"en"}
                """;
        String result = builder.buildSongEmbeddingText(features);
        assertTrue(result.contains("中能量"), "energy=0.4 → 中能量 (boundary); got: " + result);
    }

    @Test
    void tempoBucketFallback_whenNoEnergyField() {
        // No "energy" field → fall back to tempo_bucket
        String features = """
                {"genre":"摇滚","mood_tags":[],"tempo_bucket":"high","language":"en"}
                """;
        String result = builder.buildSongEmbeddingText(features);
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
        String result = builder.buildSongEmbeddingText(features);
        assertTrue(result.contains("英文"), "language=en → 英文; got: " + result);
    }

    @Test
    void languageJa_producesJapaneseWord() {
        String features = """
                {"genre":"J-Pop","mood_tags":[],"energy":0.5,"language":"ja"}
                """;
        String result = builder.buildSongEmbeddingText(features);
        assertTrue(result.contains("日文"), "language=ja → 日文; got: " + result);
    }

    @Test
    void languageKo_producesKoreanWord() {
        String features = """
                {"genre":"K-Pop","mood_tags":[],"energy":0.5,"language":"ko"}
                """;
        String result = builder.buildSongEmbeddingText(features);
        assertTrue(result.contains("韩文"), "language=ko → 韩文; got: " + result);
    }

    @Test
    void languageInstrumental_producesInstrumentalWord() {
        String features = """
                {"genre":"古典","mood_tags":[],"energy":0.3,"language":"instrumental"}
                """;
        String result = builder.buildSongEmbeddingText(features);
        assertTrue(result.contains("器乐"), "language=instrumental → 器乐; got: " + result);
    }

    @Test
    void unknownLanguage_omitsLanguagePart() {
        String features = """
                {"genre":"流行","mood_tags":[],"energy":0.5,"language":"xx"}
                """;
        String result = builder.buildSongEmbeddingText(features);
        // Just should not throw, and should still have the other parts
        assertTrue(result.contains("流行"), "genre should still be present; got: " + result);
        assertFalse(result.contains("null"), "Should not contain 'null'");
    }
}
