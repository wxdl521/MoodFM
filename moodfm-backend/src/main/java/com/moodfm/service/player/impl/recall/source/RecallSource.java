package com.moodfm.service.player.impl.recall.source;

import com.moodfm.domain.vo.SongVO;
import com.moodfm.service.player.impl.recall.RecallContext;

import java.util.List;

/**
 * Strategy interface for a single recall path in the candidate pipeline.
 * Implementations are Spring {@code @Component} beans annotated with
 * {@code @Order(N)} so the injected {@code List<RecallSource>} is sorted
 * liked→recommend→genre→vibe→explore→vector (dedup putIfAbsent priority).
 */
public interface RecallSource {

    /** Source weight; merged via {@code Math::max} in {@code addSource} when
     *  the same song appears in multiple paths. */
    double weight();

    /** Log/observability label. Canonical names:
     *  liked / recommend / genre-search / vibe-search / explore-search / vector-search. */
    String sourceName();

    /** Fetch candidates. ANY exception MUST be caught internally and return
     *  {@code List.of()} (preserves the existing fetchXxx try/catch behavior). */
    List<SongVO> recall(RecallContext ctx);
}
