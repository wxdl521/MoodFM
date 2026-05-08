package com.moodfm.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SessionSummaryVO {
    private Long id;
    private String rawInput;
    private String scene;
    private LocalDateTime startedAt;
}
