package com.moodfm.service.playlist.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.moodfm.client.music.MusicApiClient;
import com.moodfm.common.util.AesUtil;
import com.moodfm.domain.entity.PlatformBinding;
import com.moodfm.domain.vo.PlaylistVO;
import com.moodfm.domain.vo.SongVO;
import com.moodfm.service.platform.PlatformBindingService;
import com.moodfm.service.playlist.PlaylistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaylistServiceImpl implements PlaylistService {

    private final PlatformBindingService bindingService;
    private final MusicApiClient musicApiClient;
    private final AesUtil aesUtil;

    @Override
    public List<PlaylistVO> listPlaylists(Long userId) {
        List<PlatformBinding> bindings = getValidBindings(userId);
        List<PlaylistVO> result = new ArrayList<>();
        for (PlatformBinding b : bindings) {
            try {
                String cookie = aesUtil.decrypt(b.getCookieEncrypted());
                JsonNode data = musicApiClient.getUserPlaylists(b.getPlatform(), cookie);
                result.addAll(parsePlaylists(data, b.getPlatform()));
            } catch (Exception e) {
                log.warn("Failed to load playlists for platform {}", b.getPlatform(), e);
            }
        }
        return result;
    }

    @Override
    public PlaylistVO getPlaylist(Long userId, String compositeId) {
        // compositeId 格式: "{platform}:{platformPlaylistId}"
        String[] parts = compositeId.split(":", 2);
        if (parts.length != 2) return PlaylistVO.builder().id(compositeId).name("未知歌单").trackCount(0).tracks(List.of()).build();
        String platform = parts[0];
        String platformId = parts[1];

        try {
            PlatformBinding b = bindingService.getValidBinding(userId, platform);
            String cookie = aesUtil.decrypt(b.getCookieEncrypted());
            JsonNode data = musicApiClient.getPlaylistTracks(platform, platformId, cookie);
            List<SongVO> tracks = parseSongs(data, platform);
            // 尝试从 list 接口获取元信息
            String name = data.path("playlist").path("name").asText(
                          data.path("name").asText("歌单"));
            String desc = data.path("playlist").path("description").asText(
                          data.path("description").asText(""));
            String cover = data.path("playlist").path("coverImgUrl").asText(
                           data.path("coverImgUrl").asText(""));
            return PlaylistVO.builder()
                    .id(compositeId)
                    .name(name)
                    .description(desc)
                    .coverUrl(cover)
                    .platform(platform)
                    .trackCount(tracks.size())
                    .tracks(tracks)
                    .build();
        } catch (Exception e) {
            log.warn("Failed to get playlist tracks for {}", compositeId, e);
            return PlaylistVO.builder().id(compositeId).name("歌单").trackCount(0).tracks(List.of()).build();
        }
    }

    // ── 私有解析方法 ──────────────────────────────────────────────────

    private List<PlaylistVO> parsePlaylists(JsonNode data, String platform) {
        List<PlaylistVO> list = new ArrayList<>();
        // 适配器通常返回 { playlist: [...] } 或 { data: { playlist: [...] } }
        JsonNode arr = data.path("playlist");
        if (!arr.isArray()) arr = data.path("data").path("playlist");
        if (!arr.isArray()) return list;

        for (JsonNode pl : arr) {
            long id = pl.path("id").asLong();
            if (id == 0) continue;
            list.add(PlaylistVO.builder()
                    .id(platform + ":" + id)
                    .name(pl.path("name").asText(""))
                    .description(pl.path("description").asText(""))
                    .coverUrl(pl.path("coverImgUrl").asText(pl.path("picUrl").asText("")))
                    .platform(platform)
                    .trackCount(pl.path("trackCount").asInt(0))
                    .build());
        }
        return list;
    }

    private List<SongVO> parseSongs(JsonNode data, String platform) {
        List<SongVO> songs = new ArrayList<>();
        JsonNode arr = data.path("songs");
        if (!arr.isArray()) arr = data.path("playlist").path("tracks");
        if (!arr.isArray()) arr = data.path("data").path("songs");
        if (!arr.isArray()) return songs;

        for (JsonNode s : arr) {
            long id = s.path("id").asLong();
            if (id == 0) continue;
            String artist = "";
            JsonNode ar = s.path("ar");
            if (ar.isArray() && !ar.isEmpty()) artist = ar.get(0).path("name").asText("");
            if (artist.isEmpty()) artist = s.path("artists").path(0).path("name").asText("");
            String cover = s.path("al").path("picUrl").asText(s.path("album").path("picUrl").asText(""));
            String album  = s.path("al").path("name").asText(s.path("album").path("name").asText(""));
            int dur = s.path("dt").asInt(s.path("duration").asInt(0)) / 1000;
            songs.add(SongVO.builder()
                    .id(id)
                    .title(s.path("name").asText(""))
                    .artist(artist)
                    .album(album)
                    .durationSeconds(dur)
                    .coverUrl(cover)
                    .platform(platform)
                    .platformSongId(String.valueOf(id))
                    .build());
        }
        return songs;
    }

    private List<PlatformBinding> getValidBindings(Long userId) {
        // 复用 listBindings 但需要原始 entity，用 mapper 直接查
        // 实际注入 PlatformBindingMapper 更合适，这里通过 bindingService 获取默认绑定
        try {
            PlatformBinding b = bindingService.getDefaultBinding(userId);
            return List.of(b);
        } catch (Exception e) {
            return List.of();
        }
    }
}
