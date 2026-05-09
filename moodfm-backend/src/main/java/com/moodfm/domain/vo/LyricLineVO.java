package com.moodfm.domain.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LyricLineVO {
    private Long time;
    private String text;
}
