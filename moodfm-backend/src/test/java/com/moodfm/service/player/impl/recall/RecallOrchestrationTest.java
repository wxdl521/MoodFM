package com.moodfm.service.player.impl.recall;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodfm.ai.model.MoodParams;
import com.moodfm.domain.vo.SongVO;
import com.moodfm.mapper.PlatformSongMappingMapper;
import com.moodfm.mapper.SongMapper;
import com.moodfm.service.player.impl.recall.filter.CandidateFilter;
import com.moodfm.service.player.impl.recall.source.RecallSource;
import com.moodfm.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Orchestration tests for {@link CandidateRecallService}:
 * <ul>
 *   <li>§4 timeout-fallback fix: ALL completed sources (including the vibe and
 *       explore positions) contribute songs to the merged result, rather than the
 *       old fixed subset that dropped vibe/explore on timeout.</li>
 *   <li>T3-2 Task 2: the {@code List<CandidateFilter>} pipeline is applied in
 *       {@code @Order} sequence (negative→keyword→global) and the result is
 *       capped at 60.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecallOrchestrationTest {

    @Mock private SongMapper songMapper;
    @Mock private PlatformSongMappingMapper platformSongMappingMapper;
    @Mock private UserService userService;
    @Mock private SongEmbeddingTextBuilder songEmbeddingTextBuilder;

    // Mock recall sources — we construct the orchestrator directly with these
    @Mock private RecallSource likedSource;
    @Mock private RecallSource recommendSource;
    @Mock private RecallSource vibeSource;      // the "vibe" position — old code dropped this on timeout
    @Mock private RecallSource exploreSource;   // the "explore" position — old code dropped this on timeout
    @Mock private RecallSource throwingSource;  // throws inside recall() → should contribute nothing

    private CandidateRecallService service;

    @BeforeEach
    void setUp() {
        // Sources: liked, recommend, vibe, explore all return distinct songs.
        // throwingSource throws RuntimeException from inside recall().
        SongVO likedSong   = SongVO.builder().platformSongId("liked-1")  .title("LikedSong")  .artist("A").build();
        SongVO recommendSong = SongVO.builder().platformSongId("rec-1")  .title("RecSong")    .artist("B").build();
        SongVO vibeSong    = SongVO.builder().platformSongId("vibe-1")   .title("VibeSong")   .artist("C").build();
        SongVO exploreSong = SongVO.builder().platformSongId("explore-1").title("ExploreSong").artist("D").build();

        when(likedSource.weight()).thenReturn(1.0);
        when(likedSource.sourceName()).thenReturn("liked");
        when(likedSource.recall(any())).thenReturn(List.of(likedSong));

        when(recommendSource.weight()).thenReturn(0.8);
        when(recommendSource.sourceName()).thenReturn("recommend");
        when(recommendSource.recall(any())).thenReturn(List.of(recommendSong));

        when(vibeSource.weight()).thenReturn(0.6);
        when(vibeSource.sourceName()).thenReturn("vibe-search");
        when(vibeSource.recall(any())).thenReturn(List.of(vibeSong));

        when(exploreSource.weight()).thenReturn(0.4);
        when(exploreSource.sourceName()).thenReturn("explore-search");
        when(exploreSource.recall(any())).thenReturn(List.of(exploreSong));

        when(throwingSource.weight()).thenReturn(0.9);
        when(throwingSource.sourceName()).thenReturn("broken-source");
        // throws inside recall() — the source bean's internal catch returns List.of()
        when(throwingSource.recall(any())).thenThrow(new RuntimeException("boom"));

        // Stub orchestrator's remaining collaborators to prevent NPE.
        // userId == null → user-level filters short-circuit; empty filter list → no filtering.
        when(platformSongMappingMapper.selectList(any())).thenReturn(Collections.emptyList()); // backfillFeatures returns early
        when(songEmbeddingTextBuilder.buildVectorQueryText(any(), any(), any())).thenReturn("music");

        service = new CandidateRecallService(
                List.of(likedSource, recommendSource, vibeSource, exploreSource, throwingSource),
                List.of(),
                songMapper, platformSongMappingMapper, userService, new ObjectMapper(), songEmbeddingTextBuilder);
    }

    /**
     * §4 regression: vibe-search and explore-search songs MUST appear in results.
     * The old code's timeout-catch branch merged only liked/recommend/genre/vector —
     * even when vibe and explore futures had already completed normally. The new code
     * iterates ALL futures and merges any that are done-and-not-exceptionally.
     */
    @Test
    void recallSongs_allCompletedSources_includingVibeAndExplore_areMerged() {
        MoodParams mood = buildMood(0.5, 0.5);

        List<SongVO> result = service.recallSongs("netease", "cookie", mood, null);

        // Each completed source contributed 1 unique song → 4 total (throwingSource = 0)
        assertFalse(result.isEmpty(), "result must not be empty");

        List<String> ids = result.stream().map(SongVO::getPlatformSongId).toList();

        // Core §4 regression assertions: vibe and explore ARE present
        assertTrue(ids.contains("vibe-1"),
                "vibe-search source must contribute songs (§4 fix: was dropped in old timeout fallback)");
        assertTrue(ids.contains("explore-1"),
                "explore-search source must contribute songs (§4 fix: was dropped in old timeout fallback)");

        // The throwing source contributed nothing
        assertFalse(ids.contains(null), "no null platformSongId should appear");

        // liked and recommend are also present
        assertTrue(ids.contains("liked-1"),   "liked source must contribute songs");
        assertTrue(ids.contains("rec-1"),     "recommend source must contribute songs");

        // Total result ≤ 60
        assertTrue(result.size() <= 60, "result must not exceed 60 songs");
    }

    @Test
    void recallSongs_throwingSource_contributesNothing() {
        MoodParams mood = buildMood(0.5, 0.5);

        List<SongVO> result = service.recallSongs("netease", "cookie", mood, null);

        // throwingSource.recall() throws, so its internal catch is bypassed here —
        // the throw propagates to the CompletableFuture which becomes exceptionally-completed.
        // The §4 merge loop skips it (isCompletedExceptionally() == true).
        // Verify: only the 4 good sources' songs appear (4 unique IDs).
        long uniqueCount = result.stream().map(SongVO::getPlatformSongId).distinct().count();
        assertEquals(4, uniqueCount,
                "exactly 4 sources contributed (throwingSource exception → skipped by §4 merge)");
    }

    @Test
    void recallSongs_resultSizeWithinLimit() {
        MoodParams mood = buildMood(0.5, 0.5);

        List<SongVO> result = service.recallSongs("netease", "cookie", mood, null);

        assertTrue(result.size() <= 60, "result must be at most 60 songs");
        assertFalse(result.isEmpty());
    }

    /**
     * T3-2 Task 2: the injected {@code List<CandidateFilter>} is applied as an ordered
     * pipeline — each stage in turn, in list (i.e. {@code @Order}) sequence — and the
     * final result is limited to 60. Recording filters assert real call order, not just
     * "no throw". A single source returns 70 songs so the limit(60) cut is observable.
     */
    @Test
    void recallSongs_filterChain_appliedInOrder_andResultLimitedTo60() {
        @SuppressWarnings("unchecked")
        RecallSource bigSource = mock(RecallSource.class);
        List<SongVO> many = new ArrayList<>();
        for (int i = 0; i < 70; i++) {
            many.add(SongVO.builder().platformSongId("s-" + i).title("T" + i).artist("A").build());
        }
        when(bigSource.weight()).thenReturn(1.0);
        when(bigSource.sourceName()).thenReturn("liked");
        when(bigSource.recall(any())).thenReturn(many);

        // 3 recording filters in @Order sequence; each returns the list unchanged so the
        // chain reaches limit(60). Names mirror negative(10)→keyword(20)→global(30).
        CandidateFilter negative = mock(CandidateFilter.class);
        CandidateFilter keyword  = mock(CandidateFilter.class);
        CandidateFilter global   = mock(CandidateFilter.class);
        when(negative.filter(any(), any())).thenAnswer(inv -> inv.getArgument(1));
        when(keyword.filter(any(), any())).thenAnswer(inv -> inv.getArgument(1));
        when(global.filter(any(), any())).thenAnswer(inv -> inv.getArgument(1));

        CandidateRecallService svc = new CandidateRecallService(
                List.of(bigSource),
                List.of(negative, keyword, global),
                songMapper, platformSongMappingMapper, userService, new ObjectMapper(), songEmbeddingTextBuilder);

        List<SongVO> result = svc.recallSongs("netease", "cookie", buildMood(0.5, 0.5), 42L);

        assertEquals(60, result.size(), "result must be limited to 60");

        InOrder inOrder = inOrder(negative, keyword, global);
        inOrder.verify(negative).filter(eq(42L), any());
        inOrder.verify(keyword).filter(eq(42L), any());
        inOrder.verify(global).filter(eq(42L), any());
    }

    // -----------------------------------------------------------------------
    // helper
    // -----------------------------------------------------------------------

    private MoodParams buildMood(double valence, double energy) {
        MoodParams mood = new MoodParams();
        MoodParams.MoodVector mv = new MoodParams.MoodVector();
        mv.setValence(valence);
        mv.setEnergy(energy);
        mood.setMood(mv);
        return mood;
    }
}
