package com.moodfm.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PlayRecordVO {
    private Long id;
    private Long sessionId;
    private String songId;
    private String platform;
    private String title;
    private String artist;
    private String coverUrl;
    private Integer playedSeconds;
    private Integer totalSeconds;
    private String action;
    private LocalDateTime playedAt;
    private String scene;
}
