package com.moodfm.service.embedding;

/**
 * Text-to-vector embedding service.
 * Implementations can use hash-based mocks, local models, or remote embedding APIs.
 */
public interface EmbeddingService {

    /**
     * Generate a vector embedding for the given text.
     *
     * @param text the input text to embed
     * @return a float array representing the embedding vector
     */
    float[] embed(String text);
}
