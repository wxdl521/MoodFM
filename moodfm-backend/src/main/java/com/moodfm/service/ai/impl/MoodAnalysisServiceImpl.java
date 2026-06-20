package com.moodfm.service.ai.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodfm.ai.model.MoodParams;
import com.moodfm.domain.dto.radio.MoodInputRequest;
import com.moodfm.service.ai.LlmClient;
import com.moodfm.service.ai.LlmFallbackMetrics;
import com.moodfm.service.ai.MoodAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class MoodAnalysisServiceImpl implements MoodAnalysisService {

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final LlmFallbackMetrics llmFallbackMetrics;

    @Value("classpath:prompts/mood-analysis.txt")
    private Resource promptTemplate;

    @Override
    @Cacheable(value = "moodAnalysis", key = "#request.text + '_' + #request.scene")
    public MoodParams analyze(MoodInputRequest request) {
        String userInput = buildUserInput(request);
        if (userInput.isBlank()) {
            return MoodParams.defaultParams();
        }

        try {
            String systemPrompt = new String(promptTemplate.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("{{userInput}}", userInput);

            String content = llmClient.complete(systemPrompt, userInput);
            String json = extractJson(content);
            return objectMapper.readValue(json, MoodParams.class);
        } catch (Exception e) {
            llmFallbackMetrics.moodAnalysisFallback();
            String truncated = userInput.length() > 120 ? userInput.substring(0, 120) + "…" : userInput;
            log.warn("mood analysis fallback: reason={}, input='{}'", e.getMessage(), truncated);
            return MoodParams.defaultParams();
        }
    }

    private String buildUserInput(MoodInputRequest request) {
        if (request.getText() != null && !request.getText().isBlank()) {
            return request.getText();
        }
        if (request.getScene() != null) {
            return "场景：" + request.getScene();
        }
        if (request.getValence() != null && request.getEnergy() != null) {
            return String.format("情绪值:%.1f, 能量值:%.1f", request.getValence(), request.getEnergy());
        }
        return "";
    }

    private String extractJson(String response) {
        if (response == null) return "{}";
        // 去掉可能的 markdown 代码块
        response = response.trim();
        if (response.startsWith("```")) {
            int start = response.indexOf('{');
            int end = response.lastIndexOf('}');
            if (start >= 0 && end > start) {
                return response.substring(start, end + 1);
            }
        }
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        return response;
    }
}
