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
 * Recall path 1: user liked songs. @Order(10) → highest dedup priority.
 */
@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class LikedRecallSource implements RecallSource {

    private final MusicApiClient musicApiClient;

    @Override
    public double weight() { return 1.0; }

    @Override
    public String sourceName() { return "liked"; }

    @Override
    public List<SongVO> recall(RecallContext ctx) {
        try {
            var data = musicApiClient.getUserLikedSongs(ctx.platform(), ctx.cookie());
            if (MusicApiClient.isUnsupported(data)) {
                return List.of();
            }
            return MusicResponseParser.parseSongs(data, ctx.platform());
        } catch (Exception e) {
            log.warn("fetchLiked failed", e);
            return List.of();
        }
    }
}
