package com.moodfm.domain.dto.radio;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SessionDurationRequest {

    @NotNull
    private Long sessionId;

    @NotNull
    @Min(1)
    @Max(180)
    private Integer minutes;
}
