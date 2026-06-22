package com.moodfm.service.player.impl.recall.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodfm.domain.entity.FeedbackEvent;
import com.moodfm.domain.entity.PlatformSongMapping;
import com.moodfm.domain.vo.SongVO;
import com.moodfm.mapper.FeedbackEventMapper;
import com.moodfm.mapper.PlatformSongMappingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NegativeFeedbackFilterTest {

    @Mock private FeedbackEventMapper feedbackEventMapper;
    @Mock private PlatformSongMappingMapper platformSongMappingMapper;

    private NegativeFeedbackFilter filter;

    @BeforeEach
    void setUp() {
        filter = new NegativeFeedbackFilter(feedbackEventMapper, platformSongMappingMapper, new ObjectMapper());
    }

    @Test
    void userIdNull_shortCircuits_returnsCandidatesUnchanged_noMapperCalls() {
        List<SongVO> candidates = List.of(
                SongVO.builder().platformSongId("p1").title("A").artist("X").build(),
                SongVO.builder().platformSongId("p2").title("B").artist("Y").build());

        List<SongVO> result = filter.filter(null, candidates);

        assertSame(candidates, result, "null userId must return the same list unchanged");
        verifyNoInteractions(feedbackEventMapper, platformSongMappingMapper);
    }

    @Test
    void songBelowThreshold_isRemoved() {
        // Two skip events with playedSeconds<30 → -6 each → songId 100 scores -12 (< -10)
        when(feedbackEventMapper.selectList(any())).thenReturn(List.of(skip(100L), skip(100L)));
        // songId 100 maps to platformSongId "p100"
        when(platformSongMappingMapper.selectList(any())).thenReturn(List.of(mapping(100L, "p100")));

        List<SongVO> candidates = List.of(
                SongVO.builder().platformSongId("p100").title("Hated").artist("X").build(),
                SongVO.builder().platformSongId("p200").title("Liked").artist("Y").build());

        List<SongVO> result = filter.filter(7L, candidates);

        List<String> ids = result.stream().map(SongVO::getPlatformSongId).toList();
        assertFalse(ids.contains("p100"), "song scoring below -10 must be removed");
        assertTrue(ids.contains("p200"), "non-negative song must remain");
        assertEquals(1, result.size());
    }

    @Test
    void emptyEvents_returnsCandidatesUnchanged() {
        when(feedbackEventMapper.selectList(any())).thenReturn(List.of());

        List<SongVO> candidates = List.of(
                SongVO.builder().platformSongId("p1").title("A").artist("X").build());

        List<SongVO> result = filter.filter(7L, candidates);

        assertSame(candidates, result, "no feedback events → list returned unchanged");
        verifyNoInteractions(platformSongMappingMapper);
    }

    private FeedbackEvent skip(Long songId) {
        FeedbackEvent e = new FeedbackEvent();
        e.setSongId(songId);
        e.setEventType("skip");
        e.setEventData("{\"playedSeconds\":5}");
        return e;
    }

    private PlatformSongMapping mapping(Long songId, String platformSongId) {
        PlatformSongMapping m = new PlatformSongMapping();
        m.setSongId(songId);
        m.setPlatformSongId(platformSongId);
        return m;
    }
}
