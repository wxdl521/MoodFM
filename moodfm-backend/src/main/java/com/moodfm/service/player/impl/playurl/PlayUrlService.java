package com.moodfm.service.player.impl.playurl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moodfm.client.music.MusicApiClient;
import com.moodfm.common.exception.BizException;
import com.moodfm.common.result.ResultCode;
import com.moodfm.common.util.AesUtil;
import com.moodfm.common.util.MusicResponseParser;
import com.moodfm.domain.entity.PlatformBinding;
import com.moodfm.domain.entity.PlatformSongMapping;
import com.moodfm.domain.entity.Song;
import com.moodfm.domain.vo.SongVO;
import com.moodfm.mapper.PlatformSongMappingMapper;
import com.moodfm.mapper.SongMapper;
import com.moodfm.service.platform.PlatformBindingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class PlayUrlService {

    private final MusicApiClient musicApiClient;
    private final PlatformBindingService platformBindingService;
    private final AesUtil aesUtil;
    private final PlatformSongMappingMapper platformSongMappingMapper;
    private final SongMapper songMapper;

    public String getSongUrl(Long userId, String platform, String songId) {
        PlatformBinding binding = platformBindingService.getValidBinding(userId, platform);
        String cookie = aesUtil.decrypt(binding.getCookieEncrypted());
        String url = musicApiClient.getSongUrl(platform, songId, cookie);
        if (url != null && !url.isBlank()) return url;

        // Fallback: look up the song in DB and try Netease
        if ("qqmusic".equals(platform)) {
            url = fallbackToNetease(songId, null, null, null);
        }
        if (url == null || url.isBlank()) throw new BizException(ResultCode.RECALL_FAILED, "获取播放地址失败");
        return url;
    }

    /**
     * Try to get a play URL from Netease by searching the song title + artist.
     * Returns null if the fallback also fails.
     */
    private String fallbackToNetease(String qqSongId, String title, String artist, String cookie) {
        try {
            // If title/artist not provided, look up from DB via platform mapping
            if ((title == null || title.isBlank()) && qqSongId != null) {
                PlatformSongMapping mapping = platformSongMappingMapper.selectOne(
                        new LambdaQueryWrapper<PlatformSongMapping>()
                                .eq(PlatformSongMapping::getPlatformSongId, qqSongId)
                                .eq(PlatformSongMapping::getPlatform, "qqmusic")
                                .last("LIMIT 1"));
                if (mapping != null) {
                    Song song = songMapper.selectById(mapping.getSongId());
                    if (song != null) {
                        title = song.getTitle();
                        artist = song.getArtist();
                    }
                }
            }
            if (title == null || title.isBlank()) return null;

            String query = title + (artist != null && !artist.isBlank() ? " " + artist : "");
            List<SongVO> hits = fetchSearch("netease", query, 5);
            if (hits.isEmpty()) return null;

            String neteaseId = hits.get(0).getPlatformSongId();
            if (neteaseId == null) return null;

            return musicApiClient.getSongUrl("netease", neteaseId, null);
        } catch (Exception e) {
            log.warn("Netease fallback failed for QQ song {}", qqSongId, e);
            return null;
        }
    }

    /**
     * Batch-fetch play URLs from the music adapter and set them on SongVOs.
     * Uses a single API call with comma-separated IDs to avoid N+1 requests.
     * For QQ Music, songs without a URL are retried via Netease fallback.
     */
    public void enrichWithPlayUrls(List<SongVO> songs, String platform, String cookie) {
        if (songs == null || songs.isEmpty()) return;
        List<String> ids = songs.stream()
                .map(SongVO::getPlatformSongId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .collect(Collectors.toList());
        if (ids.isEmpty()) return;
        try {
            Map<String, String> urlMap = musicApiClient.getSongUrls(platform, ids, cookie);
            int enriched = 0;
            for (SongVO song : songs) {
                String url = urlMap.get(song.getPlatformSongId());
                if (url != null && !url.isBlank()) {
                    song.setPlayUrl(url);
                    song.setUrlSource(platform);
                    enriched++;
                }
            }
            log.info("Enriched {}/{} songs with play URLs from {}", enriched, songs.size(), platform);

            // For QQ Music: try Netease fallback for songs that still have no URL
            if ("qqmusic".equals(platform)) {
                List<SongVO> missing = songs.stream()
                        .filter(s -> s.getPlayUrl() == null || s.getPlayUrl().isBlank())
                        .collect(Collectors.toList());
                if (!missing.isEmpty()) {
                    fillFallbackUrls(missing);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to enrich songs with play URLs, continuing without them", e);
        }
    }

    private void fillFallbackUrls(List<SongVO> songs) {
        int fallbackCount = 0;
        int failedCount = 0;
        for (SongVO song : songs) {
            try {
                String neteaseUrl = fallbackToNetease(
                        song.getPlatformSongId(), song.getTitle(), song.getArtist(), null);
                if (neteaseUrl != null && !neteaseUrl.isBlank()) {
                    song.setPlayUrl(neteaseUrl);
                    song.setUrlSource("netease_fallback");
                    fallbackCount++;
                } else {
                    failedCount++;
                    log.debug("Netease fallback returned null for: {} - {}", song.getTitle(), song.getArtist());
                }
            } catch (Exception e) {
                failedCount++;
                log.debug("Netease fallback error for {}: {}", song.getTitle(), e.getMessage());
            }
        }
        if (fallbackCount > 0 || failedCount > 0) {
            log.info("Netease fallback: {}/{} QQ Music songs got URLs ({} failed)",
                    fallbackCount, songs.size(), failedCount);
        }
    }

    // Deliberate temporary duplication: copied verbatim from PlayerServiceImpl.fetchSearch.
    // fetchSearch is still needed by recall in PlayerServiceImpl (Task 5 will consolidate).
    private List<SongVO> fetchSearch(String platform, String keywords, int limit) {
        try { return MusicResponseParser.parseSongs(musicApiClient.searchSongs(platform, keywords, limit, null), platform); }
        catch (Exception e) { log.warn("fetchSearch failed: {}", keywords, e); return List.of(); }
    }
}
