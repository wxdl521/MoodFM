package com.moodfm.service.player.impl.recall.source;

import com.moodfm.domain.entity.Song;
import com.moodfm.domain.vo.SongVO;
import com.moodfm.mapper.SongMapper;
import com.moodfm.service.embedding.EmbeddingService;
import com.moodfm.service.player.impl.catalog.SongCatalogService;
import com.moodfm.service.player.impl.recall.RecallContext;
import com.moodfm.service.vector.QdrantService;
import com.moodfm.service.vector.VectorRecallMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Recall path 6: vector similarity search via Qdrant. @Order(60) — lowest dedup priority.
 * Gracefully returns empty list if Qdrant is unavailable.
 */
@Slf4j
@Component
@Order(60)
@RequiredArgsConstructor
public class VectorRecallSource implements RecallSource {

    private final EmbeddingService embeddingService;
    private final QdrantService qdrantService;
    private final VectorRecallMetrics vectorRecallMetrics;
    private final SongMapper songMapper;
    private final SongCatalogService songCatalogService;

    @Override
    public double weight() { return 0.9; }

    @Override
    public String sourceName() { return "vector-search"; }

    @Override
    public List<SongVO> recall(RecallContext ctx) {
        try {
            float[] embedding = embeddingService.embed(ctx.vectorQueryText());
            List<Long> songIds = qdrantService.searchSimilar(embedding, 20);
            if (songIds.isEmpty()) return List.of();

            // Batch query songs from DB
            List<Song> songs = songMapper.selectBatchIds(songIds);
            if (songs.isEmpty()) return List.of();

            // Preserve similarity order
            Map<Long, Song> songMap = new LinkedHashMap<>();
            for (Long id : songIds) {
                songs.stream().filter(s -> s.getId().equals(id)).findFirst().ifPresent(s -> songMap.put(id, s));
            }

            // Batch convert with single mapping query (no N+1)
            return songCatalogService.songsToVOs(new ArrayList<>(songMap.values()));
        } catch (Exception e) {
            log.warn("Vector recall failed: {}", e.getMessage());
            vectorRecallMetrics.recallFailure();
            return List.of();
        }
    }
}
