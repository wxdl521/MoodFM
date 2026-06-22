package com.moodfm.service.player.impl.recall.filter;

import com.moodfm.domain.entity.GlobalBlacklist;
import com.moodfm.domain.vo.SongVO;
import com.moodfm.mapper.GlobalBlacklistMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Feature 4c: 全局黑名单过滤。Pipeline stage 3 ({@code @Order(30)}).
 * <p>
 * 从 global_blacklist 表加载管理员设置的黑名单，过滤掉命中的歌曲。
 * 支持三种类型：artist（精确艺术家名）、song（精确歌曲标题）、keyword（子串匹配）。
 * 对所有用户生效，无需 Redis 缓存（表数据量小，type 列有索引）。
 * <p>
 * 对所有用户生效，忽略 userId 参数。
 * 方法体逐字搬迁自 {@code CandidateRecallService.filterGlobalBlacklist}（T3-2 Task 2）。
 */
@Slf4j
@Component
@Order(30)
@RequiredArgsConstructor
public class GlobalBlacklistFilter implements CandidateFilter {

    private final GlobalBlacklistMapper globalBlacklistMapper;

    @Override
    public List<SongVO> filter(Long userId, List<SongVO> candidates) {
        try {
            List<GlobalBlacklist> blacklist = globalBlacklistMapper.selectList(null);
            if (blacklist.isEmpty()) return candidates;

            Set<String> bannedArtists  = new HashSet<>();
            Set<String> bannedSongs    = new HashSet<>();
            List<String> bannedKeywords = new ArrayList<>();

            for (GlobalBlacklist entry : blacklist) {
                String v = entry.getValue() != null ? entry.getValue().toLowerCase() : "";
                switch (entry.getType()) {
                    case "artist"  -> bannedArtists.add(v);
                    case "song"    -> bannedSongs.add(v);
                    case "keyword" -> { if (!v.isBlank()) bannedKeywords.add(v); }
                }
            }

            int before = candidates.size();
            candidates = candidates.stream().filter(song -> {
                String title  = song.getTitle()  != null ? song.getTitle().toLowerCase()  : "";
                String artist = song.getArtist() != null ? song.getArtist().toLowerCase() : "";

                if (bannedArtists.contains(artist)) return false;
                if (bannedSongs.contains(title))    return false;
                for (String kw : bannedKeywords) {
                    if (title.contains(kw) || artist.contains(kw)) return false;
                }
                return true;
            }).collect(Collectors.toList());

            int removed = before - candidates.size();
            if (removed > 0) {
                log.info("Global blacklist filter: removed {} songs (was {})", removed, before);
            }
        } catch (Exception e) {
            log.warn("Global blacklist filtering failed, skipping", e);
        }
        return candidates;
    }
}
