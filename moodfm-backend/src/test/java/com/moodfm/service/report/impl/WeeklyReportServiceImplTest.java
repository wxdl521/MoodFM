package com.moodfm.service.report.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodfm.common.exception.LlmException;
import com.moodfm.mapper.MoodSessionMapper;
import com.moodfm.mapper.PlayRecordMapper;
import com.moodfm.mapper.UserMapper;
import com.moodfm.mapper.WeeklyReportMapper;
import com.moodfm.service.ai.LlmClient;
import com.moodfm.service.ai.LlmFallbackMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure Mockito unit test for {@link WeeklyReportServiceImpl#callAI}.
 * <p>
 * {@code callAI} is package-private so this test (same package) can call it directly.
 * The prompt resource is injected via {@link ReflectionTestUtils} using the real
 * classpath resource to avoid duplicating the template content.
 */
@ExtendWith(MockitoExtension.class)
class WeeklyReportServiceImplTest {

    @Mock private WeeklyReportMapper weeklyReportMapper;
    @Mock private PlayRecordMapper playRecordMapper;
    @Mock private MoodSessionMapper moodSessionMapper;
    @Mock private UserMapper userMapper;
    @Mock private LlmClient llmClient;
    @Mock private LlmFallbackMetrics llmFallbackMetrics;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private WeeklyReportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WeeklyReportServiceImpl(
                weeklyReportMapper,
                playRecordMapper,
                moodSessionMapper,
                userMapper,
                llmClient,
                llmFallbackMetrics,
                objectMapper
        );
        // Inject the real prompt resource — callAI reads it to build the prompt string
        ReflectionTestUtils.setField(service, "promptResource",
                new ClassPathResource("prompts/weekly-report.txt"));
    }

    // ─── Helper: consistent callAI args ─────────────────────────────

    private WeeklyReportServiceImpl.AiNarrative invokeCallAI() {
        return service.callAI(
                "WK25", "06/14—06/20",
                42L, "3h 12m", "Pop", "专注",
                "0.6", "0.5",
                "Circles", "Mac Miller", "5",
                "3");
    }

    // ─── Test cases ──────────────────────────────────────────────────

    /**
     * Success path: LLM returns valid JSON → AiNarrative fields are parsed correctly
     * and the fallback counter is never incremented.
     */
    @Test
    void success_narrativeParsedAndCounterNotFired() {
        String llmJson = "{\"headlineWord\":\"golden\",\"headlineWord2\":\"drifting.\","
                + "\"titleCn\":\"金色漂流\",\"summary\":\"一周静静流淌\","
                + "\"essayBody\":\"旋律轻柔，时光悄然。\",\"quote\":\"let the music stay\"}";

        when(llmClient.complete(isNull(), anyString())).thenReturn(llmJson);

        WeeklyReportServiceImpl.AiNarrative result = invokeCallAI();

        assertNotNull(result);
        assertEquals("golden", result.headlineWord());
        assertEquals("drifting.", result.headlineWord2());
        assertEquals("金色漂流", result.titleCn());
        assertEquals("一周静静流淌", result.summary());
        assertEquals("旋律轻柔，时光悄然。", result.essayBody());
        assertEquals("let the music stay", result.quote());

        verify(llmFallbackMetrics, never()).weeklyReportFallback();
    }

    /**
     * LLM failure path: LlmClient throws LlmException → default AiNarrative is returned
     * and the fallback counter is incremented exactly once.
     */
    @Test
    void llmException_returnsDefaultNarrativeAndCounterFired() {
        when(llmClient.complete(isNull(), anyString()))
                .thenThrow(new LlmException("upstream timeout", new RuntimeException("connect refused")));

        WeeklyReportServiceImpl.AiNarrative result = invokeCallAI();

        assertNotNull(result);
        assertEquals("quiet",    result.headlineWord());
        assertEquals("light.",   result.headlineWord2());
        assertEquals("静听一周", result.titleCn());
        assertEquals("",         result.summary());
        assertEquals("",         result.essayBody());
        assertEquals("",         result.quote());

        verify(llmFallbackMetrics).weeklyReportFallback();
    }

    /**
     * JSON parse failure path: LLM returns non-JSON text → JSON parsing fails inside
     * callAI, the catch block fires, default narrative is returned and counter incremented.
     */
    @Test
    void nonJsonResponse_returnsDefaultNarrativeAndCounterFired() {
        when(llmClient.complete(isNull(), anyString())).thenReturn("not json at all");

        WeeklyReportServiceImpl.AiNarrative result = invokeCallAI();

        assertNotNull(result);
        assertEquals("quiet",    result.headlineWord());
        assertEquals("light.",   result.headlineWord2());
        assertEquals("静听一周", result.titleCn());

        verify(llmFallbackMetrics).weeklyReportFallback();
    }
}
