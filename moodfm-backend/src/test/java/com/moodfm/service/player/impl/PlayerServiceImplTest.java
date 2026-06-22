package com.moodfm.service.player.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodfm.ai.model.MoodParams;
import com.moodfm.client.music.MusicApiClient;
import com.moodfm.common.constant.RedisKeys;
import com.moodfm.common.util.AesUtil;
import com.moodfm.domain.dto.radio.MoodInputRequest;
import com.moodfm.domain.entity.GlobalBlacklist;
import com.moodfm.domain.entity.MoodSession;
import com.moodfm.domain.entity.PlatformBinding;
import com.moodfm.domain.entity.PlatformSongMapping;
import com.moodfm.domain.entity.Song;
import com.moodfm.domain.vo.PlaylistVO;
import com.moodfm.domain.vo.RadioQueueVO;
import com.moodfm.domain.vo.SongVO;
import com.moodfm.service.playlist.PlaylistService;
import com.moodfm.mapper.FeedbackEventMapper;
import com.moodfm.mapper.GlobalBlacklistMapper;
import com.moodfm.mapper.MoodSessionMapper;
import com.moodfm.mapper.PlatformSongMappingMapper;
import com.moodfm.mapper.SongMapper;
import com.moodfm.mapper.UserProfileMapper;
import com.moodfm.service.ai.LlmClient;
import com.moodfm.service.ai.LlmFallbackMetrics;
import com.moodfm.service.ai.MoodAnalysisService;
import com.moodfm.service.embedding.EmbeddingService;
import com.moodfm.service.enrich.SongFeatureService;
import com.moodfm.service.platform.PlatformBindingService;
import com.moodfm.service.player.impl.catalog.SongCatalogService;
import com.moodfm.service.player.impl.playurl.PlayUrlService;
import com.moodfm.service.player.impl.ranking.AiRankingService;
import com.moodfm.service.player.impl.recall.CandidateRecallService;
import com.moodfm.service.player.impl.recall.source.LikedRecallSource;
import com.moodfm.service.player.impl.recall.source.RecommendRecallSource;
import com.moodfm.service.player.impl.recall.source.GenreSearchRecallSource;
import com.moodfm.service.player.impl.recall.source.VibeSearchRecallSource;
import com.moodfm.service.player.impl.recall.source.ExploreSearchRecallSource;
import com.moodfm.service.player.impl.recall.source.VectorRecallSource;
import com.moodfm.service.player.impl.recall.filter.NegativeFeedbackFilter;
import com.moodfm.service.player.impl.recall.filter.BlacklistKeywordFilter;
import com.moodfm.service.player.impl.recall.filter.GlobalBlacklistFilter;
import com.moodfm.service.player.impl.queue.RadioQueueStore;
import com.moodfm.service.player.impl.recall.SongEmbeddingTextBuilder;
import com.moodfm.service.user.UserService;
import com.moodfm.service.vector.QdrantService;
import com.moodfm.service.vector.VectorRecallMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure Mockito unit tests for PlayerServiceImpl.
 * Avoids @SpringBootTest to skip ApplicationContext / JWT_SECRET / DB setup.
 *
 * Focus per project memory:
 *   - 5-path recall merge + dedup
 *   - AI re-rank empty-response fallback
 *   - Global blacklist filtering
 *   - Session TTL "infinite" marker (durationMinutes == null)
 *   - getRecentSessions limit cap (Math.min(limit, 20))
 *   - isSessionExpired branch coverage
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlayerServiceImplTest {

    @Mock private MoodAnalysisService moodAnalysisService;
    @Mock private PlatformBindingService platformBindingService;
    @Mock private MusicApiClient musicApiClient;
    @Mock private MoodSessionMapper sessionMapper;
    @Mock private FeedbackEventMapper feedbackEventMapper;
    @Mock private SongMapper songMapper;
    @Mock private PlatformSongMappingMapper platformSongMappingMapper;
    @Mock private UserProfileMapper userProfileMapper;
    @Mock private UserService userService;
    @Mock private AesUtil aesUtil;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private LlmClient llmClient;
    @Mock private LlmFallbackMetrics llmFallbackMetrics;
    @Mock private EmbeddingService embeddingService;
    @Mock private QdrantService qdrantService;
    @Mock private VectorRecallMetrics vectorRecallMetrics;
    @Mock private GlobalBlacklistMapper globalBlacklistMapper;
    @Mock private SongFeatureService songFeatureService;
    @Mock private SongEmbeddingTextBuilder songEmbeddingTextBuilder;
    @Mock private PlaylistService playlistService;

    @Mock private ValueOperations<String, String> valueOps;
    @Mock private ListOperations<String, String> listOps;

    /** Real ObjectMapper: cleaner than mocking serialize / deserialize semantics. */
    private final ObjectMapper objectMapper = new ObjectMapper();

    private SongCatalogService songCatalogService;

    @InjectMocks private PlayerServiceImpl playerService;

    @BeforeEach
    void setUp() throws Exception {
        // Inject the real ObjectMapper into @InjectMocks-built instance.
        // (Mockito would normally inject a mock; we override via reflection.)
        var field = PlayerServiceImpl.class.getDeclaredField("objectMapper");
        field.setAccessible(true);
        field.set(playerService, objectMapper);

        // Wire a real SongCatalogService built from the existing leaf mocks.
        // enrichTimeoutSeconds moved here from PlayerServiceImpl; default 8 so async enrichment
        // completes without timing out in most tests.
        songCatalogService = new SongCatalogService(songMapper, platformSongMappingMapper, songFeatureService,
                embeddingService, qdrantService, vectorRecallMetrics, songEmbeddingTextBuilder);
        ReflectionTestUtils.setField(songCatalogService, "enrichTimeoutSeconds", 8);
        ReflectionTestUtils.setField(playerService, "songCatalogService", songCatalogService);

        // Wire a real CandidateRecallService from the existing leaf mocks (T3-2: recall sources are now beans).
        // Construct the 6 pluggable RecallSource beans, then build the orchestrator.
        LikedRecallSource liked = new LikedRecallSource(musicApiClient);
        RecommendRecallSource recommend = new RecommendRecallSource(musicApiClient);
        GenreSearchRecallSource genre = new GenreSearchRecallSource(musicApiClient);
        VibeSearchRecallSource vibe = new VibeSearchRecallSource(musicApiClient);
        ExploreSearchRecallSource explore = new ExploreSearchRecallSource(musicApiClient);
        VectorRecallSource vector = new VectorRecallSource(
                embeddingService, qdrantService, vectorRecallMetrics, songMapper, songCatalogService);
        // T3-2 Task 2: the 3 filters are now pluggable @Order CandidateFilter beans,
        // built from the same leaf mocks and injected as an ordered list.
        NegativeFeedbackFilter negativeFilter =
                new NegativeFeedbackFilter(feedbackEventMapper, platformSongMappingMapper, objectMapper);
        BlacklistKeywordFilter blacklistFilter =
                new BlacklistKeywordFilter(userProfileMapper, objectMapper);
        GlobalBlacklistFilter globalFilter = new GlobalBlacklistFilter(globalBlacklistMapper);
        CandidateRecallService candidateRecallService = new CandidateRecallService(
                List.of(liked, recommend, genre, vibe, explore, vector),
                List.of(negativeFilter, blacklistFilter, globalFilter),
                songMapper, platformSongMappingMapper, userService, objectMapper, songEmbeddingTextBuilder);
        ReflectionTestUtils.setField(playerService, "candidateRecallService", candidateRecallService);

        // Default Redis stubs used across most tests (lenient: unused stubs OK).
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForList()).thenReturn(listOps);

        // SongEmbeddingTextBuilder stubs — lenient so tests that don't exercise vector path are OK.
        lenient().when(songEmbeddingTextBuilder.buildVectorQueryText(any(), any(), any())).thenReturn("music");
        lenient().when(songEmbeddingTextBuilder.buildSongEmbeddingText(any())).thenReturn("");

        // Wire a real AiRankingService so llmClient.complete() is still called from inside rank(),
        // keeping the startRadio_rankingPrompt_* ArgumentCaptor tests green.
        AiRankingService aiRankingService = new AiRankingService(llmClient, llmFallbackMetrics, objectMapper);
        ReflectionTestUtils.setField(playerService, "aiRankingService", aiRankingService);

        // Wire a real PlayUrlService constructed from the existing leaf mocks.
        // musicApiClient.getSongUrls() is stubbed to an empty map in startRadio tests,
        // so enrichment is a no-op exactly as before.
        PlayUrlService playUrlService = new PlayUrlService(musicApiClient, platformBindingService, aesUtil,
                platformSongMappingMapper, songMapper);
        ReflectionTestUtils.setField(playerService, "playUrlService", playUrlService);

        // Wire a real RadioQueueStore from the mock redisTemplate + real objectMapper.
        // All verify(valueOps/listOps/redisTemplate...) assertions remain valid unchanged
        // because the real store funnels every call through the same mock redisTemplate.
        RadioQueueStore radioQueueStore = new RadioQueueStore(redisTemplate, objectMapper);
        ReflectionTestUtils.setField(playerService, "radioQueueStore", radioQueueStore);
    }

    // ========================================================================
    // 1. getRecentSessions: limit is capped at 20
    // ========================================================================
    @Test
    void getRecentSessions_capsLimitAt20() {
        when(sessionMapper.selectRecentSessions(eq(42L), eq(20))).thenReturn(Collections.emptyList());

        playerService.getRecentSessions(42L, 50);

        // Verify mapper called with Math.min(50, 20) == 20
        verify(sessionMapper, times(1)).selectRecentSessions(42L, 20);
    }

    @Test
    void getRecentSessions_passesLimitThroughWhenBelowCap() {
        when(sessionMapper.selectRecentSessions(eq(42L), eq(5))).thenReturn(Collections.emptyList());

        playerService.getRecentSessions(42L, 5);

        verify(sessionMapper, times(1)).selectRecentSessions(42L, 5);
    }

    // ========================================================================
    // 2. isSessionExpired: branch coverage
    // ========================================================================
    @Test
    void isSessionExpired_returnsFalseWhenSessionIdNull() {
        assertFalse(playerService.isSessionExpired(null));
    }

    @Test
    void isSessionExpired_returnsTrueWhenKeyMissing() {
        String ttlKey = RedisKeys.format(RedisKeys.SESSION_TTL, 7L);
        when(redisTemplate.hasKey(ttlKey)).thenReturn(false);

        assertTrue(playerService.isSessionExpired(7L));
    }

    @Test
    void isSessionExpired_returnsFalseWhenKeyPresent() {
        String ttlKey = RedisKeys.format(RedisKeys.SESSION_TTL, 7L);
        when(redisTemplate.hasKey(ttlKey)).thenReturn(true);

        assertFalse(playerService.isSessionExpired(7L));
    }

    @Test
    void isSessionExpired_returnsTrueWhenHasKeyReturnsNull() {
        // hasKey can return null per Spring Data Redis contract; service uses
        // Boolean.TRUE.equals(...) so null should yield "expired".
        String ttlKey = RedisKeys.format(RedisKeys.SESSION_TTL, 7L);
        when(redisTemplate.hasKey(ttlKey)).thenReturn(null);

        assertTrue(playerService.isSessionExpired(7L));
    }

    // ========================================================================
    // 3. setSessionDuration: writes Redis TTL key with seconds + expire
    // ========================================================================
    @Test
    void setSessionDuration_writesTtlKeyWithExpiry() {
        playerService.setSessionDuration(99L, 30);

        String key = RedisKeys.format(RedisKeys.SESSION_TTL, 99L);
        verify(valueOps).set(eq(key), eq(String.valueOf(30 * 60)), eq(Duration.ofSeconds(30 * 60)));
    }

    @Test
    void setSessionDuration_noOpForInvalidArgs() {
        playerService.setSessionDuration(null, 10);
        playerService.setSessionDuration(1L, 0);

        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    // ========================================================================
    // 4. startRadio happy path — session TTL "infinite" marker
    //    Covers: 5-path recall merge + dedup, AI rerank empty fallback,
    //    global blacklist filter, durationMinutes==null path.
    // ========================================================================
    @Test
    void startRadio_durationNull_writesInfiniteMarkerWith24hTtl() throws Exception {
        // --- AI mood analysis ---
        when(moodAnalysisService.analyze(any())).thenReturn(MoodParams.defaultParams());

        // --- Session insert: simulate auto-increment id assignment ---
        when(sessionMapper.insert(any(MoodSession.class))).thenAnswer(inv -> {
            MoodSession s = inv.getArgument(0);
            s.setId(1234L);
            return 1;
        });

        // --- Platform binding ---
        PlatformBinding binding = new PlatformBinding();
        binding.setPlatform("netease");
        binding.setCookieEncrypted("enc-cookie");
        when(platformBindingService.getDefaultBinding(7L)).thenReturn(binding);
        when(aesUtil.decrypt("enc-cookie")).thenReturn("plain-cookie");

        // --- Recall paths: every MusicApiClient call returns "empty" JsonNode.
        //     Parser produces an empty list, so candidates list is empty and the
        //     AI rerank shortcut returns it immediately (no LLM call).
        JsonNode emptyNode = objectMapper.createObjectNode();
        when(musicApiClient.getUserLikedSongs(anyString(), anyString())).thenReturn(emptyNode);
        when(musicApiClient.getRecommendSongs(anyString(), anyString())).thenReturn(emptyNode);
        when(musicApiClient.searchSongs(anyString(), anyString(), anyInt(), any())).thenReturn(emptyNode);

        // --- Vector recall: throw to take the catch branch (returns empty list) ---
        when(embeddingService.embed(anyString())).thenThrow(new RuntimeException("qdrant offline"));

        // --- Global blacklist mapper: no entries ---
        when(globalBlacklistMapper.selectList(any())).thenReturn(Collections.emptyList());

        // --- User preferences: null (skip merge) ---
        when(userService.getPreferences(7L)).thenReturn(null);

        // --- Build request with durationMinutes == null (infinite) ---
        MoodInputRequest req = new MoodInputRequest();
        req.setText("relaxing evening");
        req.setDurationMinutes(null);

        RadioQueueVO result = playerService.startRadio(7L, req);

        // --- Assertions ---
        assertNotNull(result);
        assertEquals(1234L, result.getSessionId());
        // Candidates were empty → ranked list also empty
        assertNotNull(result.getSongs());
        assertTrue(result.getSongs().isEmpty(), "expected empty queue when all recall paths are empty");
        assertEquals(0, result.getTotalCount());

        // Infinite marker is written with a 24-hour cap TTL
        String ttlKey = RedisKeys.format(RedisKeys.SESSION_TTL, 1234L);
        verify(valueOps).set(ttlKey, "infinite", Duration.ofHours(24));
        // The no-TTL overload must NOT be called for the infinite path
        verify(valueOps, never()).set(eq(ttlKey), anyString());

        // Empty queue → delete branch (NOT rightPushAll) — verify no transactional set up
        verify(redisTemplate, never()).execute(any(SessionCallback.class));
        // LlmClient is never invoked when candidates list is empty (AI rerank shortcut)
        verify(llmClient, never()).complete(any(), anyString());
    }

    // ========================================================================
    // 5. startRadio with fixed duration writes TTL with Duration
    // ========================================================================
    @Test
    void startRadio_withDuration_setsTtlInSeconds() {
        when(moodAnalysisService.analyze(any())).thenReturn(MoodParams.defaultParams());
        when(sessionMapper.insert(any(MoodSession.class))).thenAnswer(inv -> {
            ((MoodSession) inv.getArgument(0)).setId(55L);
            return 1;
        });

        PlatformBinding binding = new PlatformBinding();
        binding.setPlatform("netease");
        binding.setCookieEncrypted("enc");
        when(platformBindingService.getDefaultBinding(7L)).thenReturn(binding);
        when(aesUtil.decrypt(anyString())).thenReturn("c");

        JsonNode emptyNode = objectMapper.createObjectNode();
        when(musicApiClient.getUserLikedSongs(anyString(), anyString())).thenReturn(emptyNode);
        when(musicApiClient.getRecommendSongs(anyString(), anyString())).thenReturn(emptyNode);
        when(musicApiClient.searchSongs(anyString(), anyString(), anyInt(), any())).thenReturn(emptyNode);
        when(embeddingService.embed(anyString())).thenThrow(new RuntimeException("offline"));
        when(globalBlacklistMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(userService.getPreferences(7L)).thenReturn(null);

        MoodInputRequest req = new MoodInputRequest();
        req.setText("study");
        req.setDurationMinutes(45);

        playerService.startRadio(7L, req);

        // Verify TTL set with exact Duration
        String ttlKey = RedisKeys.format(RedisKeys.SESSION_TTL, 55L);
        verify(valueOps).set(eq(ttlKey), eq(String.valueOf(45 * 60)), eq(Duration.ofSeconds(45L * 60)));
        // The "infinite" marker overload must NOT be called
        verify(valueOps, never()).set(ttlKey, "infinite");
    }

    // ========================================================================
    // 6. 5-path recall merge: overlapping platformSongIds are deduped.
    //    Drives recallSongs() via the public startRadio() entry. The five
    //    music-API paths each return JSON with overlapping ids; we verify the
    //    final RadioQueueVO.songs contains unique ids only.
    // ========================================================================
    @Test
    void startRadio_recallPaths_deduplicateByPlatformSongId() {
        when(moodAnalysisService.analyze(any())).thenReturn(MoodParams.defaultParams());
        when(sessionMapper.insert(any(MoodSession.class))).thenAnswer(inv -> {
            ((MoodSession) inv.getArgument(0)).setId(1L);
            return 1;
        });

        PlatformBinding binding = new PlatformBinding();
        binding.setPlatform("netease");
        binding.setCookieEncrypted("enc");
        when(platformBindingService.getDefaultBinding(7L)).thenReturn(binding);
        when(aesUtil.decrypt(anyString())).thenReturn("c");
        when(globalBlacklistMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(userService.getPreferences(7L)).thenReturn(null);
        when(embeddingService.embed(anyString())).thenThrow(new RuntimeException("vec off"));

        // Each path returns the same 3 songs (ids 100, 200, 300). After dedup
        // and AI rerank fallback (empty LLM response), the candidates -> ranked
        // list must contain exactly 3 unique entries.
        JsonNode threeSongs = objectMapper.valueToTree(java.util.Map.of(
                "songs", List.of(
                        java.util.Map.of("id", 100, "name", "Song A",
                                "ar", List.of(java.util.Map.of("name", "ArtistA"))),
                        java.util.Map.of("id", 200, "name", "Song B",
                                "ar", List.of(java.util.Map.of("name", "ArtistB"))),
                        java.util.Map.of("id", 300, "name", "Song C",
                                "ar", List.of(java.util.Map.of("name", "ArtistC")))
                )
        ));
        when(musicApiClient.getUserLikedSongs(anyString(), anyString())).thenReturn(threeSongs);
        when(musicApiClient.getRecommendSongs(anyString(), anyString())).thenReturn(threeSongs);
        when(musicApiClient.searchSongs(anyString(), anyString(), anyInt(), any())).thenReturn(threeSongs);

        // AI rerank: empty LLM response -> goes through applyRanking's catch
        // branch and returns candidates.stream().limit(20). NO exception.
        when(llmClient.complete(any(), anyString())).thenReturn("");

        // Play-url batch fetch: return empty map (no enrichment, no NPE)
        when(musicApiClient.getSongUrls(anyString(), anyList(), any()))
                .thenReturn(java.util.Map.of());

        // persistSongs needs songMapper.selectList for the (title, artist) batch
        // lookup — return empty so every song is treated as "new".
        when(songMapper.selectList(any())).thenReturn(Collections.emptyList());
        // platformSongMappingMapper.selectList for the per-new-song mapping check
        when(platformSongMappingMapper.selectList(any())).thenReturn(Collections.emptyList());
        // SongFeatureService: stub to return instantly (avoids 8s timeout on async enrichment)
        when(songFeatureService.enrich(anyString(), anyString(), any()))
                .thenReturn("{\"valence\":0.5,\"energy\":0.5,\"genre\":\"未知\",\"language\":\"zh\"," +
                        "\"tempo_bucket\":\"mid\",\"mood_tags\":[],\"source\":\"ai\",\"version\":1}");

        MoodInputRequest req = new MoodInputRequest();
        req.setText("happy");
        req.setDurationMinutes(30);

        RadioQueueVO result = playerService.startRadio(7L, req);

        assertNotNull(result);
        assertNotNull(result.getSongs());
        // Dedup: 5 paths × 3 songs (all overlapping) -> 3 unique
        assertEquals(3, result.getSongs().size(),
                "expected 3 unique songs after dedup across 5 recall paths");
        // Sanity check: every platformSongId is unique
        long uniqueIds = result.getSongs().stream()
                .map(SongVO::getPlatformSongId)
                .distinct()
                .count();
        assertEquals(3, uniqueIds);
    }

    // ========================================================================
    // 7. Global blacklist filter: banned artist removed from final queue
    // ========================================================================
    @Test
    void startRadio_globalBlacklist_filtersBannedArtist() {
        when(moodAnalysisService.analyze(any())).thenReturn(MoodParams.defaultParams());
        when(sessionMapper.insert(any(MoodSession.class))).thenAnswer(inv -> {
            ((MoodSession) inv.getArgument(0)).setId(2L);
            return 1;
        });

        PlatformBinding binding = new PlatformBinding();
        binding.setPlatform("netease");
        binding.setCookieEncrypted("enc");
        when(platformBindingService.getDefaultBinding(7L)).thenReturn(binding);
        when(aesUtil.decrypt(anyString())).thenReturn("c");
        when(userService.getPreferences(7L)).thenReturn(null);
        when(embeddingService.embed(anyString())).thenThrow(new RuntimeException("vec off"));

        // Two recall hits with different artists; "BannedArtist" should be filtered out
        JsonNode twoSongs = objectMapper.valueToTree(java.util.Map.of(
                "songs", List.of(
                        java.util.Map.of("id", 111, "name", "Good Song",
                                "ar", List.of(java.util.Map.of("name", "GoodArtist"))),
                        java.util.Map.of("id", 222, "name", "Bad Song",
                                "ar", List.of(java.util.Map.of("name", "BannedArtist")))
                )
        ));
        when(musicApiClient.getUserLikedSongs(anyString(), anyString())).thenReturn(twoSongs);
        when(musicApiClient.getRecommendSongs(anyString(), anyString())).thenReturn(twoSongs);
        when(musicApiClient.searchSongs(anyString(), anyString(), anyInt(), any())).thenReturn(twoSongs);

        // Global blacklist: ban artist "bannedartist" (lowercase: filter normalizes)
        GlobalBlacklist banned = new GlobalBlacklist();
        banned.setType("artist");
        banned.setValue("bannedartist");
        when(globalBlacklistMapper.selectList(any())).thenReturn(List.of(banned));

        when(llmClient.complete(any(), anyString())).thenReturn("");
        when(musicApiClient.getSongUrls(anyString(), anyList(), any())).thenReturn(java.util.Map.of());
        when(songMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(platformSongMappingMapper.selectList(any())).thenReturn(Collections.emptyList());
        // SongFeatureService: stub to return instantly (avoids 8s timeout on async enrichment)
        when(songFeatureService.enrich(anyString(), anyString(), any()))
                .thenReturn("{\"valence\":0.5,\"energy\":0.5,\"genre\":\"未知\",\"language\":\"zh\"," +
                        "\"tempo_bucket\":\"mid\",\"mood_tags\":[],\"source\":\"ai\",\"version\":1}");

        MoodInputRequest req = new MoodInputRequest();
        req.setText("anything");
        req.setDurationMinutes(30);

        RadioQueueVO result = playerService.startRadio(7L, req);

        assertNotNull(result);
        assertEquals(1, result.getSongs().size(), "banned artist should be removed");
        assertEquals("GoodArtist", result.getSongs().get(0).getArtist());
        // Defensive: the banned platformSongId is NOT present
        assertFalse(result.getSongs().stream()
                .anyMatch(s -> "222".equals(s.getPlatformSongId())));
    }

    // ========================================================================
    // 8. reRankIfNeeded: increments counter, only triggers async work at threshold
    // ========================================================================
    @Test
    void reRankIfNeeded_belowThreshold_doesNotResetCounter() {
        String counterKey = RedisKeys.format(RedisKeys.QUEUE_RERANK, 7L);
        when(valueOps.increment(counterKey)).thenReturn(1L);

        playerService.reRankIfNeeded(7L, 100L);

        verify(valueOps).increment(counterKey);
        verify(redisTemplate).expire(eq(counterKey), any(Duration.class));
        // Counter should NOT be reset (still below RERANK_EVERY=3)
        verify(redisTemplate, never()).delete(counterKey);
    }

    @Test
    void reRankIfNeeded_atThreshold_deletesCounter() {
        String counterKey = RedisKeys.format(RedisKeys.QUEUE_RERANK, 7L);
        when(valueOps.increment(counterKey)).thenReturn(3L);
        // doReRank runs async; we make its first dep (platformBindingService) throw
        // so the background task exits early without touching other mocks.
        lenient().when(platformBindingService.getDefaultBinding(7L))
                .thenThrow(new RuntimeException("ignored async failure"));

        playerService.reRankIfNeeded(7L, 100L);

        verify(valueOps).increment(counterKey);
        // At threshold: counter is reset
        verify(redisTemplate).delete(counterKey);
    }

    @Test
    void reRankIfNeeded_nullUserId_isNoOp() {
        playerService.reRankIfNeeded(null, 1L);
        verify(valueOps, never()).increment(anyString());
    }

    // ========================================================================
    // 10. buildRankingPrompt via startRadio: ArgumentCaptor asserts features
    //     appear in the prompt passed to llmClient.complete().
    //     The song is treated as EXISTING (backfill path) so features are
    //     populated before rankWithAI is called.
    // ========================================================================
    @Test
    void startRadio_rankingPrompt_containsFeaturesFromCandidates() throws Exception {
        when(moodAnalysisService.analyze(any())).thenReturn(MoodParams.defaultParams());
        when(sessionMapper.insert(any(MoodSession.class))).thenAnswer(inv -> {
            ((MoodSession) inv.getArgument(0)).setId(10L);
            return 1;
        });

        PlatformBinding binding = new PlatformBinding();
        binding.setPlatform("netease");
        binding.setCookieEncrypted("enc");
        when(platformBindingService.getDefaultBinding(7L)).thenReturn(binding);
        when(aesUtil.decrypt(anyString())).thenReturn("c");
        when(globalBlacklistMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(userService.getPreferences(7L)).thenReturn(null);
        when(embeddingService.embed(anyString())).thenThrow(new RuntimeException("vec off"));

        // One song so it definitely enters rankWithAI
        JsonNode oneSong = objectMapper.valueToTree(java.util.Map.of(
                "songs", List.of(
                        java.util.Map.of("id", 501, "name", "月光曲",
                                "ar", List.of(java.util.Map.of("name", "钢琴师")))
                )
        ));
        when(musicApiClient.getUserLikedSongs(anyString(), anyString())).thenReturn(oneSong);
        when(musicApiClient.getRecommendSongs(anyString(), anyString())).thenReturn(oneSong);
        when(musicApiClient.searchSongs(anyString(), anyString(), anyInt(), any())).thenReturn(oneSong);
        when(musicApiClient.getSongUrls(anyString(), anyList(), any())).thenReturn(java.util.Map.of());

        // Features string with the specific values we'll assert on
        String richFeatures = "{\"valence\":0.2,\"energy\":0.3,\"genre\":\"民谣\"," +
                "\"mood_tags\":[\"夜晚\",\"松弛\"],\"language\":\"zh\"," +
                "\"tempo_bucket\":\"low\",\"source\":\"ai\",\"version\":1}";

        // Set up backfill: platformSongMappingMapper returns a mapping for "501" → songId=99
        // so backfillFeatures() will find the song in DB and populate features BEFORE rankWithAI.
        PlatformSongMapping psm = new PlatformSongMapping();
        psm.setSongId(99L);
        psm.setPlatform("netease");
        psm.setPlatformSongId("501");
        when(platformSongMappingMapper.selectList(any())).thenReturn(List.of(psm));

        // songMapper.selectBatchIds returns the Song with features (used in backfill)
        Song existingSong = new Song();
        existingSong.setId(99L);
        existingSong.setFeatures(richFeatures);
        when(songMapper.selectBatchIds(any())).thenReturn(List.of(existingSong));

        // selectList (used in persistSongs title/artist lookup) → treat as existing
        when(songMapper.selectList(any())).thenReturn(Collections.emptyList());

        // Enrich not needed (song is existing), but stub to be safe
        when(songFeatureService.enrich(anyString(), anyString(), any())).thenReturn(richFeatures);

        // Capture the prompt passed to llmClient
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        when(llmClient.complete(any(), promptCaptor.capture())).thenReturn("");

        MoodInputRequest req = new MoodInputRequest();
        req.setText("quiet night");
        req.setDurationMinutes(30);
        playerService.startRadio(7L, req);

        // llmClient.complete must have been called (candidates non-empty → rankWithAI)
        verify(llmClient, times(1)).complete(any(), anyString());

        String capturedPrompt = promptCaptor.getValue();
        assertTrue(capturedPrompt.contains("0.2"),  "valence 0.2 should be in prompt; got:\n" + capturedPrompt);
        assertTrue(capturedPrompt.contains("0.3"),  "energy 0.3 should be in prompt; got:\n" + capturedPrompt);
        assertTrue(capturedPrompt.contains("民谣"), "genre 民谣 should be in prompt; got:\n" + capturedPrompt);
        assertTrue(capturedPrompt.contains("夜晚"), "mood_tag 夜晚 should be in prompt; got:\n" + capturedPrompt);
    }

    @Test
    void startRadio_rankingPrompt_nullFeatures_showsUnknown() throws Exception {
        when(moodAnalysisService.analyze(any())).thenReturn(MoodParams.defaultParams());
        when(sessionMapper.insert(any(MoodSession.class))).thenAnswer(inv -> {
            ((MoodSession) inv.getArgument(0)).setId(11L);
            return 1;
        });

        PlatformBinding binding = new PlatformBinding();
        binding.setPlatform("netease");
        binding.setCookieEncrypted("enc");
        when(platformBindingService.getDefaultBinding(7L)).thenReturn(binding);
        when(aesUtil.decrypt(anyString())).thenReturn("c");
        when(globalBlacklistMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(userService.getPreferences(7L)).thenReturn(null);
        when(embeddingService.embed(anyString())).thenThrow(new RuntimeException("vec off"));

        // Fixture artist must NOT contain "未知" so the only source of "未知" in the
        // captured prompt is the feature-fallback columns, not the artist string itself.
        // (The authoritative null-path unit test is formatCandidateLine_nullFeatures_allFieldsFallToUnknown;
        //  this test only verifies end-to-end wiring between persistSongs and buildRankingPrompt.)
        JsonNode oneSong = objectMapper.valueToTree(java.util.Map.of(
                "songs", List.of(
                        java.util.Map.of("id", 502, "name", "无特征曲",
                                "ar", List.of(java.util.Map.of("name", "测试艺人")))
                )
        ));
        when(musicApiClient.getUserLikedSongs(anyString(), anyString())).thenReturn(oneSong);
        when(musicApiClient.getRecommendSongs(anyString(), anyString())).thenReturn(oneSong);
        when(musicApiClient.searchSongs(anyString(), anyString(), anyInt(), any())).thenReturn(oneSong);
        when(musicApiClient.getSongUrls(anyString(), anyList(), any())).thenReturn(java.util.Map.of());
        when(songMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(platformSongMappingMapper.selectList(any())).thenReturn(Collections.emptyList());

        // fallback features: null features (simulate enrich returning null-features song)
        // We return a features JSON that is null by having the fallback return null-features path.
        // In this test we force the features to be null by stubbing fallbackFeatures AND
        // making enrich throw, so the catch path sets features = fallbackFeatures().
        // Alternatively: stub enrich to return null so the song.features stays null.
        when(songFeatureService.enrich(anyString(), anyString(), any())).thenReturn(null);
        when(songFeatureService.fallbackFeatures(anyString(), anyString(), any())).thenReturn(null);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        when(llmClient.complete(any(), promptCaptor.capture())).thenReturn("");

        MoodInputRequest req = new MoodInputRequest();
        req.setText("mystery");
        req.setDurationMinutes(30);
        playerService.startRadio(7L, req);

        verify(llmClient, times(1)).complete(any(), anyString());
        String capturedPrompt = promptCaptor.getValue();
        assertTrue(capturedPrompt.contains("未知"),
                "prompt should contain 未知 when features are null; got:\n" + capturedPrompt);
    }

    // ========================================================================
    // 12. Enrichment timeout cap: configurable timeout is actually enforced
    //    Sets enrichTimeoutSeconds=1 via reflection, makes enrich() block 3s,
    //    drives a startRadio call with one new song, and asserts it returns in
    //    well under 3s with a fallback-sourced features JSON.
    // ========================================================================
    @Test
    void persistSongs_enrichmentTimeout_returnsWithinCapNotBlockedBySlowLlm() throws Exception {
        // --- Set the timeout to 1 second so the test runs fast ---
        ReflectionTestUtils.setField(songCatalogService, "enrichTimeoutSeconds", 1);

        // --- Wiring identical to test 6 but with a slow enrich() stub ---
        when(moodAnalysisService.analyze(any())).thenReturn(MoodParams.defaultParams());
        when(sessionMapper.insert(any(MoodSession.class))).thenAnswer(inv -> {
            ((MoodSession) inv.getArgument(0)).setId(9L);
            return 1;
        });

        PlatformBinding binding = new PlatformBinding();
        binding.setPlatform("netease");
        binding.setCookieEncrypted("enc");
        when(platformBindingService.getDefaultBinding(7L)).thenReturn(binding);
        when(aesUtil.decrypt(anyString())).thenReturn("c");
        when(globalBlacklistMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(userService.getPreferences(7L)).thenReturn(null);
        when(embeddingService.embed(anyString())).thenThrow(new RuntimeException("vec off"));

        // One unique song so persistSongs sees exactly one "truly new" song
        JsonNode oneSong = objectMapper.valueToTree(java.util.Map.of(
                "songs", List.of(
                        java.util.Map.of("id", 999, "name", "Slow Song",
                                "ar", List.of(java.util.Map.of("name", "SlowArtist")))
                )
        ));
        when(musicApiClient.getUserLikedSongs(anyString(), anyString())).thenReturn(oneSong);
        when(musicApiClient.getRecommendSongs(anyString(), anyString())).thenReturn(oneSong);
        when(musicApiClient.searchSongs(anyString(), anyString(), anyInt(), any())).thenReturn(oneSong);
        when(llmClient.complete(any(), anyString())).thenReturn("");
        when(musicApiClient.getSongUrls(anyString(), anyList(), any())).thenReturn(java.util.Map.of());
        when(songMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(platformSongMappingMapper.selectList(any())).thenReturn(Collections.emptyList());

        // enrich() blocks for 3 seconds — far beyond the 1s cap
        when(songFeatureService.enrich(anyString(), anyString(), any())).thenAnswer(inv -> {
            Thread.sleep(3000);
            return "{\"source\":\"ai\"}";
        });

        // fallbackFeatures() returns instantly with a fallback JSON
        String fallbackJson = "{\"valence\":0.5,\"energy\":0.5,\"genre\":\"未知\",\"language\":\"en\"," +
                "\"tempo_bucket\":\"mid\",\"mood_tags\":[],\"source\":\"fallback\",\"version\":1}";
        when(songFeatureService.fallbackFeatures(anyString(), anyString(), any()))
                .thenReturn(fallbackJson);

        // songMapper.insert: capture the inserted Song so we can inspect its features
        com.moodfm.domain.entity.Song[] insertedSong = {null};
        when(songMapper.insert(any(com.moodfm.domain.entity.Song.class))).thenAnswer(inv -> {
            com.moodfm.domain.entity.Song s = inv.getArgument(0);
            s.setId(42L);
            insertedSong[0] = s;
            return 1;
        });

        long start = System.currentTimeMillis();
        RadioQueueVO result = playerService.startRadio(7L, new MoodInputRequest() {{
            setText("timeout test");
            setDurationMinutes(30);
        }});
        long elapsed = System.currentTimeMillis() - start;

        // Must complete well before the 3s LLM sleep
        assertTrue(elapsed < 2500,
                "startRadio should return within 2500ms when enrich() is slow; took " + elapsed + "ms");

        // The song was still persisted (queue has 1 entry)
        assertNotNull(result.getSongs());
        assertEquals(1, result.getSongs().size());

        // The inserted Song should have been given the fallback JSON (not the slow AI result)
        assertNotNull(insertedSong[0], "songMapper.insert should have been called for the new song");
        String features = insertedSong[0].getFeatures();
        assertNotNull(features, "features must not be null");
        assertTrue(features.contains("\"source\":\"fallback\""),
                "timed-out song should have fallback features; got: " + features);
    }

    // ========================================================================
    // startRadioFromPlaylist
    // ========================================================================

    @Test
    void startRadioFromPlaylist_mergesPlaylistTracksAndCreatesSession() throws Exception {
        SongVO seed = SongVO.builder()
                .title("Night Walk")
                .artist("Artist A")
                .platform("netease")
                .platformSongId("1001")
                .durationSeconds(200)
                .build();

        when(playlistService.getPlaylist(eq(7L), eq("netease:42"))).thenReturn(
                PlaylistVO.builder()
                        .id("netease:42")
                        .name("Night Float")
                        .platform("netease")
                        .trackCount(1)
                        .tracks(List.of(seed))
                        .build());

        when(sessionMapper.insert(any(MoodSession.class))).thenAnswer(inv -> {
            MoodSession s = inv.getArgument(0);
            s.setId(900L);
            return 1;
        });

        PlatformBinding binding = new PlatformBinding();
        binding.setPlatform("netease");
        binding.setCookieEncrypted("enc");
        when(platformBindingService.getValidBinding(7L, "netease")).thenReturn(binding);
        when(aesUtil.decrypt("enc")).thenReturn("cookie");

        JsonNode emptyNode = objectMapper.createObjectNode();
        when(musicApiClient.getUserLikedSongs(anyString(), anyString())).thenReturn(emptyNode);
        when(musicApiClient.getRecommendSongs(anyString(), anyString())).thenReturn(emptyNode);
        when(musicApiClient.searchSongs(anyString(), anyString(), anyInt(), any())).thenReturn(emptyNode);

        when(llmClient.complete(anyString(), anyString())).thenReturn("[]");

        RadioQueueVO result = playerService.startRadioFromPlaylist(7L, "netease:42", 30);

        assertEquals(900L, result.getSessionId());
        assertEquals("playlist", result.getScene());
        assertTrue(result.getMoodSummary().contains("Night Float"));
        assertNotNull(result.getSongs());
        assertFalse(result.getSongs().isEmpty());
        assertEquals("Night Walk", result.getSongs().get(0).getTitle());

        ArgumentCaptor<MoodSession> sessionCaptor = ArgumentCaptor.forClass(MoodSession.class);
        verify(sessionMapper).insert(sessionCaptor.capture());
        assertEquals("playlist", sessionCaptor.getValue().getScene());
    }
}
