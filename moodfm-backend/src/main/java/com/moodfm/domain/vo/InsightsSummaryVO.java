package com.moodfm.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class InsightsSummaryVO {
    private String headline;      // English mood word, e.g. "wistful"
    private String headlineAlt;   // secondary word
    private String headlineCn;    // Chinese summary phrase

    private Stats stats;
    private AiSummary aiSummary;
    private List<ReportCard> previousReports;

    // essayTitle / essayBody / weeklyReportId flattened for front-end convenience
    private String essayTitle;
    private String essayBody;
    private Long weeklyReportId;

    @Data
    @Builder
    public static class Stats {
        private String totalListeningTime;  // "2h 34m"
        private String topGenre;
        private String topGenreRatio;       // "42%"
        private String aiAccuracy;          // "78%"
        private String moodKey;             // "平静"
        private String moodSub;             // secondary label
        private String weekLabel;           // "本周 · WK18"
        private String weekRange;           // "APR.28 — MAY.04"
        private String listeningVsLast;     // "+12%"
    }

    @Data
    @Builder
    public static class AiSummary {
        private String title;
        private String body;
        private Long reportId;
    }

    @Data
    @Builder
    public static class ReportCard {
        private Long id;
        private int week;
        private int year;
        private String title;
        private String mood;
    }
}
