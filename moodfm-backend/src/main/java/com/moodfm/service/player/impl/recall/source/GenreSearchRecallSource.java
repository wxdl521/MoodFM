package com.moodfm.service.player.impl.recall.source;

import com.moodfm.client.music.MusicApiClient;
import com.moodfm.common.util.MusicResponseParser;
import com.moodfm.domain.vo.SongVO;
import com.moodfm.service.player.impl.recall.RecallContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Recall path 3: genre keyword search (25 results). @Order(30).
 */
@Slf4j
@Component
@Order(30)
@RequiredArgsConstructor
public class GenreSearchRecallSource implements RecallSource {

    private final MusicApiClient musicApiClient;

    @Override
    public double weight() { return 0.6; }

    @Override
    public String sourceName() { return "genre-search"; }

    @Override
    public List<SongVO> recall(RecallContext ctx) {
        try {
            return MusicResponseParser.parseSongs(
                    musicApiClient.searchSongs(ctx.platform(), ctx.genreKw(), 25, null), ctx.platform());
        } catch (Exception e) {
            log.warn("fetchSearch failed: {}", ctx.genreKw(), e);
            return List.of();
        }
    }
}
