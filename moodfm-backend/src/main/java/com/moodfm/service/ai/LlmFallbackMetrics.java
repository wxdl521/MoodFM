package com.moodfm.service.ai;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Records observable fallback events for each LLM call site.
 * <p>
 * Prometheus will expose these as {@code moodfm_llm_fallback_total{stage="..."}} counters.
 * Each stage caches its Counter instance to avoid repeated MeterRegistry look-ups.
 */
@Component
@RequiredArgsConstructor
public class LlmFallbackMetrics {

    private static final String METRIC_NAME = "moodfm.llm.fallback";
    private static final String TAG_STAGE = "stage";

    private final MeterRegistry meterRegistry;

    private volatile Counter moodAnalysisCounter;
    private volatile Counter songRankingCounter;
    private volatile Counter songFeatureCounter;

    /** Increment the mood-analysis fallback counter. */
    public void moodAnalysisFallback() {
        moodAnalysis().increment();
    }

    /** Increment the song-ranking fallback counter. */
    public void songRankingFallback() {
        songRanking().increment();
    }

    /** Increment the song-feature fallback counter (reserved for Task 2). */
    public void songFeatureFallback() {
        songFeature().increment();
    }

    // ---- lazy-init helpers (double-checked locking not needed; Counter.increment is thread-safe) ----

    private Counter moodAnalysis() {
        if (moodAnalysisCounter == null) {
            moodAnalysisCounter = Counter.builder(METRIC_NAME)
                    .tag(TAG_STAGE, "mood_analysis")
                    .description("Number of times mood analysis fell back to default params")
                    .register(meterRegistry);
        }
        return moodAnalysisCounter;
    }

    private Counter songRanking() {
        if (songRankingCounter == null) {
            songRankingCounter = Counter.builder(METRIC_NAME)
                    .tag(TAG_STAGE, "song_ranking")
                    .description("Number of times song ranking fell back to limit-20 shuffle")
                    .register(meterRegistry);
        }
        return songRankingCounter;
    }

    private Counter songFeature() {
        if (songFeatureCounter == null) {
            songFeatureCounter = Counter.builder(METRIC_NAME)
                    .tag(TAG_STAGE, "song_feature")
                    .description("Number of times song feature enrichment fell back")
                    .register(meterRegistry);
        }
        return songFeatureCounter;
    }
}
