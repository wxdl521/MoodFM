package com.moodfm.service.player.impl.catalog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moodfm.domain.entity.PlatformSongMapping;
import com.moodfm.domain.entity.Song;
import com.moodfm.domain.vo.SongVO;
import com.moodfm.mapper.PlatformSongMappingMapper;
import com.moodfm.mapper.SongMapper;
import com.moodfm.service.embedding.EmbeddingService;
import com.moodfm.service.enrich.SongFeatureService;
import com.moodfm.service.player.impl.recall.SongEmbeddingTextBuilder;
import com.moodfm.service.vector.QdrantService;
import com.moodfm.service.vector.VectorRecallMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class SongCatalogService {

    private final SongMapper songMapper;
    private final PlatformSongMappingMapper platformSongMappingMapper;
    private final SongFeatureService songFeatureService;
    private final EmbeddingService embeddingService;
    private final QdrantService qdrantService;
    private final VectorRecallMetrics vectorRecallMetrics;
    private final SongEmbeddingTextBuilder songEmbeddingTextBuilder;

    /** Configurable total timeout (seconds) for concurrent song-feature enrichment in persistSongs. */
    @Value("${song.feature.enrich.timeout-seconds:8}")
    private int enrichTimeoutSeconds;

    /**
     * 持久化歌曲到 DB + 创建 platform_song_mapping（Feature 3 前置）
     */
    public void persistSongs(List<SongVO> songs, String platform) {
        if (songs.isEmpty()) return;

        // Batch lookup: fetch all existing songs in one query instead of N queries
        List<SongVO> needPersist = songs.stream()
                .filter(s -> s.getTitle() != null && s.getArtist() != null)
                .collect(Collectors.toList());
        if (needPersist.isEmpty()) return;

        // Split into two groups: with external (platform, platformSongId) and title/artist-only fallback.
        // The (platform, platformSongId) pair is the authoritative external ID and the unique key on
        // platform_song_mapping — using it first avoids creating duplicate Song rows when the same
        // external track has minor title variations (whitespace, version tags, simplified/traditional).
        List<SongVO> byPlatformId = new ArrayList<>();
        List<SongVO> byTitleArtistOnly = new ArrayList<>();
        for (SongVO vo : needPersist) {
            if (vo.getPlatformSongId() != null) {
                byPlatformId.add(vo);
            } else {
                byTitleArtistOnly.add(vo);
            }
        }

        // Step 1: single query against platform_song_mapping by (platform, platformSongId IN ...)
        Map<String, Long> existingByExternalId = new HashMap<>();
        if (!byPlatformId.isEmpty()) {
            List<String> platformSongIds = byPlatformId.stream()
                    .map(SongVO::getPlatformSongId)
                    .distinct()
                    .collect(Collectors.toList());
            List<PlatformSongMapping> existingMappings = platformSongMappingMapper.selectList(
                    new LambdaQueryWrapper<PlatformSongMapping>()
                            .eq(PlatformSongMapping::getPlatform, platform)
                            .in(PlatformSongMapping::getPlatformSongId, platformSongIds));
            for (PlatformSongMapping m : existingMappings) {
                existingByExternalId.put(m.getPlatformSongId(), m.getSongId());
            }
        }

        // Step 2: walk byPlatformId — mapping hits reuse song id and skip song insert + mapping insert.
        // Misses fall through to the (title, artist) batch path below alongside byTitleArtistOnly.
        Map<Long, SongVO> newSongsById = new HashMap<>();
        List<SongVO> fallbackToTitleArtist = new ArrayList<>(byTitleArtistOnly);

        for (SongVO vo : byPlatformId) {
            Long existingSongId = existingByExternalId.get(vo.getPlatformSongId());
            if (existingSongId != null) {
                try {
                    vo.setId(existingSongId);
                    // Re-index into Qdrant using features (may be null for old songs — skip is acceptable).
                    indexSongForVectorSearch(existingSongId, vo.getFeatures());
                } catch (Exception e) {
                    log.warn("Failed to re-index existing song {}: {} - {}", existingSongId, vo.getTitle(), vo.getArtist(), e);
                }
            } else {
                fallbackToTitleArtist.add(vo);
            }
        }

        // Step 3: original (title, artist) batch path for both fallback groups (true new + no-external-id).
        if (!fallbackToTitleArtist.isEmpty()) {
            var titleArtistPairs = fallbackToTitleArtist.stream()
                    .map(s -> new String[]{s.getTitle(), s.getArtist()})
                    .collect(Collectors.toList());

            LambdaQueryWrapper<Song> songQuery = new LambdaQueryWrapper<>();
            for (int i = 0; i < titleArtistPairs.size(); i++) {
                String[] pair = titleArtistPairs.get(i);
                if (i > 0) songQuery.or();
                songQuery.and(w -> w.eq(Song::getTitle, pair[0]).eq(Song::getArtist, pair[1]));
            }
            List<Song> existingSongs = songMapper.selectList(songQuery);
            Map<String, Song> existingByKey = new HashMap<>();
            for (Song s : existingSongs) {
                existingByKey.put(s.getTitle() + "\0" + s.getArtist(), s);
            }

            // Concurrently enrich only truly-new songs (not already in DB).
            // Use a virtual-thread executor with an 8-second total timeout.
            List<SongVO> trulyNewVos = fallbackToTitleArtist.stream()
                    .filter(vo -> !existingByKey.containsKey(vo.getTitle() + "\0" + vo.getArtist()))
                    .toList();
            Map<String, CompletableFuture<String>> enrichFutures = new HashMap<>();
            try (var enrichExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (SongVO vo : trulyNewVos) {
                    String featureKey = vo.getTitle() + "\0" + vo.getArtist();
                    enrichFutures.put(featureKey, CompletableFuture.supplyAsync(
                            () -> songFeatureService.enrich(vo.getTitle(), vo.getArtist(), vo.getAlbum()),
                            enrichExecutor));
                }
                try {
                    CompletableFuture.allOf(enrichFutures.values().toArray(new CompletableFuture[0]))
                            .get(enrichTimeoutSeconds, TimeUnit.SECONDS);
                } catch (Exception timeout) {
                    long notDone = enrichFutures.values().stream().filter(f -> !f.isDone()).count();
                    log.warn("Song feature enrichment timed out after {}s; {} song(s) fell back to local fallback",
                            enrichTimeoutSeconds, notDone);
                    // Interrupt straggler virtual threads so close() returns promptly
                    enrichExecutor.shutdownNow();
                }
            }

            for (SongVO vo : fallbackToTitleArtist) {
                try {
                    String key = vo.getTitle() + "\0" + vo.getArtist();
                    Song existing = existingByKey.get(key);

                    Long songId;
                    String newFeaturesJson = null; // populated only for truly-new songs
                    if (existing != null) {
                        songId = existing.getId();
                    } else {
                        Song song = new Song();
                        song.setTitle(vo.getTitle());
                        song.setArtist(vo.getArtist());
                        song.setAlbum(vo.getAlbum());
                        song.setDurationSeconds(vo.getDurationSeconds());
                        song.setCoverUrl(vo.getCoverUrl());
                        // Set features: use enrich result if done, else local non-blocking fallback
                        CompletableFuture<String> fut = enrichFutures.get(key);
                        if (fut != null && fut.isDone() && !fut.isCompletedExceptionally()) {
                            newFeaturesJson = fut.get();
                        } else {
                            // Future is null, still running, or completed exceptionally —
                            // use non-blocking local fallback (no LLM call, no metric increment)
                            newFeaturesJson = songFeatureService.fallbackFeatures(
                                    vo.getTitle(), vo.getArtist(), vo.getAlbum());
                        }
                        song.setFeatures(newFeaturesJson);
                        songMapper.insert(song);
                        songId = song.getId();
                        newSongsById.put(songId, vo);
                    }
                    // Replace Netease platform ID with the DB auto-increment ID so that
                    // all downstream references (feedback, liked, history) use the correct key.
                    vo.setId(songId);

                    // Auto-index into Qdrant using features aligned with query-side embeddings.
                    // existing-by-title branch: use DB features (may be null → skip accepted).
                    // new-song branch: use freshly-computed featuresJson.
                    String featuresToIndex = (existing != null) ? existing.getFeatures() : newFeaturesJson;
                    indexSongForVectorSearch(songId, featuresToIndex);
                } catch (Exception e) {
                    log.warn("Failed to persist song: {} - {}", vo.getTitle(), vo.getArtist(), e);
                }
            }
        }

        // Batch create platform mappings: single query to find existing, then insert missing.
        // newSongsById only contains true-new songs whose (platform, platformSongId) was confirmed
        // absent in Step 1, so the unique-key collision can no longer occur.
        if (!newSongsById.isEmpty()) {
            List<Long> newSongIds = new ArrayList<>(newSongsById.keySet());
            List<PlatformSongMapping> existingMappings = platformSongMappingMapper.selectList(
                    new LambdaQueryWrapper<PlatformSongMapping>()
                            .in(PlatformSongMapping::getSongId, newSongIds));
            Map<Long, List<PlatformSongMapping>> mappingsBySongId = existingMappings.stream()
                    .collect(Collectors.groupingBy(PlatformSongMapping::getSongId));

            for (Map.Entry<Long, SongVO> entry : newSongsById.entrySet()) {
                try {
                    Long songId = entry.getKey();
                    SongVO vo = entry.getValue();
                    if (vo.getPlatformSongId() == null) continue;
                    List<PlatformSongMapping> existing = mappingsBySongId.getOrDefault(songId, List.of());
                    boolean hasMapping = existing.stream()
                            .anyMatch(m -> platform.equals(m.getPlatform())
                                    && vo.getPlatformSongId().equals(m.getPlatformSongId()));
                    if (!hasMapping) {
                        PlatformSongMapping mapping = new PlatformSongMapping();
                        mapping.setSongId(songId);
                        mapping.setPlatform(platform);
                        mapping.setPlatformSongId(vo.getPlatformSongId());
                        platformSongMappingMapper.insert(mapping);
                    }
                } catch (Exception e) {
                    log.warn("Failed to create platform mapping for song {}: {}", entry.getKey(), e.getMessage());
                }
            }
        }
    }

    /**
     * Index a song into Qdrant for vector-based recall.
     * Generates an embedding from the song's emotion/features description text
     * (structurally aligned with the query side) and upserts it.
     * <p>
     * If {@code featuresJson} is null/blank/unparseable (i.e. {@link com.moodfm.service.player.impl.recall.SongEmbeddingTextBuilder#buildSongEmbeddingText}
     * returns empty), indexing is skipped — we never fall back to song-name text.
     * Wrapped in try-catch — failure here does not affect song persistence.
     *
     * @param songId      the DB song ID
     * @param featuresJson the Task 2 features JSON (genre/mood_tags/energy/language/…)
     */
    private void indexSongForVectorSearch(Long songId, String featuresJson) {
        try {
            String text = songEmbeddingTextBuilder.buildSongEmbeddingText(featuresJson);
            if (text.isEmpty()) return; // no features → skip, do NOT index song-name text

            float[] embedding = embeddingService.embed(text);

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("songId", songId);

            qdrantService.upsertSong(songId, embedding, metadata);
        } catch (Exception e) {
            log.warn("Vector indexing failed for song {}: {}", songId, e.getMessage());
            vectorRecallMetrics.indexFailure();
        }
    }

    /**
     * Batch-convert Song entities to SongVOs with a single mapping query.
     */
    public List<SongVO> songsToVOs(List<Song> songs) {
        if (songs.isEmpty()) return List.of();
        List<Long> songIds = songs.stream().map(Song::getId).collect(Collectors.toList());
        List<PlatformSongMapping> allMappings = platformSongMappingMapper.selectList(
                new LambdaQueryWrapper<PlatformSongMapping>()
                        .in(PlatformSongMapping::getSongId, songIds));
        Map<Long, List<PlatformSongMapping>> mappingMap = allMappings.stream()
                .collect(Collectors.groupingBy(PlatformSongMapping::getSongId));

        List<SongVO> result = new ArrayList<>(songs.size());
        for (Song song : songs) {
            List<PlatformSongMapping> mappings = mappingMap.getOrDefault(song.getId(), List.of());
            PlatformSongMapping mapping = mappings.isEmpty() ? null : mappings.get(0);
            result.add(SongVO.builder()
                    .id(song.getId())
                    .title(song.getTitle())
                    .artist(song.getArtist())
                    .album(song.getAlbum())
                    .durationSeconds(song.getDurationSeconds())
                    .coverUrl(song.getCoverUrl())
                    .platform(mapping != null ? mapping.getPlatform() : null)
                    .platformSongId(mapping != null ? mapping.getPlatformSongId() : null)
                    .features(song.getFeatures())
                    .build());
        }
        return result;
    }

    /**
     * Convert a single Song entity to SongVO (delegates to batch method for mapping lookup).
     */
    private SongVO songToVO(Song song) {
        return songsToVOs(List.of(song)).get(0);
    }
}
