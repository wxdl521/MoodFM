package com.moodfm.service.vector;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Records observable failure events for the vector recall pipeline.
 * <p>
 * Prometheus will expose these as {@code moodfm_vector_failure_total{op="..."}} counters.
 * All counters are initialized eagerly in the constructor to ensure thread safety under
 * concurrent invocations (e.g. virtual-thread async contexts).
 * <p>
 * Mirrors the pattern of {@link com.moodfm.service.ai.LlmFallbackMetrics}.
 */
@Component
public class VectorRecallMetrics {

    private static final String METRIC_NAME = "moodfm.vector.failure";
    private static final String TAG_OP = "op";

    private final Counter recallCounter;
    private final Counter indexCounter;

    public VectorRecallMetrics(MeterRegistry meterRegistry) {
        this.recallCounter = Counter.builder(METRIC_NAME)
                .tag(TAG_OP, "recall")
                .description("Number of times vector recall (fetchVectorSimilar) failed")
                .register(meterRegistry);
        this.indexCounter = Counter.builder(METRIC_NAME)
                .tag(TAG_OP, "index")
                .description("Number of times vector indexing (indexSongForVectorSearch) failed")
                .register(meterRegistry);
    }

    /** Increment the vector recall failure counter. */
    public void recallFailure() {
        recallCounter.increment();
    }

    /** Increment the vector index failure counter. */
    public void indexFailure() {
        indexCounter.increment();
    }
}
