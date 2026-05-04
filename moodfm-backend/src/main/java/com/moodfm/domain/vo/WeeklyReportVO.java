package com.moodfm.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyReportVO {

    private Long id;
    private String weekLabel;     // "WK21"
    private String dateRange;     // "05/05—05/11"
    private String titleEn1;      // "A week of"
    private String headlineWord;  // "quiet"
    private String headlineWord2; // "light."
    private String titleCn;       // 中文标题
    private String summary;       // AI 一句话摘要
    private String essayBody;     // AI 短文段落
    private String quote;         // AI 金句
    private String userNickname;

    private WeekStats stats;
    private SongInfo mostPlayedSong;
    private List<TrackInfo> topDiscoveries;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeekStats {
        private String tracks;
        private String listenTime;
        private String newDiscovered;
        private String moodMatch;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SongInfo {
        private String title;
        private String artist;
        private int playCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrackInfo {
        private String title;
        private String artist;
        private String mood;
        private String genre;
    }
}
