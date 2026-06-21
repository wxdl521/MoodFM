package com.moodfm.service.player.impl.recall;

import com.moodfm.ai.model.MoodParams;

/**
 * Immutable carrier for a single recall pipeline invocation.
 * Built once at the top of {@code CandidateRecallService.recallSongs} after
 * keyword derivation, then passed to every {@link source.RecallSource} bean.
 */
public record RecallContext(
        String platform, String cookie, MoodParams mood, Long userId,
        String genreKw, String vibeKw, String exploreKw, String vectorQueryText) {}
