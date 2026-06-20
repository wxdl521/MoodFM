package com.moodfm.service.ai;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Records observable fallback events for each LLM call site.
 * <p>
 * Prometheus will expose these as {@code moodfm_llm_fallback_total{stage="..."}} counters.
 * All counters are initialized eagerly in the constructor to ensure thread safety under
 * concurrent invocations (e.g. virtual-thread async contexts).
 */
@Component
public class LlmFallbackMetrics {

    private static final String METRIC_NAME = "moodfm.llm.fallback";
    private static final String TAG_STAGE = "stage";

    private final Counter moodAnalysisCounter;
    private final Counter songRankingCounter;
    private final Counter songFeatureCounter;

    public LlmFallbackMetrics(MeterRegistry meterRegistry) {
        this.moodAnalysisCounter = Counter.builder(METRIC_NAME)
                .tag(TAG_STAGE, "mood_analysis")
                .description("Number of times mood analysis fell back to default params")
                .register(meterRegistry);
        this.songRankingCounter = Counter.builder(METRIC_NAME)
                .tag(TAG_STAGE, "song_ranking")
                .description("Number of times song ranking fell back to limit-20 shuffle")
                .register(meterRegistry);
        this.songFeatureCounter = Counter.builder(METRIC_NAME)
                .tag(TAG_STAGE, "song_feature")
                .description("Number of times song feature enrichment fell back")
                .register(meterRegistry);
    }

    /** Increment the mood-analysis fallback counter. */
    public void moodAnalysisFallback() {
        moodAnalysisCounter.increment();
    }

    /** Increment the song-ranking fallback counter. */
    public void songRankingFallback() {
        songRankingCounter.increment();
    }

    /** Increment the song-feature fallback counter (reserved for Task 2). */
    public void songFeatureFallback() {
        songFeatureCounter.increment();
    }
}
