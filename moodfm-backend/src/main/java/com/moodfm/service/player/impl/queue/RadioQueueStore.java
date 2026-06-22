package com.moodfm.service.player.impl.queue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodfm.common.constant.RedisKeys;
import com.moodfm.domain.vo.SongVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Dumb Redis I/O for the radio queue, re-rank counter, and session-duration TTL.
 * Holds no business logic — orchestration stays in PlayerServiceImpl.
 * Extracted from PlayerServiceImpl (T3-1 Task 6).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RadioQueueStore {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // ===================== Queue =====================

    /**
     * Atomically replace the user's queue with {@code songs} (serialize → MULTI/del/rPush → 2h TTL).
     * Empty list → delete the queue key. Self-contained: swallows + logs failures like the original.
     */
    public void replaceQueue(Long userId, List<SongVO> songs) {
        String queueKey = RedisKeys.format(RedisKeys.USER_QUEUE, userId);
        try {
            if (!songs.isEmpty()) {
                List<String> serialized = new ArrayList<>();
                for (SongVO s : songs) {
                    serialized.add(objectMapper.writeValueAsString(s));
                }
                // Use Redis transaction to atomically replace the queue
                redisTemplate.execute(new SessionCallback<>() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public List<Object> execute(RedisOperations operations) {
                        operations.multi();
                        operations.delete(queueKey);
                        operations.opsForList().rightPushAll(queueKey, serialized);
                        operations.expire(queueKey, Duration.ofHours(2));
                        return operations.exec();
                    }
                });
            } else {
                redisTemplate.delete(queueKey);
            }
        } catch (Exception e) {
            log.warn("Failed to cache queue", e);
        }
    }

    /** Pop up to {@code max} songs from the head of the queue (one LPOP per item). */
    public List<SongVO> popBatch(Long userId, int max) {
        String queueKey = RedisKeys.format(RedisKeys.USER_QUEUE, userId);
        List<SongVO> result = new ArrayList<>();
        for (int i = 0; i < max; i++) {
            String raw = redisTemplate.opsForList().leftPop(queueKey);
            if (raw == null) break;
            try {
                result.add(objectMapper.readValue(raw, SongVO.class));
            } catch (Exception e) {
                log.warn("Failed to deserialize queued song", e);
            }
        }
        return result;
    }

    /** Current queue length (nullable per Spring Data Redis contract). */
    public Long size(Long userId) {
        String queueKey = RedisKeys.format(RedisKeys.USER_QUEUE, userId);
        return redisTemplate.opsForList().size(queueKey);
    }

    /** Platform-song-ids currently in the queue (deserialize each entry, skip unparseable). */
    public Set<String> peekIds(Long userId) {
        String queueKey = RedisKeys.format(RedisKeys.USER_QUEUE, userId);
        List<String> rawList = redisTemplate.opsForList().range(queueKey, 0, -1);
        if (rawList == null) return Set.of();
        return rawList.stream().map(raw -> {
            try {
                return objectMapper.readValue(raw, SongVO.class).getPlatformSongId();
            } catch (Exception e) {
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    /**
     * Prepend {@code songs} to the head so the best-ranked ends up first (reverse + LPUSH).
     * No TTL refresh (matches the original re-rank prepend). Propagates serialization failure
     * to the caller's outer try/catch unchanged.
     */
    public void prependBatch(Long userId, List<SongVO> songs) throws JsonProcessingException {
        String queueKey = RedisKeys.format(RedisKeys.USER_QUEUE, userId);
        List<String> serialized = new ArrayList<>();
        for (SongVO s : songs) {
            serialized.add(objectMapper.writeValueAsString(s));
        }
        Collections.reverse(serialized);
        redisTemplate.opsForList().leftPushAll(queueKey, serialized);
    }

    /** Append {@code songs} to the tail (RPUSH) and refresh the 2h TTL. */
    public void appendBatch(Long userId, List<SongVO> songs) throws JsonProcessingException {
        String queueKey = RedisKeys.format(RedisKeys.USER_QUEUE, userId);
        List<String> serialized = new ArrayList<>();
        for (SongVO s : songs) {
            serialized.add(objectMapper.writeValueAsString(s));
        }
        redisTemplate.opsForList().rightPushAll(queueKey, serialized);
        redisTemplate.expire(queueKey, Duration.ofHours(2));
    }

    // ===================== Re-rank counter =====================

    /** Increment the re-rank counter (coercing null→1), refresh its 2h TTL, return the new count. */
    public long incrementAndExpireRerankCounter(Long userId) {
        String counterKey = RedisKeys.format(RedisKeys.QUEUE_RERANK, userId);
        Long count = redisTemplate.opsForValue().increment(counterKey);
        if (count == null) count = 1L;
        redisTemplate.expire(counterKey, Duration.ofHours(2));
        return count;
    }

    /** Reset (delete) the re-rank counter. */
    public void resetRerankCounter(Long userId) {
        redisTemplate.delete(RedisKeys.format(RedisKeys.QUEUE_RERANK, userId));
    }

    // ===================== Session duration TTL =====================

    /** Infinite-duration marker capped at a 24h TTL. */
    public void markSessionTtlInfinite(Long sessionId) {
        markSessionTtlInfinite(sessionId, null);
    }

    /** Infinite-duration marker capped at a 24h TTL, optionally recording session platform. */
    public void markSessionTtlInfinite(Long sessionId, String platform) {
        String ttlKey = RedisKeys.format(RedisKeys.SESSION_TTL, sessionId);
        Duration ttl = Duration.ofHours(24);
        redisTemplate.opsForValue().set(ttlKey, "infinite", ttl);
        markSessionPlatform(sessionId, platform, ttl);
    }

    /** Fixed-duration TTL of {@code seconds}. */
    public void markSessionTtlSeconds(Long sessionId, int seconds) {
        markSessionTtlSeconds(sessionId, seconds, null);
    }

    /** Fixed-duration TTL of {@code seconds}, optionally recording session platform. */
    public void markSessionTtlSeconds(Long sessionId, int seconds, String platform) {
        String ttlKey = RedisKeys.format(RedisKeys.SESSION_TTL, sessionId);
        Duration ttl = Duration.ofSeconds(seconds);
        redisTemplate.opsForValue().set(ttlKey, String.valueOf(seconds), ttl);
        markSessionPlatform(sessionId, platform, ttl);
    }

    public void markSessionPlatform(Long sessionId, String platform, Duration ttl) {
        if (sessionId == null || platform == null || platform.isBlank() || ttl == null) return;
        String key = RedisKeys.format(RedisKeys.SESSION_PLATFORM, sessionId);
        redisTemplate.opsForValue().set(key, platform, ttl);
    }

    public String getSessionPlatform(Long sessionId) {
        if (sessionId == null) return null;
        return redisTemplate.opsForValue().get(RedisKeys.format(RedisKeys.SESSION_PLATFORM, sessionId));
    }

    /** True when the session TTL key is absent (expired or never set). */
    public boolean isExpired(Long sessionId) {
        String ttlKey = RedisKeys.format(RedisKeys.SESSION_TTL, sessionId);
        return !Boolean.TRUE.equals(redisTemplate.hasKey(ttlKey));
    }
}
