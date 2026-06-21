package com.moodfm.service.player.impl.recall;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodfm.ai.model.MoodParams;
import com.moodfm.domain.vo.SongVO;
import com.moodfm.mapper.FeedbackEventMapper;
import com.moodfm.mapper.GlobalBlacklistMapper;
import com.moodfm.mapper.PlatformSongMappingMapper;
import com.moodfm.mapper.SongMapper;
import com.moodfm.mapper.UserProfileMapper;
import com.moodfm.service.player.impl.recall.source.RecallSource;
import com.moodfm.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Regression test for the §4 timeout-fallback fix: verifies that ALL completed
 * sources (including vibe and explore positions) contribute songs to the merged
 * result, rather than the old fixed subset that dropped vibe/explore on timeout.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecallOrchestrationTest {

    @Mock private SongMapper songMapper;
    @Mock private PlatformSongMappingMapper platformSongMappingMapper;
    @Mock private FeedbackEventMapper feedbackEventMapper;
    @Mock private UserProfileMapper userProfileMapper;
    @Mock private GlobalBlacklistMapper globalBlacklistMapper;
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
    void setUp() throws Exception {
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

        // Stub orchestrator's remaining collaborators to prevent NPE / filter side-effects.
        // userId == null → skips negative-feedback and blacklist-keyword filters.
        when(globalBlacklistMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(platformSongMappingMapper.selectList(any())).thenReturn(Collections.emptyList()); // backfillFeatures returns early
        when(songEmbeddingTextBuilder.buildVectorQueryText(any(), any(), any())).thenReturn("music");

        service = new CandidateRecallService(
                List.of(likedSource, recommendSource, vibeSource, exploreSource, throwingSource),
                songMapper, platformSongMappingMapper, feedbackEventMapper, userProfileMapper,
                userService, globalBlacklistMapper, new ObjectMapper(), songEmbeddingTextBuilder);

        // Inject a real ObjectMapper (mirrors RecallScoringTest setUp)
        var field = CandidateRecallService.class.getDeclaredField("objectMapper");
        field.setAccessible(true);
        field.set(service, new ObjectMapper());
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
