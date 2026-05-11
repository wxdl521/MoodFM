package com.moodfm.domain.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HistoryItemVO {
    private Long id;
    private Long sessionId;
    private String playedAt;
    private Integer durationPlayed;
    private SongBriefVO song;

    @Data
    @Builder
    public static class SongBriefVO {
        private String id;
        private String title;
        private String artist;
        private String platform;
        private String platformSongId;
        private Integer duration;
        private String coverUrl;
    }
}
