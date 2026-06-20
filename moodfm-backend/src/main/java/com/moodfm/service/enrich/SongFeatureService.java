package com.moodfm.service.enrich;

/**
 * Enriches a song with structured emotion features by calling the LLM.
 * The result is a JSON string containing the 8 canonical fields
 * (valence, energy, genre, language, tempo_bucket, mood_tags, source, version).
 *
 * <p>Implementations MUST never throw: any LLM/network failure falls back to a
 * safe default JSON with {@code source="fallback"}.
 */
public interface SongFeatureService {

    /**
     * Returns the JSON feature string for the given song.
     *
     * <p>Results are cached by (title, artist).  Fallback results (source=fallback)
     * are intentionally excluded from the cache so the next call can retry the LLM.
     *
     * @param title  song title (must not be null)
     * @param artist artist name (must not be null)
     * @param album  album name (may be null)
     * @return a valid JSON string with the 8 feature fields; never null, never throws
     */
    String enrich(String title, String artist, String album);

    /**
     * Returns a local fallback feature JSON without calling the LLM.
     *
     * <p>Intended for use when an async enrichment future has timed out or
     * completed exceptionally — avoids a second blocking LLM round-trip.
     * Does NOT increment any fallback metric (no double-counting; {@link #enrich}
     * already counts its own failures).
     *
     * @param title  song title (may be null)
     * @param artist artist name (may be null)
     * @param album  album name (may be null)
     * @return a valid JSON string with {@code source="fallback"}; never null, never throws
     */
    String fallbackFeatures(String title, String artist, String album);
}
