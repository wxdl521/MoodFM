package com.moodfm.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PlatformBindingVO {
    private Long id;
    private String platform;
    private String platformUsername;
    private boolean valid;
    private boolean isDefault;
    private LocalDateTime lastValidatedAt;
}
