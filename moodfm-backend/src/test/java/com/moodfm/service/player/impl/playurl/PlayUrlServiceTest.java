package com.moodfm.service.player.impl.playurl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodfm.client.music.MusicApiClient;
import com.moodfm.common.util.AesUtil;
import com.moodfm.domain.entity.PlatformBinding;
import com.moodfm.domain.entity.PlatformSongMapping;
import com.moodfm.domain.entity.Song;
import com.moodfm.domain.vo.SongVO;
import com.moodfm.mapper.PlatformSongMappingMapper;
import com.moodfm.mapper.SongMapper;
import com.moodfm.service.platform.PlatformBindingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlayUrlServiceTest {

    @Mock private MusicApiClient musicApiClient;
    @Mock private PlatformBindingService platformBindingService;
    @Mock private AesUtil aesUtil;
    @Mock private PlatformSongMappingMapper platformSongMappingMapper;
    @Mock private SongMapper songMapper;

    @InjectMocks private PlayUrlService playUrlService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void enrichWithPlayUrls_setsPlatformUrl() throws Exception {
        SongVO song = SongVO.builder().platformSongId("001").title("Test").artist("A").build();
        when(musicApiClient.getSongUrls(eq("netease"), anyList(), eq("cookie")))
                .thenReturn(Map.of("001", "https://cdn.example.com/001.mp3"));

        playUrlService.enrichWithPlayUrls(List.of(song), "netease", "cookie");

        assertEquals("https://cdn.example.com/001.mp3", song.getPlayUrl());
        assertEquals("netease", song.getUrlSource());
    }

    @Test
    void enrichWithPlayUrls_qqMissingUrl_fallsBackToNetease() throws Exception {
        SongVO song = SongVO.builder()
                .platformSongId("qq001")
                .title("QQ Song")
                .artist("Artist")
                .build();

        when(musicApiClient.getSongUrls(eq("qqmusic"), anyList(), eq("qq-cookie")))
                .thenReturn(Map.of());

        var searchNode = objectMapper.readTree("""
                {"code":200,"songs":[{"id":123456,"name":"QQ Song","ar":[{"name":"Artist"}]}]}
                """);
        when(musicApiClient.searchSongs(eq("netease"), contains("QQ Song"), eq(5), isNull()))
                .thenReturn(searchNode);
        when(musicApiClient.getSongUrl(eq("netease"), eq("123456"), isNull()))
                .thenReturn("https://cdn.example.com/ne001.mp3");

        playUrlService.enrichWithPlayUrls(List.of(song), "qqmusic", "qq-cookie");

        assertEquals("https://cdn.example.com/ne001.mp3", song.getPlayUrl());
        assertEquals("netease_fallback", song.getUrlSource());
    }

    @Test
    void getSongUrl_qqPrimaryFails_usesNeteaseFallback() throws Exception {
        PlatformBinding binding = new PlatformBinding();
        binding.setCookieEncrypted("enc");
        when(platformBindingService.getValidBinding(1L, "qqmusic")).thenReturn(binding);
        when(aesUtil.decrypt("enc")).thenReturn("qq-cookie");
        when(musicApiClient.getSongUrl(eq("qqmusic"), eq("qq001"), eq("qq-cookie"))).thenReturn("");

        PlatformSongMapping mapping = new PlatformSongMapping();
        mapping.setSongId(10L);
        when(platformSongMappingMapper.selectOne(any())).thenReturn(mapping);

        Song dbSong = new Song();
        dbSong.setTitle("Fallback Title");
        dbSong.setArtist("Fallback Artist");
        when(songMapper.selectById(10L)).thenReturn(dbSong);

        var searchNode = objectMapper.readTree("""
                {"code":200,"songs":[{"id":999,"name":"Fallback Title","ar":[{"name":"Fallback Artist"}]}]}
                """);
        when(musicApiClient.searchSongs(eq("netease"), anyString(), eq(5), isNull())).thenReturn(searchNode);
        when(musicApiClient.getSongUrl(eq("netease"), eq("999"), isNull()))
                .thenReturn("https://cdn.example.com/ne99.mp3");

        String url = playUrlService.getSongUrl(1L, "qqmusic", "qq001");

        assertEquals("https://cdn.example.com/ne99.mp3", url);
    }
}