package com.moodfm.service.song.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.moodfm.client.music.MusicApiClient;
import com.moodfm.client.music.SongApiClient;
import com.moodfm.common.util.AesUtil;
import com.moodfm.common.util.MusicResponseParser;
import com.moodfm.domain.entity.FeedbackEvent;
import com.moodfm.domain.entity.PlatformBinding;
import com.moodfm.domain.entity.PlatformSongMapping;
import com.moodfm.domain.entity.Song;
import com.moodfm.domain.vo.LyricLineVO;
import com.moodfm.domain.vo.SongVO;
import com.moodfm.mapper.FeedbackEventMapper;
import com.moodfm.mapper.PlatformSongMappingMapper;
import com.moodfm.mapper.SongMapper;
import com.moodfm.service.platform.PlatformBindingService;
import com.moodfm.service.song.SongService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.moodfm.common.exception.BizException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SongServiceImpl implements SongService {

    private final PlatformBindingService bindingService;
    private final SongApiClient songApiClient;
    private final MusicApiClient musicApiClient;
    private final FeedbackEventMapper feedbackEventMapper;
    private final PlatformSongMappingMapper platformSongMappingMapper;
    private final SongMapper songMapper;
    private final AesUtil aesUtil;

    @Override
    public List<SongVO> getLikedSongs(Long userId) {
        try {
            List<FeedbackEvent> likes = feedbackEventMapper.selectList(
                    new LambdaQueryWrapper<FeedbackEvent>()
                            .eq(FeedbackEvent::getUserId, userId)
                            .eq(FeedbackEvent::getEventType, "like")
                            .isNotNull(FeedbackEvent::getSongId)
                            .orderByDesc(FeedbackEvent::getCreatedAt));
            if (likes.isEmpty()) return List.of();

            List<Long> songIds = likes.stream()
                    .map(FeedbackEvent::getSongId)
                    .distinct()
                    .collect(Collectors.toList());

            List<Song> songs = songMapper.selectBatchIds(songIds);
            Map<Long, Song> songMap = songs.stream()
                    .collect(Collectors.toMap(Song::getId, s -> s));

            List<PlatformSongMapping> mappings = platformSongMappingMapper.selectList(
                    new LambdaQueryWrapper<PlatformSongMapping>()
                            .in(PlatformSongMapping::getSongId, songIds));
            Map<Long, PlatformSongMapping> mappingMap = mappings.stream()
                    .collect(Collectors.toMap(PlatformSongMapping::getSongId, m -> m, (a, b) -> a));

            return songIds.stream()
                    .map(songMap::get)
                    .filter(Objects::nonNull)
                    .map(song -> {
                        PlatformSongMapping mapping = mappingMap.get(song.getId());
                        return SongVO.builder()
                                .id(song.getId())
                                .title(song.getTitle())
                                .artist(song.getArtist())
                                .album(song.getAlbum())
                                .durationSeconds(song.getDurationSeconds())
                                .coverUrl(song.getCoverUrl())
                                .platform(mapping != null ? mapping.getPlatform() : null)
                                .platformSongId(mapping != null ? mapping.getPlatformSongId() : null)
                                .build();
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("getLikedSongs failed for user {}", userId, e);
            return List.of();
        }
    }

    @Override
    public boolean isLiked(Long userId, Long songId) {
        return feedbackEventMapper.selectOne(new LambdaQueryWrapper<FeedbackEvent>()
                .eq(FeedbackEvent::getUserId, userId)
                .eq(FeedbackEvent::getSongId, songId)
                .eq(FeedbackEvent::getEventType, "like")
                .last("LIMIT 1")) != null;
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
            // Feature 3: 同步取消红心到平台
            syncPlatformLike(userId, songId, false);
            return false;
        } else {
            FeedbackEvent evt = new FeedbackEvent();
            evt.setUserId(userId);
            evt.setSongId(songId);
            evt.setEventType("like");
            feedbackEventMapper.insert(evt);
            // Feature 3: 同步红心到平台
            syncPlatformLike(userId, songId, true);
            return true;
        }
    }

    /**
     * Feature 3: 将红心操作同步到音乐平台（优雅降级：失败不影响本地状态）
     */
    private void syncPlatformLike(Long userId, Long songId, boolean like) {
        try {
            // 通过 platform_song_mapping 查找平台歌曲 ID
            PlatformSongMapping mapping = platformSongMappingMapper.selectOne(
                    new LambdaQueryWrapper<PlatformSongMapping>()
                            .eq(PlatformSongMapping::getSongId, songId)
                            .last("LIMIT 1"));
            if (mapping == null) {
                log.debug("No platform mapping for song {}, skipping platform sync", songId);
                return;
            }

            // 获取用户的平台绑定 cookie
            PlatformBinding binding = bindingService.getDefaultBinding(userId);
            String cookie = aesUtil.decrypt(binding.getCookieEncrypted());

            boolean success = songApiClient.likeSong(
                    mapping.getPlatform(), mapping.getPlatformSongId(), like, cookie);
            if (success) {
                log.info("Platform like sync success: song {} platform={} like={}", songId, mapping.getPlatform(), like);
            } else {
                log.warn("Platform like sync failed (non-blocking): song {} platform={} like={}", songId, mapping.getPlatform(), like);
            }
        } catch (Exception e) {
            // 优雅降级：平台同步失败不影响本地红心状态
            log.warn("Platform like sync error (non-blocking): song {}", songId, e);
        }
    }

    @Override
    @Cacheable(value = "songs", key = "#userId + ':' + #songId")
    public SongVO getSongDetail(Long userId, Long songId) {
        try {
            PlatformBinding b = bindingService.getDefaultBinding(userId);
            String cookie = aesUtil.decrypt(b.getCookieEncrypted());
            JsonNode data = songApiClient.getSongDetail(b.getPlatform(), String.valueOf(songId), cookie);
            List<SongVO> songs = MusicResponseParser.parseSongs(data, b.getPlatform());
            if (!songs.isEmpty()) return songs.get(0);
        } catch (Exception e) {
            log.warn("getSongDetail failed for song {}", songId, e);
        }
        return SongVO.builder().id(songId).title("未知歌曲").artist("—").build();
    }

    @Override
    public List<SongVO> getSimilarSongs(Long userId, Long songId) {
        try {
            PlatformBinding b = bindingService.getDefaultBinding(userId);
            String cookie = aesUtil.decrypt(b.getCookieEncrypted());
            // Use platform-native simi/song endpoint (e.g. Netease /simi/song)
            JsonNode simiData = songApiClient.getSimilarSongs(b.getPlatform(), String.valueOf(songId), cookie);
            List<SongVO> simiSongs = MusicResponseParser.parseSongs(simiData, b.getPlatform());
            if (!simiSongs.isEmpty()) {
                return simiSongs.stream()
                        .filter(s -> !String.valueOf(songId).equals(s.getPlatformSongId()))
                        .limit(8)
                        .toList();
            }
            // Fallback: keyword search when simi endpoint returns nothing
            SongVO detail = getSongDetail(userId, songId);
            if ("未知歌曲".equals(detail.getTitle())) return List.of();
            String keywords = detail.getTitle() + (detail.getArtist() != null ? " " + detail.getArtist() : "");
            JsonNode searchData = songApiClient.searchSongs(b.getPlatform(), keywords.trim(), 10);
            return MusicResponseParser.parseSongs(searchData, b.getPlatform()).stream()
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

    @Override
    public String getAudioUrl(Long userId, Long songId) {
        PlatformSongMapping mapping = platformSongMappingMapper.selectOne(
                new LambdaQueryWrapper<PlatformSongMapping>()
                        .eq(PlatformSongMapping::getSongId, songId)
                        .last("LIMIT 1"));
        if (mapping == null) {
            log.warn("No platform mapping for song {}", songId);
            return null;
        }
        try {
            PlatformBinding binding;
            try {
                binding = bindingService.getValidBinding(userId, mapping.getPlatform());
            } catch (BizException e) {
                binding = bindingService.getDefaultBinding(userId);
            }
            String cookie = aesUtil.decrypt(binding.getCookieEncrypted());
            String url = musicApiClient.getSongUrl(mapping.getPlatform(), mapping.getPlatformSongId(), cookie);
            if (url == null || url.isBlank()) {
                log.warn("Empty audio URL for song {} platform={}", songId, mapping.getPlatform());
                return null;
            }
            return url;
        } catch (Exception e) {
            log.warn("getAudioUrl failed for song {} user {}", songId, userId, e);
            return null;
        }
    }

    @Override
    public Map<Long, String> getAudioUrls(Long userId, List<Long> songIds) {
        if (songIds == null || songIds.isEmpty()) return Map.of();

        List<PlatformSongMapping> mappings = platformSongMappingMapper.selectList(
                new LambdaQueryWrapper<PlatformSongMapping>()
                        .in(PlatformSongMapping::getSongId, songIds));
        if (mappings.isEmpty()) return Map.of();

        Map<String, List<PlatformSongMapping>> byPlatform = mappings.stream()
                .collect(Collectors.groupingBy(PlatformSongMapping::getPlatform));

        Map<Long, String> result = new HashMap<>();

        for (Map.Entry<String, List<PlatformSongMapping>> entry : byPlatform.entrySet()) {
            String platform = entry.getKey();
            List<PlatformSongMapping> platformMappings = entry.getValue();
            try {
                PlatformBinding binding;
                try {
                    binding = bindingService.getValidBinding(userId, platform);
                } catch (BizException e) {
                    binding = bindingService.getDefaultBinding(userId);
                }
                String cookie = aesUtil.decrypt(binding.getCookieEncrypted());

                List<String> platformIds = platformMappings.stream()
                        .map(PlatformSongMapping::getPlatformSongId)
                        .collect(Collectors.toList());
                Map<String, String> urlMap = musicApiClient.getSongUrls(platform, platformIds, cookie);

                for (PlatformSongMapping m : platformMappings) {
                    String url = urlMap.get(m.getPlatformSongId());
                    if (url != null && !url.isBlank()) {
                        result.put(m.getSongId(), url);
                    }
                }
            } catch (Exception e) {
                log.warn("getAudioUrls batch failed for platform {}", platform, e);
            }
        }
        return result;
    }

    // -- 解析工具 --

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
