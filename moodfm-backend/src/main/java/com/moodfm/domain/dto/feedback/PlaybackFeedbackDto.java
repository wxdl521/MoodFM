package com.moodfm.domain.dto.feedback;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PlaybackFeedbackDto {

    @NotNull
    private Long sessionId;

    @NotNull
    private Long songId;

    /** play / skip / like / dislike / volume_up / pause */
    @NotBlank
    private String eventType;

    private Integer playedSeconds;

    private Integer totalSeconds;
}
