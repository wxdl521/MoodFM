package com.moodfm.service.embedding;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/**
 * Deterministic hash-based mock embedding service.
 * <p>
 * Produces similar vectors for similar text by:
 * - Tokenizing on whitespace
 * - Hashing each token into a fixed-dimensional sub-space
 * - Accumulating token contributions into a {@code dim}-dimensional vector
 * - L2-normalizing the result
 * <p>
 * This is a fallback — the primary service is {@link OpenAiEmbeddingService} which uses
 * a real embedding API. This class is only used when the API is unavailable.
 * <p>
 * The default no-arg constructor uses {@code dim=2048} to preserve existing behavior.
 * Use {@link #HashEmbeddingService(int)} to configure a custom dimension.
 */
public class HashEmbeddingService implements EmbeddingService {

    private final int dim;

    /** Default constructor: 2048-dim (backward-compatible). */
    public HashEmbeddingService() {
        this(2048);
    }

    /** Configurable-dimension constructor. */
    public HashEmbeddingService(int dim) {
        if (dim <= 0) throw new IllegalArgumentException("dim must be positive, got: " + dim);
        this.dim = dim;
    }

    @Override
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new float[dim];
        }

        float[] vec = new float[dim];
        String[] tokens = text.toLowerCase().split("\\s+");

        for (String token : tokens) {
            if (token.isEmpty()) continue;

            // CRC32-based deterministic hash per token
            CRC32 crc = new CRC32();
            crc.update(token.getBytes(StandardCharsets.UTF_8));
            long hash = crc.getValue();

            // Each token influences a subset of dimensions
            int baseIdx = (int) (hash % dim + dim) % dim;
            float sign = ((hash & 1) == 0) ? 1.0f : -1.0f;
            float magnitude = 1.0f / (1 + token.length());

            // Spread token influence across 8 consecutive dimensions
            for (int i = 0; i < 8; i++) {
                int idx = (baseIdx + i) % dim;
                vec[idx] += sign * magnitude * (1.0f + (i * 0.1f));
            }

            // Secondary hash for broader distribution
            CRC32 crc2 = new CRC32();
            crc2.update((token + "_2").getBytes(StandardCharsets.UTF_8));
            long hash2 = crc2.getValue();
            int baseIdx2 = (int) (hash2 % dim + dim) % dim;
            for (int i = 0; i < 4; i++) {
                int idx = (baseIdx2 + i) % dim;
                vec[idx] += sign * magnitude * 0.5f;
            }
        }

        // L2 normalize
        float norm = 0f;
        for (float v : vec) norm += v * v;
        norm = (float) Math.sqrt(norm);
        if (norm > 1e-6f) {
            for (int i = 0; i < dim; i++) vec[i] /= norm;
        }

        return vec;
    }
}
