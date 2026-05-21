package com.moodfm.service.search.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.moodfm.client.music.MusicApiClient;
import com.moodfm.common.util.AesUtil;
import com.moodfm.common.util.MusicResponseParser;
import com.moodfm.domain.entity.PlatformBinding;
import com.moodfm.domain.entity.Song;
import com.moodfm.domain.vo.SearchResultVO;
import com.moodfm.domain.vo.SongVO;
import com.moodfm.mapper.PlatformBindingMapper;
import com.moodfm.mapper.SongMapper;
import com.moodfm.service.embedding.EmbeddingService;
import com.moodfm.service.search.SearchService;
import com.moodfm.service.vector.QdrantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final PlatformBindingMapper platformBindingMapper;
    private final SongMapper songMapper;
    private final MusicApiClient musicApiClient;
    private final EmbeddingService embeddingService;
    private final QdrantService qdrantService;
    private final AesUtil aesUtil;

    private static final int MAX_LIMIT = 50;

    @Override
    public SearchResultVO search(Long userId, String query, String mode, int limit) {
        int capped = Math.min(limit, MAX_LIMIT);
        if ("mood".equalsIgnoreCase(mode)) {
            return moodSearch(userId, query, capped);
        }
        return keywordSearch(userId, query, capped);
    }

    // ------------------------------------------------------------------ //
    //  Keyword search                                                      //
    // ------------------------------------------------------------------ //

    private SearchResultVO keywordSearch(Long userId, String query, int limit) {
        List<PlatformBinding> bindings = platformBindingMapper.selectList(
                new LambdaQueryWrapper<PlatformBinding>()
                        .eq(PlatformBinding::getUserId, userId)
                        .eq(PlatformBinding::getIsValid, 1));

        if (bindings == null || bindings.isEmpty()) {
            return SearchResultVO.builder()
                    .mode("keyword")
                    .query(query)
                    .songs(List.of())
                    .notice("请先在「平台绑定」中接入音乐平台")
                    .build();
        }

        // Dedup by "platform:platformSongId"
        Map<String, SongVO> dedupMap = new LinkedHashMap<>();

        for (PlatformBinding binding : bindings) {
            try {
                String cookie = aesUtil.decrypt(binding.getCookieEncrypted());
                JsonNode raw = musicApiClient.searchSongs(binding.getPlatform(), query, limit, cookie);
                List<SongVO> parsed = MusicResponseParser.parseSongs(raw, binding.getPlatform());
                if (parsed != null) {
                    for (SongVO song : parsed) {
                        String key = buildKey(song);
                        dedupMap.putIfAbsent(key, song);
                    }
                }
            } catch (Exception e) {
                log.warn("Keyword search failed for platform {} (userId={}): {}",
                        binding.getPlatform(), userId, e.getMessage());
            }
        }

        return SearchResultVO.builder()
                .mode("keyword")
                .query(query)
                .songs(new ArrayList<>(dedupMap.values()))
                .build();
    }

    // ------------------------------------------------------------------ //
    //  Mood search                                                         //
    // ------------------------------------------------------------------ //

    private SearchResultVO moodSearch(Long userId, String query, int limit) {
        List<SongVO> songs = new ArrayList<>();

        try {
            float[] vector = embeddingService.embed(query);
            List<Long> songIds = qdrantService.searchSimilar(vector, limit);

            if (!songIds.isEmpty()) {
                // 批量查询，单次 SELECT ... WHERE id IN (...)
                List<Song> dbSongs = songMapper.selectBatchIds(songIds);
                // 保持向量搜索的排序顺序
                Map<Long, Song> songMap = dbSongs.stream()
                        .collect(Collectors.toMap(Song::getId, s -> s));
                for (Long songId : songIds) {
                    Song song = songMap.get(songId);
                    if (song != null) songs.add(toVO(song));
                }
            }
        } catch (Exception e) {
            log.warn("Mood search vector recall failed (userId={}, query='{}'): {}",
                    userId, query, e.getMessage());
        }

        // Supplement with keyword search when vector results are insufficient
        if (songs.size() < 5) {
            try {
                SearchResultVO kwResult = keywordSearch(userId, query, limit);
                if (kwResult.getSongs() != null) {
                    // Build set of already-present platformSongIds for dedup
                    Map<String, SongVO> dedupMap = new LinkedHashMap<>();
                    for (SongVO s : songs) {
                        String key = buildKey(s);
                        dedupMap.put(key, s);
                    }
                    for (SongVO s : kwResult.getSongs()) {
                        String key = buildKey(s);
                        dedupMap.putIfAbsent(key, s);
                    }
                    songs = new ArrayList<>(dedupMap.values());
                }
            } catch (Exception e) {
                log.warn("Mood search keyword fallback failed (userId={}): {}", userId, e.getMessage());
            }
        }

        return SearchResultVO.builder()
                .mode("mood")
                .query(query)
                .songs(songs)
                .build();
    }

    // ------------------------------------------------------------------ //
    //  Entity → VO conversion                                             //
    // ------------------------------------------------------------------ //

    private SongVO toVO(Song song) {
        return SongVO.builder()
                .id(song.getId())
                .title(song.getTitle())
                .artist(song.getArtist())
                .album(song.getAlbum())
                .durationSeconds(song.getDurationSeconds())
                .coverUrl(song.getCoverUrl())
                // Song entity does not carry platform/platformSongId;
                // those live in PlatformSongMapping. Left null here.
                .platform(null)
                .platformSongId(null)
                .build();
    }

    // ------------------------------------------------------------------ //
    //  Helpers                                                             //
    // ------------------------------------------------------------------ //

    /** Builds a dedup key from a SongVO. Falls back to id if platform info absent. */
    private String buildKey(SongVO song) {
        if (song.getPlatform() != null && song.getPlatformSongId() != null) {
            return song.getPlatform() + ":" + song.getPlatformSongId();
        }
        // For songs from DB that have no platform binding attached, use the DB id
        return "db:" + song.getId();
    }
}
