package com.moodfm.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TopItemsVO {
    private List<ArtistItem> artists;
    private List<SongItem> songs;

    @Data
    @Builder
    public static class ArtistItem {
        private String name;
        private String tag;
        private String totalTime;  // "1h 23m"
        private double proportion; // 0–1
    }

    @Data
    @Builder
    public static class SongItem {
        private String title;
        private String artist;
        private int playCount;
        private double proportion; // 0–1
    }
}
