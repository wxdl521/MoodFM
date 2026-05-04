package com.moodfm.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MoodTrendVO {
    private List<String> labels;    // date labels, e.g. "05/01"
    private List<Double> userMood;  // valence from mood sessions
    private List<Double> songMood;  // avg energy from songs played
    private int lowPointIndex;      // index of the lowest userMood value
}
