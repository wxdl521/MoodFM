package com.moodfm.domain.dto.radio;

import com.moodfm.domain.dto.feedback.PlaybackFeedbackDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class BatchFeedbackRequest {

    @NotNull
    @Valid
    private List<PlaybackFeedbackDto> events;
}
