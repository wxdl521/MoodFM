package com.moodfm.service.player.impl.recall.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodfm.domain.entity.UserProfile;
import com.moodfm.domain.vo.SongVO;
import com.moodfm.mapper.UserProfileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Feature 4b: 黑名单关键词过滤。Pipeline stage 2 ({@code @Order(20)}).
 * <p>
 * 从用户 UserProfile 获取黑名单关键词，过滤掉标题或歌手名包含关键词的歌曲。
 * 简单的大小写不敏感子串匹配。
 * <p>
 * userId==null 时短路返回原列表（匿名会话不做黑名单过滤）。
 * 方法体逐字搬迁自 {@code CandidateRecallService.filterBlacklistKeywords}（T3-2 Task 2）。
 */
@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class BlacklistKeywordFilter implements CandidateFilter {

    private final UserProfileMapper userProfileMapper;
    private final ObjectMapper objectMapper;

    @Override
    public List<SongVO> filter(Long userId, List<SongVO> candidates) {
        if (userId == null) return candidates;
        try {
            UserProfile profile = userProfileMapper.selectByUserId(userId);
            if (profile == null || profile.getBlacklistKeywords() == null || profile.getBlacklistKeywords().isBlank()) {
                return candidates;
            }

            // Parse JSON array of keywords
            List<String> keywords = objectMapper.readValue(profile.getBlacklistKeywords(),
                    new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
            if (keywords.isEmpty()) return candidates;

            List<String> lowerKeywords = keywords.stream()
                    .filter(k -> k != null && !k.isBlank())
                    .map(String::toLowerCase)
                    .collect(Collectors.toList());
            if (lowerKeywords.isEmpty()) return candidates;

            int beforeSize = candidates.size();
            candidates = candidates.stream()
                    .filter(song -> {
                        String title = song.getTitle() != null ? song.getTitle().toLowerCase() : "";
                        String artist = song.getArtist() != null ? song.getArtist().toLowerCase() : "";
                        for (String kw : lowerKeywords) {
                            if (title.contains(kw) || artist.contains(kw)) {
                                return false;
                            }
                        }
                        return true;
                    })
                    .collect(Collectors.toList());

            int removed = beforeSize - candidates.size();
            if (removed > 0) {
                log.info("Blacklist keyword filter: removed {} songs for user {} (was {})", removed, userId, beforeSize);
            }
        } catch (Exception e) {
            log.warn("Blacklist keyword filtering failed for user {}, skipping", userId, e);
        }
        return candidates;
    }
}
