package com.moodfm.service.player.impl.ranking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodfm.domain.vo.SongVO;
import com.moodfm.service.ai.LlmClient;
import com.moodfm.service.ai.LlmFallbackMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class AiRankingServiceTest {

    @Mock private LlmClient llmClient;
    @Mock private LlmFallbackMetrics llmFallbackMetrics;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AiRankingService aiRankingService;

    @BeforeEach
    void setUp() {
        aiRankingService = new AiRankingService(llmClient, llmFallbackMetrics, objectMapper);
    }

    // ========================================================================
    // formatCandidateLine: features-present path injects valence/energy/genre/mood_tags
    // ========================================================================
    @Test
    void formatCandidateLine_withFeatures_injectsAllFields() {
        SongVO song = SongVO.builder()
                .title("月光")
                .artist("陈奕迅")
                .features("{\"valence\":0.2,\"energy\":0.3,\"genre\":\"民谣\",\"mood_tags\":[\"夜晚\",\"松弛\"]}")
                .build();

        String line = aiRankingService.formatCandidateLine(1, song);

        assertTrue(line.contains("0.2"),    "valence should appear in line; got: " + line);
        assertTrue(line.contains("0.3"),    "energy should appear in line; got: " + line);
        assertTrue(line.contains("民谣"),   "genre should appear in line; got: " + line);
        assertTrue(line.contains("夜晚"),   "first mood_tag should appear in line; got: " + line);
        // Should NOT contain the unknown fallback for any field
        assertFalse(line.contains("未知"),  "no field should fall back to 未知 when features are complete; got: " + line);
    }

    @Test
    void formatCandidateLine_nullFeatures_allFieldsFallToUnknown() {
        SongVO song = SongVO.builder()
                .title("静夜思")
                .artist("周杰伦")
                .features(null)
                .build();

        String line = aiRankingService.formatCandidateLine(2, song);

        // All four feature columns should be "未知"
        long unknownCount = line.chars().filter(c -> c == '|').count();
        // Format: idx|title|artist|valence|energy|genre|mood_tags\n → 6 pipes
        assertEquals(6, unknownCount, "line should have 6 pipe separators; got: " + line);
        // Count occurrences of 未知
        int count = 0;
        int idx = 0;
        while ((idx = line.indexOf("未知", idx)) != -1) { count++; idx += 2; }
        assertEquals(4, count, "all 4 feature fields should be 未知 when features is null; got: " + line);
    }

    @Test
    void formatCandidateLine_emptyMoodTags_fallsBackToUnknown() {
        SongVO song = SongVO.builder()
                .title("空白")
                .artist("无名")
                .features("{\"valence\":0.5,\"energy\":0.5,\"genre\":\"流行\",\"mood_tags\":[]}")
                .build();

        String line = aiRankingService.formatCandidateLine(3, song);

        assertTrue(line.contains("流行"), "genre should appear; got: " + line);
        // mood_tags is empty array → should fall back to 未知
        assertTrue(line.endsWith("未知\n"), "empty mood_tags should produce 未知; got: " + line);
    }
}
