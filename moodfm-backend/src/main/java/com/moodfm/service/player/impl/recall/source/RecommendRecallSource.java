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
 * Recall path 2: platform recommended songs. @Order(20).
 */
@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class RecommendRecallSource implements RecallSource {

    private final MusicApiClient musicApiClient;

    @Override
    public double weight() { return 0.8; }

    @Override
    public String sourceName() { return "recommend"; }

    @Override
    public List<SongVO> recall(RecallContext ctx) {
        try {
            return MusicResponseParser.parseSongs(
                    musicApiClient.getRecommendSongs(ctx.platform(), ctx.cookie()), ctx.platform());
        } catch (Exception e) {
            log.warn("fetchRecommend failed", e);
            return List.of();
        }
    }
}
