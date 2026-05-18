package com.moodfm.service.embedding;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Real embedding service backed by Spring AI's EmbeddingModel (OpenAI-compatible API).
 * <p>
 * Delegates to the auto-configured {@link EmbeddingModel} bean (e.g. OpenAI text-embedding-3-small).
 * On failure, gracefully falls back to the deterministic hash-based embedding so the
 * recommendation pipeline never crashes due to a transient embedding API outage.
 */
@Slf4j
@Service
public class OpenAiEmbeddingService implements EmbeddingService {

    @Autowired(required = false)
    private EmbeddingModel embeddingModel;
    private final HashEmbeddingService hashFallback = new HashEmbeddingService();

    @Override
    @Cacheable(value = "embeddings", key = "#text")
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new float[2048];
        }

        try {
            if (embeddingModel == null) {
                return hashFallback.embed(text);
            }
            float[] vector = embeddingModel.embed(text);
            if (vector != null && vector.length > 0) {
                return vector;
            }
            log.warn("EmbeddingModel returned null/empty vector for text '{}', falling back to hash", text);
            return hashFallback.embed(text);
        } catch (Exception e) {
            log.warn("Embedding API call failed for text '{}': {}. Falling back to hash embedding.",
                    truncate(text, 80), e.getMessage());
            return hashFallback.embed(text);
        }
    }

    private static String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
