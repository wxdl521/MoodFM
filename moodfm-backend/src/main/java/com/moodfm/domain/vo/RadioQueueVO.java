package com.moodfm.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RadioQueueVO {
    private Long sessionId;
    private String scene;
    private String moodSummary;
    private List<SongVO> songs;
    private int totalCount;
}
