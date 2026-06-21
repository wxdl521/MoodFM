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
 * Recall path 5: explore/discovery keyword search (15 results). @Order(50).
 */
@Slf4j
@Component
@Order(50)
@RequiredArgsConstructor
public class ExploreSearchRecallSource implements RecallSource {

    private final MusicApiClient musicApiClient;

    @Override
    public double weight() { return 0.4; }

    @Override
    public String sourceName() { return "explore-search"; }

    @Override
    public List<SongVO> recall(RecallContext ctx) {
        try {
            return MusicResponseParser.parseSongs(
                    musicApiClient.searchSongs(ctx.platform(), ctx.exploreKw(), 15, null), ctx.platform());
        } catch (Exception e) {
            log.warn("fetchSearch failed: {}", ctx.exploreKw(), e);
            return List.of();
        }
    }
}
