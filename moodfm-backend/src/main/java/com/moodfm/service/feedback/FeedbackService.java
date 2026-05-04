package com.moodfm.service.feedback;

import com.moodfm.domain.dto.feedback.PlaybackFeedbackDto;

public interface FeedbackService {
    void record(Long userId, PlaybackFeedbackDto dto);
}
