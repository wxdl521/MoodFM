package com.moodfm.service.playlist;

import com.moodfm.mapper.PlayRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmartPlaylistService {

    private final PlayRecordMapper playRecordMapper;

    private static final String TYPE_WEEKLY_LOVES   = "weekly-loves";
    private static final String TYPE_LATE_NIGHT     = "late-night";
    private static final String TYPE_ENERGY         = "energy";
    private static final String TYPE_DISCOVERIES    = "discoveries";

    /**
     * Generate all smart playlists for the given user.
     */
    public List<Map<String, Object>> generateSmartPlaylists(Long userId) {
        List<Map<String, Object>> playlists = new ArrayList<>();

        playlists.add(buildPlaylist(
                TYPE_WEEKLY_LOVES, "本周红心", "heart",
                playRecordMapper.selectWeeklyLoves(userId)));
        playlists.add(buildPlaylist(
                TYPE_LATE_NIGHT, "深夜最爱", "moon",
                playRecordMapper.selectLateNightFavorites(userId)));
        playlists.add(buildPlaylist(
                TYPE_ENERGY, "高能时刻", "zap",
                playRecordMapper.selectHighEnergySceneSongs(userId)));
        playlists.add(buildPlaylist(
                TYPE_DISCOVERIES, "新发现", "compass",
                playRecordMapper.selectRecentDiscoveries(userId)));

        return playlists;
    }

    /**
     * Get songs for a specific smart playlist type.
     */
    public Map<String, Object> getSmartPlaylist(Long userId, String type) {
        List<Map<String, Object>> songs;
        String name;
        String icon;

        switch (type) {
            case TYPE_WEEKLY_LOVES -> {
                songs = playRecordMapper.selectWeeklyLoves(userId);
                name = "本周红心";
                icon = "heart";
            }
            case TYPE_LATE_NIGHT -> {
                songs = playRecordMapper.selectLateNightFavorites(userId);
                name = "深夜最爱";
                icon = "moon";
            }
            case TYPE_ENERGY -> {
                songs = playRecordMapper.selectHighEnergySceneSongs(userId);
                name = "高能时刻";
                icon = "zap";
            }
            case TYPE_DISCOVERIES -> {
                songs = playRecordMapper.selectRecentDiscoveries(userId);
                name = "新发现";
                icon = "compass";
            }
            default -> {
                return Map.of("error", "Unknown smart playlist type: " + type);
            }
        }

        return Map.of(
                "type", type,
                "name", name,
                "icon", icon,
                "songCount", songs.size(),
                "songs", songs
        );
    }

    // ── helpers ─────────────────────────────────────────────────────

    private Map<String, Object> buildPlaylist(String type, String name,
                                              String icon,
                                              List<Map<String, Object>> songs) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("name", name);
        m.put("icon", icon);
        m.put("songCount", songs.size());
        m.put("songs", songs);
        return m;
    }
}
