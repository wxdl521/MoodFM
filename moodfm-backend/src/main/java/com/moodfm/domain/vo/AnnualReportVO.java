package com.moodfm.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AnnualReportVO {
    private int year;
    private String totalListeningTime;   // "324h 12m"
    private int totalTracks;
    private int totalSessions;
    private String topGenre;
    private String topMood;
    private List<TopArtist> topArtists;
    private List<TopSong> topSongs;
    private List<MonthlyStat> monthlyStats;

    @Data
    @Builder
    public static class TopArtist {
        private String name;
        private String totalTime;
        private int playCount;
    }

    @Data
    @Builder
    public static class TopSong {
        private String title;
        private String artist;
        private int playCount;
    }

    @Data
    @Builder
    public static class MonthlyStat {
        private int month;
        private int tracks;
        private int minutes;
    }
}
