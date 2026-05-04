package com.moodfm.service.ai;

import com.moodfm.ai.model.MoodParams;
import com.moodfm.domain.dto.radio.MoodInputRequest;

public interface MoodAnalysisService {
    MoodParams analyze(MoodInputRequest request);
}
