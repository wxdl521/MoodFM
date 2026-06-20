package com.moodfm.service.feedback.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moodfm.domain.dto.feedback.PlaybackFeedbackDto;
import com.moodfm.domain.entity.FeedbackEvent;
import com.moodfm.domain.entity.PlayRecord;
import com.moodfm.domain.entity.PlatformSongMapping;
import com.moodfm.mapper.FeedbackEventMapper;
import com.moodfm.mapper.PlayRecordMapper;
import com.moodfm.mapper.PlatformSongMappingMapper;
import com.moodfm.service.feedback.FeedbackService;
import com.moodfm.service.platform.PlatformBindingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {

    private final FeedbackEventMapper feedbackEventMapper;
    private final PlayRecordMapper playRecordMapper;
    private final PlatformSongMappingMapper platformSongMappingMapper;
    private final PlatformBindingService platformBindingService;

    @Override
    @Async
    @Transactional
    public void record(Long userId, PlaybackFeedbackDto dto) {
        FeedbackEvent event = new FeedbackEvent();
        event.setUserId(userId);
        event.setSessionId(dto.getSessionId());
        event.setSongId(dto.getSongId());
        event.setEventType(dto.getEventType());
        feedbackEventMapper.insert(event);

        if ("completed".equals(dto.getEventType()) || "skip".equals(dto.getEventType())) {
            PlayRecord record = new PlayRecord();
            record.setUserId(userId);
            record.setSessionId(dto.getSessionId());
            record.setSongId(dto.getSongId());
            record.setPlatform(resolvePlatform(userId, dto));
            record.setPlayedSeconds(dto.getPlayedSeconds());
            record.setTotalSeconds(dto.getTotalSeconds());
            record.setAction(mapAction(dto.getEventType(), dto.getPlayedSeconds(), dto.getTotalSeconds()));
            playRecordMapper.insert(record);
            log.info("play_record written userId={} songId={} type={}", userId, dto.getSongId(), dto.getEventType());
        }
    }

    /**
     * Resolves the platform for a play_record using a priority chain:
     * <ol>
     *   <li>dto.platform (non-blank) — use it directly.</li>
     *   <li>platform_song_mapping by songId — use only when exactly one row exists.</li>
     *   <li>User's default platform binding — swallows BizException if unbound.</li>
     *   <li>Fallback to "unknown" with a warn log; record is still written.</li>
     * </ol>
     * No exception from any step may propagate — this runs inside @Async fire-and-forget.
     */
    private String resolvePlatform(Long userId, PlaybackFeedbackDto dto) {
        // Step 1: dto carries an explicit platform value.
        if (StringUtils.hasText(dto.getPlatform())) {
            return dto.getPlatform();
        }

        // Step 2: exactly one mapping row for this song → unambiguous platform.
        if (dto.getSongId() != null) {
            try {
                List<PlatformSongMapping> mappings = platformSongMappingMapper.selectList(
                        new LambdaQueryWrapper<PlatformSongMapping>()
                                .eq(PlatformSongMapping::getSongId, dto.getSongId())
                );
                if (mappings != null && mappings.size() == 1) {
                    return mappings.get(0).getPlatform();
                }
            } catch (Exception e) {
                log.warn("resolvePlatform: mapping lookup failed userId={} songId={} — {}",
                        userId, dto.getSongId(), e.getMessage());
            }
        }

        // Step 3: user's default platform binding.
        try {
            var binding = platformBindingService.getDefaultBinding(userId);
            if (binding != null && StringUtils.hasText(binding.getPlatform())) {
                return binding.getPlatform();
            }
        } catch (Exception e) {
            // BizException(PLATFORM_NOT_BOUND) is expected when unbound; swallow silently.
            log.debug("resolvePlatform: no default binding for userId={} — {}", userId, e.getMessage());
        }

        // Step 4: nothing resolved — record "unknown" and warn.
        log.warn("resolvePlatform: could not determine platform userId={} songId={} sessionId={} — writing 'unknown'",
                userId, dto.getSongId(), dto.getSessionId());
        return "unknown";
    }

    private String mapAction(String eventType, Integer played, Integer total) {
        if ("completed".equals(eventType)) return "completed";
        if ("skip".equals(eventType) && played != null && total != null) {
            return played < 30 ? "skipped_early" : "skipped";
        }
        return eventType;
    }
}
