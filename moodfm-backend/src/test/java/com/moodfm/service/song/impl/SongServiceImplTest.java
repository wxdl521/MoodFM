package com.moodfm.service.song.impl;

import com.moodfm.client.music.MusicApiClient;
import com.moodfm.client.music.SongApiClient;
import com.moodfm.common.exception.BizException;
import com.moodfm.common.result.ResultCode;
import com.moodfm.common.util.AesUtil;
import com.moodfm.domain.entity.PlatformBinding;
import com.moodfm.domain.entity.PlatformSongMapping;
import com.moodfm.mapper.FeedbackEventMapper;
import com.moodfm.mapper.PlatformSongMappingMapper;
import com.moodfm.mapper.SongMapper;
import com.moodfm.service.platform.PlatformBindingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure-Mockito unit tests for {@link SongServiceImpl}.
 * Avoids @SpringBootTest so it does not require JWT_SECRET, DB, or any context bean.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SongServiceImplTest {

    @Mock private PlatformBindingService bindingService;
    @Mock private SongApiClient songApiClient;
    @Mock private MusicApiClient musicApiClient;
    @Mock private FeedbackEventMapper feedbackEventMapper;
    @Mock private PlatformSongMappingMapper platformSongMappingMapper;
    @Mock private SongMapper songMapper;
    @Mock private AesUtil aesUtil;

    @InjectMocks
    private SongServiceImpl songService;

    private static final Long USER_ID = 42L;
    private static final Long SONG_ID = 100L;
    private static final String PLATFORM = "netease";
    private static final String PLATFORM_SONG_ID = "ne-100";

    private PlatformSongMapping mapping(Long songId, String platform, String platformSongId) {
        PlatformSongMapping m = new PlatformSongMapping();
        m.setId(songId);
        m.setSongId(songId);
        m.setPlatform(platform);
        m.setPlatformSongId(platformSongId);
        return m;
    }

    private PlatformBinding binding(String platform) {
        PlatformBinding b = new PlatformBinding();
        b.setId(1L);
        b.setUserId(USER_ID);
        b.setPlatform(platform);
        b.setCookieEncrypted("enc-cookie");
        return b;
    }

    @BeforeEach
    void setUpCommon() {
        // Default decrypt behaviour for any encrypted cookie.
        lenient().when(aesUtil.decrypt(anyString())).thenReturn("plain-cookie");
    }

    // ---------- getAudioUrl ----------

    @Test
    void getAudioUrl_returnsNull_whenClientReturnsEmptyString() {
        when(platformSongMappingMapper.selectOne(any()))
                .thenReturn(mapping(SONG_ID, PLATFORM, PLATFORM_SONG_ID));
        when(bindingService.getValidBinding(USER_ID, PLATFORM)).thenReturn(binding(PLATFORM));
        when(musicApiClient.getSongUrl(eq(PLATFORM), eq(PLATFORM_SONG_ID), anyString())).thenReturn("");

        String url = songService.getAudioUrl(USER_ID, SONG_ID);

        assertNull(url, "empty string from client must be normalized to null");
    }

    @Test
    void getAudioUrl_returnsNull_whenClientReturnsNull() {
        when(platformSongMappingMapper.selectOne(any()))
                .thenReturn(mapping(SONG_ID, PLATFORM, PLATFORM_SONG_ID));
        when(bindingService.getValidBinding(USER_ID, PLATFORM)).thenReturn(binding(PLATFORM));
        when(musicApiClient.getSongUrl(anyString(), anyString(), anyString())).thenReturn(null);

        assertNull(songService.getAudioUrl(USER_ID, SONG_ID));
    }

    @Test
    void getAudioUrl_returnsUrl_onHappyPath() {
        when(platformSongMappingMapper.selectOne(any()))
                .thenReturn(mapping(SONG_ID, PLATFORM, PLATFORM_SONG_ID));
        when(bindingService.getValidBinding(USER_ID, PLATFORM)).thenReturn(binding(PLATFORM));
        when(musicApiClient.getSongUrl(anyString(), anyString(), anyString()))
                .thenReturn("https://cdn/song.mp3");

        assertEquals("https://cdn/song.mp3", songService.getAudioUrl(USER_ID, SONG_ID));
    }

    @Test
    void getAudioUrl_returnsNull_whenNoMappingExists() {
        when(platformSongMappingMapper.selectOne(any())).thenReturn(null);

        assertNull(songService.getAudioUrl(USER_ID, SONG_ID));
        verify(musicApiClient, never()).getSongUrl(anyString(), anyString(), anyString());
    }

    @Test
    void getAudioUrl_swallowsClientException_andReturnsNull() {
        when(platformSongMappingMapper.selectOne(any()))
                .thenReturn(mapping(SONG_ID, PLATFORM, PLATFORM_SONG_ID));
        when(bindingService.getValidBinding(USER_ID, PLATFORM)).thenReturn(binding(PLATFORM));
        when(musicApiClient.getSongUrl(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("adapter down"));

        // Should NOT throw — service catches and returns null.
        assertNull(songService.getAudioUrl(USER_ID, SONG_ID));
    }

    @Test
    void getAudioUrl_fallsBackToDefaultBinding_whenValidBindingThrowsBizException() {
        when(platformSongMappingMapper.selectOne(any()))
                .thenReturn(mapping(SONG_ID, PLATFORM, PLATFORM_SONG_ID));
        when(bindingService.getValidBinding(USER_ID, PLATFORM))
                .thenThrow(new BizException(ResultCode.PLATFORM_COOKIE_INVALID, "binding expired"));
        when(bindingService.getDefaultBinding(USER_ID)).thenReturn(binding(PLATFORM));
        when(musicApiClient.getSongUrl(anyString(), anyString(), anyString()))
                .thenReturn("https://fallback/song.mp3");

        String url = songService.getAudioUrl(USER_ID, SONG_ID);

        assertEquals("https://fallback/song.mp3", url);
        verify(bindingService, times(1)).getDefaultBinding(USER_ID);
    }

    // ---------- getAudioUrls (batch) ----------

    @Test
    void getAudioUrls_returnsEmptyMap_forNullInput() {
        Map<Long, String> result = songService.getAudioUrls(USER_ID, null);

        assertTrue(result.isEmpty());
        verify(platformSongMappingMapper, never()).selectList(any());
        verify(musicApiClient, never()).getSongUrls(anyString(), anyList(), anyString());
    }

    @Test
    void getAudioUrls_returnsEmptyMap_forEmptyInput() {
        Map<Long, String> result = songService.getAudioUrls(USER_ID, List.of());

        assertTrue(result.isEmpty());
        verify(platformSongMappingMapper, never()).selectList(any());
        verify(musicApiClient, never()).getSongUrls(anyString(), anyList(), anyString());
    }

    @Test
    void getAudioUrls_returnsSingleEntry_forSingletonList() {
        PlatformSongMapping m = mapping(SONG_ID, PLATFORM, PLATFORM_SONG_ID);
        when(platformSongMappingMapper.selectList(any())).thenReturn(List.of(m));
        when(bindingService.getValidBinding(USER_ID, PLATFORM)).thenReturn(binding(PLATFORM));

        Map<String, String> urls = new HashMap<>();
        urls.put(PLATFORM_SONG_ID, "https://cdn/100.mp3");
        when(musicApiClient.getSongUrls(eq(PLATFORM), anyList(), anyString())).thenReturn(urls);

        Map<Long, String> result = songService.getAudioUrls(USER_ID, List.of(SONG_ID));

        assertEquals(1, result.size());
        assertEquals("https://cdn/100.mp3", result.get(SONG_ID));
    }

    @Test
    void getAudioUrls_filtersBlankAndMissingUrls() {
        PlatformSongMapping m1 = mapping(1L, PLATFORM, "p1");
        PlatformSongMapping m2 = mapping(2L, PLATFORM, "p2");
        PlatformSongMapping m3 = mapping(3L, PLATFORM, "p3");
        when(platformSongMappingMapper.selectList(any())).thenReturn(List.of(m1, m2, m3));
        when(bindingService.getValidBinding(USER_ID, PLATFORM)).thenReturn(binding(PLATFORM));

        Map<String, String> urls = new HashMap<>();
        urls.put("p1", "https://cdn/1.mp3");
        urls.put("p2", ""); // blank → filtered out
        // p3 missing entirely → filtered out
        when(musicApiClient.getSongUrls(eq(PLATFORM), anyList(), anyString())).thenReturn(urls);

        Map<Long, String> result = songService.getAudioUrls(USER_ID, List.of(1L, 2L, 3L));

        assertEquals(1, result.size());
        assertEquals("https://cdn/1.mp3", result.get(1L));
        assertFalse(result.containsKey(2L));
        assertFalse(result.containsKey(3L));
    }

    @Test
    void getAudioUrls_returnsEmptyMap_whenNoMappingsFound() {
        when(platformSongMappingMapper.selectList(any())).thenReturn(List.of());

        Map<Long, String> result = songService.getAudioUrls(USER_ID, List.of(1L, 2L));

        assertTrue(result.isEmpty());
        verify(musicApiClient, never()).getSongUrls(anyString(), anyList(), anyString());
    }

    @Test
    void getAudioUrls_partialPlatformFailure_otherPlatformStillReturnsResults() {
        // 5 songs across two platforms: netease (3) + qqmusic (2).
        // qqmusic batch throws — netease results should still come through.
        PlatformSongMapping n1 = mapping(1L, "netease", "n1");
        PlatformSongMapping n2 = mapping(2L, "netease", "n2");
        PlatformSongMapping n3 = mapping(3L, "netease", "n3");
        PlatformSongMapping q4 = mapping(4L, "qqmusic", "q4");
        PlatformSongMapping q5 = mapping(5L, "qqmusic", "q5");
        when(platformSongMappingMapper.selectList(any()))
                .thenReturn(List.of(n1, n2, n3, q4, q5));

        when(bindingService.getValidBinding(USER_ID, "netease")).thenReturn(binding("netease"));
        when(bindingService.getValidBinding(USER_ID, "qqmusic")).thenReturn(binding("qqmusic"));

        Map<String, String> neteaseUrls = new HashMap<>();
        neteaseUrls.put("n1", "https://cdn/n1.mp3");
        neteaseUrls.put("n2", "https://cdn/n2.mp3");
        neteaseUrls.put("n3", "https://cdn/n3.mp3");
        when(musicApiClient.getSongUrls(eq("netease"), anyList(), anyString())).thenReturn(neteaseUrls);
        when(musicApiClient.getSongUrls(eq("qqmusic"), anyList(), anyString()))
                .thenThrow(new RuntimeException("qq adapter exploded"));

        Map<Long, String> result = songService.getAudioUrls(USER_ID, List.of(1L, 2L, 3L, 4L, 5L));

        // netease 3 entries survive; qqmusic 2 dropped silently
        assertEquals(3, result.size());
        assertEquals("https://cdn/n1.mp3", result.get(1L));
        assertEquals("https://cdn/n2.mp3", result.get(2L));
        assertEquals("https://cdn/n3.mp3", result.get(3L));
        assertFalse(result.containsKey(4L));
        assertFalse(result.containsKey(5L));
    }

    @Test
    void getAudioUrls_perPlatformBindingFallback_whenValidBindingThrows() {
        PlatformSongMapping m = mapping(SONG_ID, PLATFORM, PLATFORM_SONG_ID);
        when(platformSongMappingMapper.selectList(any())).thenReturn(List.of(m));
        when(bindingService.getValidBinding(USER_ID, PLATFORM))
                .thenThrow(new BizException(ResultCode.PLATFORM_NOT_BOUND, "no valid binding"));
        when(bindingService.getDefaultBinding(USER_ID)).thenReturn(binding(PLATFORM));

        Map<String, String> urls = new HashMap<>();
        urls.put(PLATFORM_SONG_ID, "https://fallback/100.mp3");
        when(musicApiClient.getSongUrls(eq(PLATFORM), anyList(), anyString())).thenReturn(urls);

        Map<Long, String> result = songService.getAudioUrls(USER_ID, List.of(SONG_ID));

        assertEquals(1, result.size());
        assertEquals("https://fallback/100.mp3", result.get(SONG_ID));
        verify(bindingService).getDefaultBinding(USER_ID);
    }

    // ---------- caching note ----------
    // SongServiceImpl uses @Cacheable on getSongDetail, but @Cacheable is a Spring AOP
    // concern that only activates inside an application context. With pure Mockito
    // wiring (@InjectMocks), the proxy never gets installed and every call hits the
    // real method, so cache-key behavior is out of scope for this unit-test class.
    // SongServiceCacheTest (a @SpringBootTest) covers cache semantics end-to-end.

    // ---------- additional coverage: getAudioUrl decrypt failure ----------

    @Test
    void getAudioUrl_returnsNull_whenDecryptThrows() {
        when(platformSongMappingMapper.selectOne(any()))
                .thenReturn(mapping(SONG_ID, PLATFORM, PLATFORM_SONG_ID));
        when(bindingService.getValidBinding(USER_ID, PLATFORM)).thenReturn(binding(PLATFORM));
        when(aesUtil.decrypt(anyString())).thenThrow(new RuntimeException("bad key"));

        assertNull(songService.getAudioUrl(USER_ID, SONG_ID));
        verify(musicApiClient, never()).getSongUrl(anyString(), anyString(), anyString());
    }

    // ---------- additional coverage: getAudioUrls handles duplicate IDs ----------

    @Test
    void getAudioUrls_handlesDuplicateSongIds_byTrustingMapperResult() {
        // Caller passes duplicates; service simply forwards to mapper.selectList.
        // We assert the service does not blow up and returns whatever mapping covers.
        PlatformSongMapping m = mapping(SONG_ID, PLATFORM, PLATFORM_SONG_ID);
        when(platformSongMappingMapper.selectList(any())).thenReturn(List.of(m));
        when(bindingService.getValidBinding(USER_ID, PLATFORM)).thenReturn(binding(PLATFORM));

        Map<String, String> urls = new HashMap<>();
        urls.put(PLATFORM_SONG_ID, "https://cdn/dup.mp3");
        when(musicApiClient.getSongUrls(eq(PLATFORM), anyList(), anyString())).thenReturn(urls);

        Map<Long, String> result = songService.getAudioUrls(USER_ID, Arrays.asList(SONG_ID, SONG_ID, SONG_ID));

        assertEquals(1, result.size());
        assertEquals("https://cdn/dup.mp3", result.get(SONG_ID));
    }
}
