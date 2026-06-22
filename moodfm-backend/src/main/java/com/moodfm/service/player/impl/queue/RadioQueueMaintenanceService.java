package com.moodfm.service.player.impl.queue;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodfm.ai.model.MoodParams;
import com.moodfm.common.util.AesUtil;
import com.moodfm.domain.entity.MoodSession;
import com.moodfm.domain.entity.PlatformBinding;
import com.moodfm.domain.vo.SongVO;
import com.moodfm.mapper.MoodSessionMapper;
import com.moodfm.service.platform.PlatformBindingService;
import com.moodfm.service.player.impl.catalog.SongCatalogService;
import com.moodfm.service.player.impl.playurl.PlayUrlService;
import com.moodfm.service.player.impl.ranking.AiRankingService;
import com.moodfm.service.player.impl.recall.CandidateRecallService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * Dynamic re-rank and queue refill extracted from {@code PlayerServiceImpl} (T3 post-review).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RadioQueueMaintenanceService {

    static final int RERANK_EVERY = 3;
    static final int REFILL_THRESHOLD = 5;
    static final int RERANK_NEXT_N = 5;

    private final PlatformBindingService platformBindingService;
    private final AesUtil aesUtil;
    private final ObjectMapper objectMapper;
    private final MoodSessionMapper sessionMapper;
    private final CandidateRecallService candidateRecallService;
    private final AiRankingService aiRankingService;
    private final PlayUrlService playUrlService;
    private final SongCatalogService songCatalogService;
    private final RadioQueueStore radioQueueStore;

    public void reRankIfNeeded(Long userId, Long sessionId, ExecutorService bgExecutor) {
        if (userId == null) return;
        long count = radioQueueStore.incrementAndExpireRerankCounter(userId);
        if (count >= RERANK_EVERY) {
            radioQueueStore.resetRerankCounter(userId);
            CompletableFuture.runAsync(() -> doReRank(userId, sessionId), bgExecutor);
            log.info("Triggered re-rank for user {} after {} songs", userId, count);
        }
    }

    public void refillIfBelowThreshold(Long userId, ExecutorService bgExecutor) {
        CompletableFuture.runAsync(() -> refillQueue(userId), bgExecutor);
    }

    public int refillThreshold() {
        return REFILL_THRESHOLD;
    }

    private void doReRank(Long userId, Long sessionId) {
        try {
            PlatformBinding binding = platformBindingService.getDefaultBinding(userId);
            String cookie = aesUtil.decrypt(binding.getCookieEncrypted());
            String platform = binding.getPlatform();

            MoodParams mood = loadMoodFromSession(sessionId);
            if (mood == null) {
                log.warn("Cannot re-rank: session {} mood params not found", sessionId);
                return;
            }

            List<SongVO> candidates = candidateRecallService.recallSongs(platform, cookie, mood, userId);

            Set<String> existingIds = radioQueueStore.peekIds(userId);
            List<SongVO> fresh = candidates.stream()
                    .filter(s -> s.getPlatformSongId() != null && !existingIds.contains(s.getPlatformSongId()))
                    .collect(Collectors.toList());

            if (fresh.isEmpty()) {
                log.info("Re-rank: no new candidates for user {}", userId);
                return;
            }

            List<SongVO> ranked = aiRankingService.rank(fresh, mood);
            List<SongVO> nextBatch = ranked.stream().limit(RERANK_NEXT_N).collect(Collectors.toList());

            playUrlService.enrichWithPlayUrls(nextBatch, platform, cookie);
            songCatalogService.persistSongs(nextBatch, platform);
            radioQueueStore.prependBatch(userId, nextBatch);

            log.info("Re-ranked {} songs prepended to queue for user {}", nextBatch.size(), userId);
        } catch (Exception e) {
            log.error("Re-rank failed for user {}", userId, e);
        }
    }

    private void refillQueue(Long userId) {
        try {
            Long currentSize = radioQueueStore.size(userId);
            if (currentSize != null && currentSize >= REFILL_THRESHOLD) return;

            PlatformBinding binding = platformBindingService.getDefaultBinding(userId);
            String cookie = aesUtil.decrypt(binding.getCookieEncrypted());
            String platform = binding.getPlatform();

            MoodParams mood = loadLatestMood(userId);
            List<SongVO> candidates = candidateRecallService.recallSongs(platform, cookie, mood, userId);

            Set<String> existingIds = radioQueueStore.peekIds(userId);
            List<SongVO> fresh = candidates.stream()
                    .filter(s -> s.getPlatformSongId() != null && !existingIds.contains(s.getPlatformSongId()))
                    .limit(20)
                    .collect(Collectors.toList());

            if (fresh.isEmpty()) return;

            playUrlService.enrichWithPlayUrls(fresh, platform, cookie);
            songCatalogService.persistSongs(fresh, platform);
            radioQueueStore.appendBatch(userId, fresh);

            log.info("Refilled queue with {} songs for user {}", fresh.size(), userId);
        } catch (Exception e) {
            log.warn("Queue refill failed for user {}", userId, e);
        }
    }

    private MoodParams loadMoodFromSession(Long sessionId) {
        if (sessionId == null) return null;
        try {
            MoodSession session = sessionMapper.selectById(sessionId);
            if (session != null && session.getMoodParams() != null) {
                return objectMapper.readValue(session.getMoodParams(), MoodParams.class);
            }
        } catch (Exception e) {
            log.warn("Failed to load mood from session {}", sessionId, e);
        }
        return null;
    }

    private MoodParams loadLatestMood(Long userId) {
        try {
            MoodSession session = sessionMapper.selectOne(
                    new LambdaQueryWrapper<MoodSession>()
                            .eq(MoodSession::getUserId, userId)
                            .isNotNull(MoodSession::getMoodParams)
                            .orderByDesc(MoodSession::getStartedAt)
                            .last("LIMIT 1"));
            if (session != null && session.getMoodParams() != null) {
                return objectMapper.readValue(session.getMoodParams(), MoodParams.class);
            }
        } catch (Exception e) {
            log.warn("Failed to load latest mood for user {}", userId, e);
        }
        return MoodParams.defaultParams();
    }
}