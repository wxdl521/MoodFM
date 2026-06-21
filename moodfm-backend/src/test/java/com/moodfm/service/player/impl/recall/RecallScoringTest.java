package com.moodfm.service.player.impl.recall;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodfm.ai.model.MoodParams;
import com.moodfm.domain.vo.SongVO;
import com.moodfm.mapper.FeedbackEventMapper;
import com.moodfm.mapper.GlobalBlacklistMapper;
import com.moodfm.mapper.PlatformSongMappingMapper;
import com.moodfm.mapper.SongMapper;
import com.moodfm.mapper.UserProfileMapper;
import com.moodfm.service.embedding.EmbeddingService;
import com.moodfm.service.player.impl.catalog.SongCatalogService;
import com.moodfm.service.user.UserService;
import com.moodfm.service.vector.QdrantService;
import com.moodfm.service.vector.VectorRecallMetrics;
import com.moodfm.client.music.MusicApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the emotionMatchScore / hitsAvoid / source-weighted scoring
 * logic added in Task 3 (T1-1 + T1-2).
 *
 * All methods under test are package-visible inside CandidateRecallService so this
 * test class sits in the same package.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecallScoringTest {

    // All collaborators mocked so @InjectMocks can build CandidateRecallService
    @Mock private MusicApiClient musicApiClient;
    @Mock private SongMapper songMapper;
    @Mock private PlatformSongMappingMapper platformSongMappingMapper;
    @Mock private FeedbackEventMapper feedbackEventMapper;
    @Mock private UserProfileMapper userProfileMapper;
    @Mock private UserService userService;
    @Mock private GlobalBlacklistMapper globalBlacklistMapper;
    @Mock private EmbeddingService embeddingService;
    @Mock private QdrantService qdrantService;
    @Mock private VectorRecallMetrics vectorRecallMetrics;
    @Mock private SongEmbeddingTextBuilder songEmbeddingTextBuilder;
    @Mock private SongCatalogService songCatalogService;

    @InjectMocks
    private CandidateRecallService service;

    @BeforeEach
    void setUp() throws Exception {
        // Inject a real ObjectMapper so JSON parsing inside the scoring methods works
        var field = CandidateRecallService.class.getDeclaredField("objectMapper");
        field.setAccessible(true);
        field.set(service, new ObjectMapper());
    }

    // -----------------------------------------------------------------------
    // emotionMatchScore — null / invalid JSON → neutral baseline
    // -----------------------------------------------------------------------

    @Test
    void emotionMatchScore_nullFeatures_returnsNeutral() {
        MoodParams mood = buildMood(0.2, 0.3, null, null);
        double score = service.emotionMatchScore(null, mood);
        assertEquals(CandidateRecallService.UNKNOWN_EMOTION, score, 1e-9,
                "null features should return UNKNOWN_EMOTION=0.5");
    }

    @Test
    void emotionMatchScore_blankFeatures_returnsNeutral() {
        MoodParams mood = buildMood(0.5, 0.5, null, null);
        double score = service.emotionMatchScore("", mood);
        assertEquals(CandidateRecallService.UNKNOWN_EMOTION, score, 1e-9,
                "blank features should return UNKNOWN_EMOTION=0.5");
    }

    @Test
    void emotionMatchScore_invalidJson_returnsNeutral() {
        MoodParams mood = buildMood(0.5, 0.5, null, null);
        double score = service.emotionMatchScore("not-json!!!", mood);
        assertEquals(CandidateRecallService.UNKNOWN_EMOTION, score, 1e-9,
                "invalid JSON should return UNKNOWN_EMOTION=0.5");
    }

    @Test
    void emotionMatchScore_missingMoodVector_returnsNeutral() {
        // mood.getMood() == null → can't compute distance
        MoodParams mood = new MoodParams();
        double score = service.emotionMatchScore("{\"valence\":0.5,\"energy\":0.5}", mood);
        assertEquals(CandidateRecallService.UNKNOWN_EMOTION, score, 1e-9,
                "null MoodVector should return UNKNOWN_EMOTION=0.5");
    }

    // -----------------------------------------------------------------------
    // emotionMatchScore — distance math
    // -----------------------------------------------------------------------

    @Test
    void emotionMatchScore_perfectMatch_returnsCloseToOne() {
        // Song valence=0.8, energy=0.3 exactly matches mood
        MoodParams mood = buildMood(0.8, 0.3, null, null);
        String features = "{\"valence\":0.8,\"energy\":0.3}";
        double score = service.emotionMatchScore(features, mood);
        // dist=0 → emotion = 1.0; no genre/language bonus
        assertEquals(1.0, score, 1e-6, "perfect match should score ~1.0");
    }

    @Test
    void emotionMatchScore_worstMatch_returnsNearZero() {
        // Low valence/energy mood vs high valence/energy song → largest possible distance
        MoodParams mood = buildMood(0.0, 0.0, null, null);
        String features = "{\"valence\":1.0,\"energy\":1.0}";
        double score = service.emotionMatchScore(features, mood);
        // dist = sqrt(2) → emotion = 1 - 1 = 0.0
        assertEquals(0.0, score, 1e-6, "worst match should score ~0.0");
    }

    @Test
    void emotionMatchScore_lowMoodHighSong_isLowerThan_lowMoodLowSong() {
        MoodParams mood = buildMood(0.2, 0.2, null, null);

        String highSong = "{\"valence\":0.9,\"energy\":0.9}";
        String lowSong  = "{\"valence\":0.2,\"energy\":0.2}";

        double scoreHigh = service.emotionMatchScore(highSong, mood);
        double scoreLow  = service.emotionMatchScore(lowSong,  mood);

        assertTrue(scoreLow > scoreHigh,
                "low-v/e song should score higher than high-v/e song against low-v/e mood; "
                + "scoreLow=" + scoreLow + ", scoreHigh=" + scoreHigh);
    }

    // -----------------------------------------------------------------------
    // emotionMatchScore — genre / language bonus
    // -----------------------------------------------------------------------

    @Test
    void emotionMatchScore_genreHit_addsBonus() {
        MoodParams mood = buildMood(0.5, 0.5, List.of("indie", "pop"), null);
        String withGenre    = "{\"valence\":0.5,\"energy\":0.5,\"genre\":\"pop\"}";
        String withoutGenre = "{\"valence\":0.5,\"energy\":0.5,\"genre\":\"metal\"}";

        double hit  = service.emotionMatchScore(withGenre,    mood);
        double miss = service.emotionMatchScore(withoutGenre, mood);

        // GENRE_BONUS = 0.3
        assertEquals(0.3, hit - miss, 1e-9,
                "genre hit should add exactly GENRE_BONUS=0.3");
    }

    @Test
    void emotionMatchScore_languageHit_addsBonus() {
        MoodParams mood = buildMood(0.5, 0.5, null, List.of("zh", "en"));
        String withLang    = "{\"valence\":0.5,\"energy\":0.5,\"language\":\"zh\"}";
        String withoutLang = "{\"valence\":0.5,\"energy\":0.5,\"language\":\"fr\"}";

        double hit  = service.emotionMatchScore(withLang,    mood);
        double miss = service.emotionMatchScore(withoutLang, mood);

        // LANGUAGE_BONUS = 0.2
        assertEquals(0.2, hit - miss, 1e-9,
                "language hit should add exactly LANGUAGE_BONUS=0.2");
    }

    @Test
    void emotionMatchScore_bothHit_addsBothBonuses() {
        MoodParams mood = buildMood(0.5, 0.5, List.of("pop"), List.of("en"));
        String features = "{\"valence\":0.5,\"energy\":0.5,\"genre\":\"pop\",\"language\":\"en\"}";
        // dist=0 → emotion=1.0; + 0.3 + 0.2 = 1.5
        double score = service.emotionMatchScore(features, mood);
        assertEquals(1.5, score, 1e-6, "both bonuses should stack to 1.5 on perfect match");
    }

    @Test
    void emotionMatchScore_nullPreferredGenres_noNpe() {
        // getPreferredGenres() may be null — must not throw
        MoodParams mood = buildMood(0.5, 0.5, null, null);
        assertDoesNotThrow(() -> service.emotionMatchScore(
                "{\"valence\":0.5,\"energy\":0.5,\"genre\":\"pop\"}", mood));
    }

    // -----------------------------------------------------------------------
    // hitsAvoid — title / artist / mood_tags matching
    // -----------------------------------------------------------------------

    @Test
    void hitsAvoid_titleMatch_returnsTrue() {
        MoodParams mood = buildMood(0.5, 0.5, null, null);
        mood.setAvoidKeywords(List.of("sad", "death"));
        SongVO song = SongVO.builder().title("Death March").artist("Unknown").build();
        assertTrue(service.hitsAvoid(song, mood));
    }

    @Test
    void hitsAvoid_artistMatch_returnsTrue() {
        MoodParams mood = buildMood(0.5, 0.5, null, null);
        mood.setAvoidKeywords(List.of("war"));
        SongVO song = SongVO.builder().title("Good Song").artist("Warzone").build();
        assertTrue(service.hitsAvoid(song, mood));
    }

    @Test
    void hitsAvoid_moodTagsMatch_returnsTrue() {
        MoodParams mood = buildMood(0.5, 0.5, null, null);
        mood.setAvoidKeywords(List.of("violent"));
        SongVO song = SongVO.builder()
                .title("Good Song").artist("Artist")
                .features("{\"mood_tags\":[\"aggressive\",\"violent\",\"dark\"]}")
                .build();
        assertTrue(service.hitsAvoid(song, mood));
    }

    @Test
    void hitsAvoid_noMatch_returnsFalse() {
        MoodParams mood = buildMood(0.5, 0.5, null, null);
        mood.setAvoidKeywords(List.of("sad"));
        SongVO song = SongVO.builder().title("Happy Dance").artist("JoyBand").build();
        assertFalse(service.hitsAvoid(song, mood));
    }

    @Test
    void hitsAvoid_nullAvoidKeywords_returnsFalse() {
        MoodParams mood = buildMood(0.5, 0.5, null, null);
        mood.setAvoidKeywords(null);
        SongVO song = SongVO.builder().title("Sad Song").artist("Artist").build();
        assertFalse(service.hitsAvoid(song, mood));
    }

    @Test
    void hitsAvoid_caseInsensitive() {
        MoodParams mood = buildMood(0.5, 0.5, null, null);
        mood.setAvoidKeywords(List.of("WAR"));
        SongVO song = SongVO.builder().title("Warfare").artist("Nobody").build();
        assertTrue(service.hitsAvoid(song, mood), "matching should be case-insensitive");
    }

    // -----------------------------------------------------------------------
    // Source weighting: liked > explore when emotion score is equal
    // -----------------------------------------------------------------------

    @Test
    void sourceWeight_likedRanksAboveExplore_withNeutralEmotion() {
        // Build two candidates with identical neutral features (emotion score equal)
        String neutralFeatures = "{\"valence\":0.5,\"energy\":0.5}";

        SongVO liked   = SongVO.builder()
                .platformSongId("liked-1").title("Liked Song")  .artist("A").features(neutralFeatures).build();
        SongVO explore = SongVO.builder()
                .platformSongId("expl-1") .title("Explore Song").artist("B").features(neutralFeatures).build();

        LinkedHashMap<String, SongVO> dedup = new LinkedHashMap<>();
        dedup.put("liked-1", liked);
        dedup.put("expl-1",  explore);

        Map<String, Double> sourceWeight = new LinkedHashMap<>();
        // W_LIKED=1.0, W_EXPLORE=0.4
        sourceWeight.put("liked-1", 1.0);
        sourceWeight.put("expl-1",  0.4);

        // Mood with same valence/energy as songs → equal emotion scores
        MoodParams mood = buildMood(0.5, 0.5, null, null);

        List<SongVO> ranked = service.scoreAndSort(dedup, sourceWeight, mood);

        assertEquals(2, ranked.size());
        assertEquals("liked-1", ranked.get(0).getPlatformSongId(),
                "Liked song (W=1.0) should rank above explore (W=0.4) with equal emotion score");
    }

    @Test
    void sourceWeight_multiSourceTakesMax() {
        // A song present in both liked(1.0) and explore(0.4) → max=1.0 should beat recommend=0.8
        String neutralFeatures = "{\"valence\":0.5,\"energy\":0.5}";

        SongVO both   = SongVO.builder()
                .platformSongId("both-1").title("Double").artist("A").features(neutralFeatures).build();
        SongVO single = SongVO.builder()
                .platformSongId("solo-1").title("Single").artist("B").features(neutralFeatures).build();

        LinkedHashMap<String, SongVO> dedup = new LinkedHashMap<>();
        dedup.put("both-1", both);
        dedup.put("solo-1", single);

        Map<String, Double> sourceWeight = new LinkedHashMap<>();
        // both-1 appeared in explore (0.4) then liked (1.0) → max already merged = 1.0
        sourceWeight.put("both-1", 1.0);
        // solo-1 only in recommend = 0.8
        sourceWeight.put("solo-1", 0.8);

        MoodParams mood = buildMood(0.5, 0.5, null, null);

        List<SongVO> ranked = service.scoreAndSort(dedup, sourceWeight, mood);

        assertEquals("both-1", ranked.get(0).getPlatformSongId(),
                "Song with max(liked,explore)=1.0 should beat recommend=0.8 song");
    }

    @Test
    void avoidPenalty_sinksToBottom() {
        String neutralFeatures = "{\"valence\":0.5,\"energy\":0.5}";

        // goodSong: liked weight + neutral emotion → high rank
        SongVO goodSong = SongVO.builder()
                .platformSongId("g1").title("Good Music").artist("Friend")
                .features(neutralFeatures).build();
        // avoidSong: liked weight but title hits avoidKeywords → should sink
        SongVO avoidSong = SongVO.builder()
                .platformSongId("a1").title("Death Metal").artist("Screamer")
                .features(neutralFeatures).build();

        LinkedHashMap<String, SongVO> dedup = new LinkedHashMap<>();
        dedup.put("g1", goodSong);
        dedup.put("a1", avoidSong);

        Map<String, Double> sourceWeight = new LinkedHashMap<>();
        sourceWeight.put("g1", 1.0);
        sourceWeight.put("a1", 1.0);

        MoodParams mood = buildMood(0.5, 0.5, null, null);
        mood.setAvoidKeywords(List.of("death"));

        List<SongVO> ranked = service.scoreAndSort(dedup, sourceWeight, mood);

        assertEquals(2, ranked.size());
        assertEquals("g1", ranked.get(0).getPlatformSongId(),
                "avoidKeyword-hitting song should rank last");
        assertEquals("a1", ranked.get(1).getPlatformSongId());
    }

    // -----------------------------------------------------------------------
    // Regression: comparator-contract fix (T1-1 stable sort with 100 candidates)
    // -----------------------------------------------------------------------

    /**
     * Regression test for the TimSort comparator-contract violation fixed in T1-1.
     * Builds 100 candidates all with equal source weight and null features (so their
     * scores differ only by jitter — the worst case for comparator consistency).
     * Before the fix, scoreAndSort re-called computeTotal inside the comparator,
     * producing a fresh random jitter on every pairwise comparison and triggering
     * "Comparison method violates its general contract!" with >~18 candidates.
     */
    @Test
    void scoreAndSort_100CandidatesEqualWeight_doesNotThrowAndReturnsAll() {
        int N = 100;
        LinkedHashMap<String, SongVO> dedup = new LinkedHashMap<>();
        Map<String, Double> sourceWeight = new LinkedHashMap<>();

        for (int i = 0; i < N; i++) {
            String id = "song-" + i;
            SongVO song = SongVO.builder()
                    .platformSongId(id)
                    .title("Song " + i)
                    .artist("Artist " + i)
                    // null features: emotion score falls back to UNKNOWN_EMOTION=0.5 for all
                    .features(null)
                    .build();
            dedup.put(id, song);
            sourceWeight.put(id, 1.0); // identical source weight for all
        }

        MoodParams mood = buildMood(0.5, 0.5, null, null);

        // Must not throw IllegalArgumentException or any other exception
        List<SongVO> result = assertDoesNotThrow(
                () -> service.scoreAndSort(dedup, sourceWeight, mood),
                "scoreAndSort must not throw even when all candidates share identical scores");

        assertEquals(N, result.size(),
                "scoreAndSort must return all " + N + " candidates");
    }

    // -----------------------------------------------------------------------
    // helper
    // -----------------------------------------------------------------------

    private MoodParams buildMood(double valence, double energy,
                                 List<String> genres, List<String> languages) {
        MoodParams mood = new MoodParams();
        MoodParams.MoodVector mv = new MoodParams.MoodVector();
        mv.setValence(valence);
        mv.setEnergy(energy);
        mood.setMood(mv);
        mood.setPreferredGenres(genres != null ? new ArrayList<>(genres) : null);
        mood.setPreferredLanguages(languages != null ? new ArrayList<>(languages) : null);
        return mood;
    }
}
