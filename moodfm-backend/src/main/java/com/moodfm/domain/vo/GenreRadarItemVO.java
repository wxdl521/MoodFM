package com.moodfm.domain.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GenreRadarItemVO {
    private String genre;    // English
    private String genreCn;  // Chinese
    private double value;    // 0–1 normalised
}
