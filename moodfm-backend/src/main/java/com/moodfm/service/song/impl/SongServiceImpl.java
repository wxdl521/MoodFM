package com.moodfm.service.song.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.moodfm.client.music.SongApiClient;
import com.moodfm.common.util.AesUtil;
import com.moodfm.domain.entity.FeedbackEvent;
import com.moodfm.domain.entity.PlatformBinding;
import com.moodfm.domain.vo.LyricLineVO;
import com.moodfm.domain.vo.SongVO;
import com.moodfm.mapper.FeedbackEventMapper;
import com.moodfm.service.platform.PlatformBindingService;
import com.moodfm.service.song.SongService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class SongServiceImpl implements SongService {

    private final PlatformBindingService bindingService;
    private final SongApiClient songApiClient;
    private final FeedbackEventMapper feedbackEventMapper;
    private final AesUtil aesUtil;

    @Override
    public List<SongVO> getLikedSongs(Long userId) {
        try {
            PlatformBinding b = bindingService.getDefaultBinding(userId);
            String cookie = aesUtil.decrypt(b.getCookieEncrypted());
            JsonNode data = songApiClient.getUserLikedSongs(b.getPlatform(), cookie);
            return parseSongs(data, b.getPlatform());
        } catch (Exception e) {
            log.warn("getLikedSongs failed for user {}", userId, e);
            return List.of();
        }
    }

    @Override
    public boolean toggleLike(Long userId, Long songId) {
        FeedbackEvent existing = feedbackEventMapper.selectOne(new LambdaQueryWrapper<FeedbackEvent>()
                .eq(FeedbackEvent::getUserId, userId)
                .eq(FeedbackEvent::getSongId, songId)
                .eq(FeedbackEvent::getEventType, "like")
                .last("LIMIT 1"));
        if (existing != null) {
            feedbackEventMapper.deleteById(existing.getId());
            return false;
        } else {
            FeedbackEvent evt = new FeedbackEvent();
            evt.setUserId(userId);
            evt.setSongId(songId);
            evt.setEventType("like");
            feedbackEventMapper.insert(evt);
            return true;
        }
    }

    @Override
    public SongVO getSongDetail(Long userId, Long songId) {
        try {
            PlatformBinding b = bindingService.getDefaultBinding(userId);
            String cookie = aesUtil.decrypt(b.getCookieEncrypted());
            JsonNode data = songApiClient.getSongDetail(b.getPlatform(), String.valueOf(songId), cookie);
            List<SongVO> songs = parseSongs(data, b.getPlatform());
            if (!songs.isEmpty()) return songs.get(0);
        } catch (Exception e) {
            log.warn("getSongDetail failed for song {}", songId, e);
        }
        return SongVO.builder().id(songId).title("未知歌曲").artist("—").build();
    }

    @Override
    public List<SongVO> getSimilarSongs(Long userId, Long songId) {
        try {
            SongVO detail = getSongDetail(userId, songId);
            if ("未知歌曲".equals(detail.getTitle())) return List.of();
            PlatformBinding b = bindingService.getDefaultBinding(userId);
            String cookie = aesUtil.decrypt(b.getCookieEncrypted());
            String keywords = detail.getTitle() + " " + (detail.getArtist() != null ? detail.getArtist() : "");
            JsonNode data = songApiClient.searchSongs(b.getPlatform(), keywords.trim(), 10);
            return parseSongs(data, b.getPlatform()).stream()
                    .filter(s -> !String.valueOf(songId).equals(s.getPlatformSongId()))
                    .limit(8)
                    .toList();
        } catch (Exception e) {
            log.warn("getSimilarSongs failed for song {}", songId, e);
            return List.of();
        }
    }

    @Override
    public List<LyricLineVO> getLyrics(Long userId, Long songId) {
        try {
            PlatformBinding b = bindingService.getDefaultBinding(userId);
            String cookie = aesUtil.decrypt(b.getCookieEncrypted());
            JsonNode data = songApiClient.getLyrics(b.getPlatform(), String.valueOf(songId), cookie);
            String lrc = data.path("lrc").path("lyric").asText(data.path("lyric").asText(""));
            return parseLrc(lrc);
        } catch (Exception e) {
            log.warn("getLyrics failed for song {}", songId, e);
            return List.of();
        }
    }

    // ── 解析工具 ──────────────────────────────────────────────────────

    private List<SongVO> parseSongs(JsonNode data, String platform) {
        List<SongVO> songs = new ArrayList<>();
        JsonNode arr = data.path("songs");
        if (!arr.isArray()) arr = data.path("data").path("songs");
        if (!arr.isArray()) arr = data.path("result").path("songs");
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

    private static final Pattern LRC_LINE = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})](.*)");

    private List<LyricLineVO> parseLrc(String lrc) {
        List<LyricLineVO> lines = new ArrayList<>();
        if (lrc == null || lrc.isBlank()) return lines;
        for (String line : lrc.split("\n")) {
            Matcher m = LRC_LINE.matcher(line.trim());
            if (!m.matches()) continue;
            long min = Long.parseLong(m.group(1));
            long sec = Long.parseLong(m.group(2));
            String msStr = m.group(3);
            long ms = Long.parseLong(msStr.length() == 2 ? msStr + "0" : msStr);
            long time = min * 60_000 + sec * 1000 + ms;
            String text = m.group(4).trim();
            if (!text.isEmpty()) {
                lines.add(LyricLineVO.builder().time(time).text(text).build());
            }
        }
        return lines;
    }
}
