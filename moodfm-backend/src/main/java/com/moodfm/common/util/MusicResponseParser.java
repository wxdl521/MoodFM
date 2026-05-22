package com.moodfm.common.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.moodfm.domain.vo.SongVO;

import java.util.ArrayList;
import java.util.List;

public final class MusicResponseParser {

    private MusicResponseParser() {}

    /**
     * Parses a song list from a music-adapter JSON response.
     * Tries several common paths to locate the song array.
     */
    public static List<SongVO> parseSongs(JsonNode data, String platform) {
        List<SongVO> songs = new ArrayList<>();
        JsonNode arr = data.path("songs");
        if (!arr.isArray()) arr = data.path("playlist").path("tracks");
        if (!arr.isArray()) arr = data.path("data").path("songs");
        if (!arr.isArray()) arr = data.path("result").path("songs");
        if (!arr.isArray()) return songs;

        if ("qqmusic".equalsIgnoreCase(platform)) {
            return parseQqMusicSongs(arr, platform);
        }

        for (JsonNode s : arr) {
            long id = s.path("id").asLong();
            if (id == 0) id = s.path("song").path("id").asLong();
            if (id == 0) continue;
            String artist = "";
            JsonNode ar = s.path("ar");
            if (ar.isArray() && !ar.isEmpty()) artist = ar.get(0).path("name").asText("");
            if (artist.isEmpty()) artist = s.path("artists").path(0).path("name").asText("");
            if (artist.isEmpty()) artist = s.path("song").path("artists").path(0).path("name").asText("");
            String cover = s.path("al").path("picUrl").asText(
                           s.path("album").path("picUrl").asText(
                           s.path("song").path("album").path("picUrl").asText("")));
            String album  = s.path("al").path("name").asText(
                            s.path("album").path("name").asText(
                            s.path("song").path("album").path("name").asText("")));
            int dur = s.path("dt").asInt(s.path("duration").asInt(0)) / 1000;
            String title = s.path("name").asText(s.path("song").path("name").asText(""));
            songs.add(SongVO.builder()
                    .id(id)
                    .title(title)
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

    private static List<SongVO> parseQqMusicSongs(JsonNode arr, String platform) {
        List<SongVO> songs = new ArrayList<>();
        for (JsonNode s : arr) {
            String songmid = s.path("songmid").asText("");
            if (songmid.isEmpty()) songmid = s.path("mid").asText("");
            if (songmid.isEmpty()) continue;

            long id = s.path("id").asLong(0);
            String title = s.path("songname").asText(s.path("title").asText(""));

            StringBuilder artistBuilder = new StringBuilder();
            JsonNode singers = s.path("singer");
            if (singers.isArray()) {
                for (JsonNode singer : singers) {
                    if (artistBuilder.length() > 0) artistBuilder.append("/");
                    artistBuilder.append(singer.path("name").asText(""));
                }
            }

            String album = s.path("album").path("name").asText("");
            if (album.isEmpty()) album = s.path("albumname").asText("");
            String albumMid = s.path("album").path("mid").asText("");
            if (albumMid.isEmpty()) albumMid = s.path("albummid").asText("");
            String cover = albumMid.isEmpty() ? "" :
                    "https://y.qq.com/music/photo_new/T002R300x300M000" + albumMid + ".jpg";

            int dur = s.path("interval").asInt(0);

            songs.add(SongVO.builder()
                    .id(id)
                    .title(title)
                    .artist(artistBuilder.toString())
                    .album(album)
                    .durationSeconds(dur)
                    .coverUrl(cover)
                    .platform(platform)
                    .platformSongId(songmid)
                    .build());
        }
        return songs;
    }
}
