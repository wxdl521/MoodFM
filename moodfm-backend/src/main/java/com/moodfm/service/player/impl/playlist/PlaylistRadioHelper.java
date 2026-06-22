package com.moodfm.service.player.impl.playlist;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodfm.ai.model.MoodParams;
import com.moodfm.domain.entity.Song;
import com.moodfm.domain.vo.PlaylistVO;
import com.moodfm.domain.vo.SongVO;
import com.moodfm.mapper.SongMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Playlist-seeded radio helpers extracted from {@code PlayerServiceImpl} (T3 post-review).
 */
@Component
@RequiredArgsConstructor
public class PlaylistRadioHelper {

    private final SongMapper songMapper;
    private final ObjectMapper objectMapper;

    public MoodParams buildMoodParamsFromPlaylist(PlaylistVO playlist, List<SongVO> tracks) {
        MoodParams moodParams = MoodParams.defaultParams();
        moodParams.setSceneInferred("playlist");

        LinkedHashSet<String> artists = new LinkedHashSet<>();
        LinkedHashSet<String> genres = new LinkedHashSet<>();
        double valenceSum = 0;
        double energySum = 0;
        int featureCount = 0;

        int limit = Math.min(tracks.size(), 15);
        for (int i = 0; i < limit; i++) {
            SongVO track = tracks.get(i);
            if (track.getArtist() != null && !track.getArtist().isBlank()) {
                artists.add(track.getArtist().trim());
            }
            if (track.getId() != null) {
                Song dbSong = songMapper.selectById(track.getId());
                if (dbSong != null && dbSong.getFeatures() != null && !dbSong.getFeatures().isBlank()) {
                    try {
                        JsonNode features = objectMapper.readTree(dbSong.getFeatures());
                        String genre = features.path("genre").asText(null);
                        if (genre != null && !genre.isBlank()) genres.add(genre);
                        if (features.has("valence")) {
                            valenceSum += features.path("valence").asDouble(0.5);
                            energySum += features.path("energy").asDouble(0.5);
                            featureCount++;
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        if (genres.isEmpty() && playlist.getName() != null && !playlist.getName().isBlank()) {
            genres.add(playlist.getName().trim());
        }
        if (genres.isEmpty()) genres.add("歌单");

        moodParams.setVibeKeywords(artists.stream().limit(6).collect(Collectors.toList()));
        moodParams.setPreferredGenres(genres.stream().limit(5).collect(Collectors.toList()));

        if (featureCount > 0 && moodParams.getMood() != null) {
            moodParams.getMood().setValence(valenceSum / featureCount);
            moodParams.getMood().setEnergy(energySum / featureCount);
        }

        return moodParams;
    }

    public List<SongVO> mergePlaylistWithRecalled(List<SongVO> playlistTracks, List<SongVO> recalled) {
        LinkedHashMap<String, SongVO> byKey = new LinkedHashMap<>();
        for (SongVO track : playlistTracks) {
            String key = songDedupeKey(track);
            if (key != null) byKey.putIfAbsent(key, track);
        }
        for (SongVO song : recalled) {
            String key = songDedupeKey(song);
            if (key != null) byKey.putIfAbsent(key, song);
        }
        return new ArrayList<>(byKey.values());
    }

    private String songDedupeKey(SongVO song) {
        if (song.getPlatformSongId() != null && !song.getPlatformSongId().isBlank()) {
            String p = song.getPlatform() != null ? song.getPlatform() : "";
            return p + ":" + song.getPlatformSongId();
        }
        if (song.getTitle() != null && song.getArtist() != null) {
            return song.getTitle() + "|" + song.getArtist();
        }
        return null;
    }
}