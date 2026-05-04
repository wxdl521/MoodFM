package com.moodfm.service.feedback.impl;

import com.moodfm.domain.dto.feedback.PlaybackFeedbackDto;
import com.moodfm.domain.entity.FeedbackEvent;
import com.moodfm.domain.entity.PlayRecord;
import com.moodfm.mapper.FeedbackEventMapper;
import com.moodfm.mapper.PlayRecordMapper;
import com.moodfm.service.feedback.FeedbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {

    private final FeedbackEventMapper feedbackEventMapper;
    private final PlayRecordMapper playRecordMapper;

    @Override
    @Async
    public void record(Long userId, PlaybackFeedbackDto dto) {
        // 记录反馈事件
        FeedbackEvent event = new FeedbackEvent();
        event.setUserId(userId);
        event.setSessionId(dto.getSessionId());
        event.setSongId(dto.getSongId());
        event.setEventType(dto.getEventType());
        feedbackEventMapper.insert(event);

        // 播放完成或跳过时，写入播放记录
        if ("completed".equals(dto.getEventType()) || "skip".equals(dto.getEventType())) {
            PlayRecord record = new PlayRecord();
            record.setUserId(userId);
            record.setSessionId(dto.getSessionId());
            record.setSongId(dto.getSongId());
            record.setPlayedSeconds(dto.getPlayedSeconds());
            record.setTotalSeconds(dto.getTotalSeconds());
            record.setAction(mapAction(dto.getEventType(), dto.getPlayedSeconds(), dto.getTotalSeconds()));
            playRecordMapper.insert(record);
        }
    }

    private String mapAction(String eventType, Integer played, Integer total) {
        if ("completed".equals(eventType)) return "completed";
        if ("skip".equals(eventType) && played != null && total != null) {
            return played < 30 ? "skipped_early" : "skipped";
        }
        return eventType;
    }
}
