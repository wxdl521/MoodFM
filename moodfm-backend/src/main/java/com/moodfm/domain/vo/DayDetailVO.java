package com.moodfm.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DayDetailVO {
    private int day;
    private String mood;
    private int tracks;
    private int minutes;
    private int sessions;
    private List<TrackVO> topTracks;

    @Data
    @Builder
    public static class TrackVO {
        private String title;
        private String artist;
        private String durationFormatted;
        private int playCount;
    }
}
