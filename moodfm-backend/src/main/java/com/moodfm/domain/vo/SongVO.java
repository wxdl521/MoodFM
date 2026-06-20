package com.moodfm.domain.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SongVO {
    private Long id;
    private String title;
    private String artist;
    private String album;
    private Integer durationSeconds;
    private String coverUrl;
    private String platform;
    private String platformSongId;
    private String playUrl;
    private String recommendReason; // AI 推荐解释
    private String urlSource; // "qqmusic" | "netease_fallback" — set when play URL comes from a different platform
    private String features;  // JSON string from Song.features; null if not yet enriched
}
