package com.moodfm.service.player.impl.recall.filter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodfm.domain.entity.FeedbackEvent;
import com.moodfm.domain.entity.PlatformSongMapping;
import com.moodfm.domain.vo.SongVO;
import com.moodfm.mapper.FeedbackEventMapper;
import com.moodfm.mapper.PlatformSongMappingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Feature 4: 反馈评分过滤。Pipeline stage 1 ({@code @Order(10)}).
 * <p>
 * 查询用户近期反馈，计算每首歌的得分，过滤掉严重负分歌曲。
 * 信号权重: completed=+1, skip(playedSeconds&lt;30)=-3, like=+5, volume_up=+1 (scaled 0.5)
 * 使用 2x 整数缩放以支持 0.5 权重。
 * <p>
 * userId==null 时短路返回原列表（匿名会话不做反馈过滤）。
 * 方法体逐字搬迁自 {@code CandidateRecallService.filterNegativeFeedback}（T3-2 Task 2）。
 */
@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class NegativeFeedbackFilter implements CandidateFilter {

    private final FeedbackEventMapper feedbackEventMapper;
    private final PlatformSongMappingMapper platformSongMappingMapper;
    private final ObjectMapper objectMapper;

    @Override
    public List<SongVO> filter(Long userId, List<SongVO> candidates) {
        if (userId == null) return candidates;
        try {
            List<FeedbackEvent> recentEvents = feedbackEventMapper.selectList(
                    new LambdaQueryWrapper<FeedbackEvent>()
                            .eq(FeedbackEvent::getUserId, userId)
                            .in(FeedbackEvent::getEventType, "completed", "skip", "like", "volume_up")
                            .orderByDesc(FeedbackEvent::getCreatedAt)
                            .last("LIMIT 200"));

            if (recentEvents.isEmpty()) return candidates;

            // 计算每首歌的反馈得分（2x 缩放：volume_up=1 代表 0.5）
            Map<Long, Integer> songScores = new HashMap<>();
            for (FeedbackEvent evt : recentEvents) {
                if (evt.getSongId() == null) continue;
                int delta = switch (evt.getEventType()) {
                    case "completed" -> 2;
                    case "like" -> 10;
                    case "volume_up" -> 1;  // +0.5 scaled
                    case "skip" -> {
                        int played = parsePlayedSeconds(evt.getEventData());
                        yield played < 30 ? -6 : 0;
                    }
                    default -> 0;
                };
                songScores.merge(evt.getSongId(), delta, Integer::sum);
            }

            // 从候选中移除严重负分歌曲（score < -10, 即 2x 缩放后等价于 -5）
            // Batch lookup: collect all negative songIds, single DB query instead of N+1
            Set<String> negativeIds = new HashSet<>();
            List<Long> negativeSongIds = songScores.entrySet().stream()
                    .filter(e -> e.getValue() < -10)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
            if (!negativeSongIds.isEmpty()) {
                List<PlatformSongMapping> mappings = platformSongMappingMapper.selectList(
                        new LambdaQueryWrapper<PlatformSongMapping>()
                                .in(PlatformSongMapping::getSongId, negativeSongIds));
                for (PlatformSongMapping m : mappings) {
                    negativeIds.add(m.getPlatformSongId());
                }
            }

            if (!negativeIds.isEmpty()) {
                int beforeSize = candidates.size();
                candidates = candidates.stream()
                        .filter(s -> s.getPlatformSongId() == null || !negativeIds.contains(s.getPlatformSongId()))
                        .collect(Collectors.toList());
                log.info("Feedback filter: removed {} negative songs (was {})", beforeSize - candidates.size(), beforeSize);
            }
        } catch (Exception e) {
            log.warn("Feedback filtering failed, skipping", e);
        }
        return candidates;
    }

    private int parsePlayedSeconds(String eventData) {
        if (eventData == null || eventData.isBlank()) return 0;
        try {
            JsonNode node = objectMapper.readTree(eventData);
            return node.path("playedSeconds").asInt(0);
        } catch (Exception e) {
            return 0;
        }
    }
}
