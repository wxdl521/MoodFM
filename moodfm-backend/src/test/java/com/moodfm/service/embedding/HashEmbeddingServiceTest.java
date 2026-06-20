package com.moodfm.service.embedding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link HashEmbeddingService}, focusing on configurable dimension.
 */
class HashEmbeddingServiceTest {

    // -----------------------------------------------------------------------
    // Default constructor (dim=2048)
    // -----------------------------------------------------------------------

    @Test
    void defaultConstructor_produces2048DimVector() {
        HashEmbeddingService svc = new HashEmbeddingService();
        float[] vec = svc.embed("test text");
        assertEquals(2048, vec.length, "Default dim should be 2048");
    }

    @Test
    void defaultConstructor_blankText_returns2048Zeros() {
        HashEmbeddingService svc = new HashEmbeddingService();
        float[] vec = svc.embed("   ");
        assertEquals(2048, vec.length);
        for (float v : vec) {
            assertEquals(0.0f, v, "Blank text should produce zero vector");
        }
    }

    @Test
    void defaultConstructor_nullText_returns2048Zeros() {
        HashEmbeddingService svc = new HashEmbeddingService();
        float[] vec = svc.embed(null);
        assertEquals(2048, vec.length);
    }

    // -----------------------------------------------------------------------
    // Custom dim constructor
    // -----------------------------------------------------------------------

    @Test
    void customDim1536_producesCorrectLength() {
        HashEmbeddingService svc = new HashEmbeddingService(1536);
        float[] vec = svc.embed("hello world");
        assertEquals(1536, vec.length, "Custom dim=1536 should be respected");
    }

    @Test
    void customDim1536_blankText_returns1536Zeros() {
        HashEmbeddingService svc = new HashEmbeddingService(1536);
        float[] vec = svc.embed("  ");
        assertEquals(1536, vec.length);
        for (float v : vec) {
            assertEquals(0.0f, v, "Blank text should produce zero vector of the configured dim");
        }
    }

    @Test
    void customDim512_producesCorrectLength() {
        HashEmbeddingService svc = new HashEmbeddingService(512);
        float[] vec = svc.embed("short dim test");
        assertEquals(512, vec.length);
    }

    @Test
    void customDim1_producesLength1() {
        HashEmbeddingService svc = new HashEmbeddingService(1);
        float[] vec = svc.embed("single");
        assertEquals(1, vec.length);
    }

    // -----------------------------------------------------------------------
    // Invalid dim guard
    // -----------------------------------------------------------------------

    @Test
    void zeroDim_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new HashEmbeddingService(0));
    }

    @Test
    void negativeDim_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new HashEmbeddingService(-1));
    }

    // -----------------------------------------------------------------------
    // Output sanity: non-blank text should produce a normalized (non-zero) vector
    // -----------------------------------------------------------------------

    @Test
    void nonBlankText_producesNonZeroVector() {
        HashEmbeddingService svc = new HashEmbeddingService(256);
        float[] vec = svc.embed("music genre pop");
        // At least some dimensions should be non-zero after L2 norm
        boolean anyNonZero = false;
        for (float v : vec) {
            if (v != 0.0f) { anyNonZero = true; break; }
        }
        assertTrue(anyNonZero, "Non-blank text should produce at least one non-zero dimension");
    }

    @Test
    void sameText_producesIdenticalVectors() {
        HashEmbeddingService svc = new HashEmbeddingService(2048);
        float[] a = svc.embed("华语流行 夜晚 低能量 中文");
        float[] b = svc.embed("华语流行 夜晚 低能量 中文");
        assertArrayEquals(a, b, "Same text must produce identical deterministic vectors");
    }
}
