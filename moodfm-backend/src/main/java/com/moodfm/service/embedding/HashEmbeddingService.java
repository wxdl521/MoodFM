package com.moodfm.service.embedding;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/**
 * Deterministic hash-based mock embedding service (128-dim).
 * <p>
 * Produces similar vectors for similar text by:
 * - Tokenizing on whitespace
 * - Hashing each token into a fixed-dimensional sub-space
 * - Accumulating token contributions into a 128-dim vector
 * - L2-normalizing the result
 * <p>
 * This is a placeholder: swap for a real embedding model (OpenAI, BGE, etc.) later.
 */
@Service
public class HashEmbeddingService implements EmbeddingService {

    private static final int DIM = 128;

    @Override
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new float[DIM];
        }

        float[] vec = new float[DIM];
        String[] tokens = text.toLowerCase().split("\\s+");

        for (String token : tokens) {
            if (token.isEmpty()) continue;

            // CRC32-based deterministic hash per token
            CRC32 crc = new CRC32();
            crc.update(token.getBytes(StandardCharsets.UTF_8));
            long hash = crc.getValue();

            // Each token influences a subset of dimensions
            int baseIdx = (int) (hash % DIM + DIM) % DIM;
            float sign = ((hash & 1) == 0) ? 1.0f : -1.0f;
            float magnitude = 1.0f / (1 + token.length());

            // Spread token influence across 8 consecutive dimensions
            for (int i = 0; i < 8; i++) {
                int idx = (baseIdx + i) % DIM;
                vec[idx] += sign * magnitude * (1.0f + (i * 0.1f));
            }

            // Secondary hash for broader distribution
            CRC32 crc2 = new CRC32();
            crc2.update((token + "_2").getBytes(StandardCharsets.UTF_8));
            long hash2 = crc2.getValue();
            int baseIdx2 = (int) (hash2 % DIM + DIM) % DIM;
            for (int i = 0; i < 4; i++) {
                int idx = (baseIdx2 + i) % DIM;
                vec[idx] += sign * magnitude * 0.5f;
            }
        }

        // L2 normalize
        float norm = 0f;
        for (float v : vec) norm += v * v;
        norm = (float) Math.sqrt(norm);
        if (norm > 1e-6f) {
            for (int i = 0; i < DIM; i++) vec[i] /= norm;
        }

        return vec;
    }
}
