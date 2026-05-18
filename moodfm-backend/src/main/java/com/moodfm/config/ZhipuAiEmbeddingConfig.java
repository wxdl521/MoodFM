package com.moodfm.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom {@link EmbeddingModel} bean that calls ZhipuAI's embedding API directly,
 * bypassing Spring AI's OpenAI auto-configuration which hardcodes {@code /v1/embeddings}.
 * <p>
 * ZhipuAI endpoint: {@code https://open.bigmodel.cn/api/paas/v4/embeddings}
 * Model: {@code embedding-3}, dimensions: 2048.
 * <p>
 * This bean is marked {@code @Primary} so it takes precedence over the auto-configured
 * {@code OpenAiEmbeddingModel} when both are present.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "zhipu.embedding.api-key")
public class ZhipuAiEmbeddingConfig {

    private static final String ZHIPU_EMBEDDING_URL = "https://open.bigmodel.cn/api/paas/v4/embeddings";
    private static final int DIMENSIONS = 2048;

    @Bean
    @Primary
    public EmbeddingModel zhipuAiEmbeddingModel(
            @Value("${zhipu.embedding.api-key}") String apiKey,
            @Value("${zhipu.embedding.model:embedding-3}") String model) {

        RestClient restClient = RestClient.builder()
                .baseUrl(ZHIPU_EMBEDDING_URL)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();

        return new ZhipuAiEmbeddingModel(restClient, model);
    }

    /**
     * EmbeddingModel implementation backed by ZhipuAI's REST API.
     */
    private static class ZhipuAiEmbeddingModel extends AbstractEmbeddingModel {

        private final RestClient restClient;
        private final String model;
        private final ObjectMapper objectMapper = new ObjectMapper();

        ZhipuAiEmbeddingModel(RestClient restClient, String model) {
            this.restClient = restClient;
            this.model = model;
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<String> inputs = request.getInstructions();
            if (inputs == null || inputs.isEmpty()) {
                throw new IllegalArgumentException("EmbeddingRequest must contain at least one input text");
            }

            try {
                // Build request body: {"model": "embedding-3", "input": ["text1", "text2"]}
                ObjectNode body = objectMapper.createObjectNode();
                body.put("model", model);
                ArrayNode inputArray = body.putArray("input");
                for (String text : inputs) {
                    inputArray.add(text);
                }

                String responseBody = restClient.post()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body.toString())
                        .retrieve()
                        .body(String.class);

                return parseResponse(responseBody, inputs);
            } catch (Exception e) {
                log.error("ZhipuAI embedding API call failed: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to call ZhipuAI embedding API", e);
            }
        }

        @Override
        public float[] embed(Document document) {
            String text = document.getText();
            if (text == null || text.isBlank()) {
                return new float[DIMENSIONS];
            }
            EmbeddingRequest request = new EmbeddingRequest(List.of(text), null);
            EmbeddingResponse response = call(request);
            Embedding result = response.getResult();
            return (result != null) ? result.getOutput() : new float[DIMENSIONS];
        }

        @Override
        public int dimensions() {
            return DIMENSIONS;
        }

        /**
         * Parse ZhipuAI embedding response.
         * <pre>
         * {
         *   "data": [
         *     { "embedding": [0.0023064255, -0.009327292, ...], "index": 0 }
         *   ],
         *   "model": "embedding-3",
         *   "usage": { "prompt_tokens": 8, "total_tokens": 8 }
         * }
         * </pre>
         */
        private EmbeddingResponse parseResponse(String responseBody, List<String> inputs) {
            try {
                JsonNode root = objectMapper.readTree(responseBody);
                JsonNode dataArray = root.get("data");

                if (dataArray == null || !dataArray.isArray() || dataArray.isEmpty()) {
                    throw new RuntimeException("ZhipuAI response missing 'data' array or data is empty: " + responseBody);
                }

                String responseModel = root.has("model") ? root.get("model").asText() : model;

                List<Embedding> embeddings = new ArrayList<>();
                for (JsonNode item : dataArray) {
                    int index = item.has("index") ? item.get("index").asInt() : 0;
                    JsonNode embeddingArray = item.get("embedding");

                    if (embeddingArray == null || !embeddingArray.isArray()) {
                        throw new RuntimeException("ZhipuAI response item missing 'embedding' array at index " + index);
                    }

                    float[] vector = new float[embeddingArray.size()];
                    for (int i = 0; i < embeddingArray.size(); i++) {
                        vector[i] = embeddingArray.get(i).numberValue().floatValue();
                    }

                    Embedding embedding = new Embedding(vector, index);
                    embeddings.add(embedding);
                }

                EmbeddingResponseMetadata metadata = new EmbeddingResponseMetadata(responseModel, null);
                return new EmbeddingResponse(embeddings, metadata);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse ZhipuAI embedding response", e);
            }
        }
    }
}
