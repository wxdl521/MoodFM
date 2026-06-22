package com.moodfm.domain.dto.radio;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PlayFromPlaylistRequest {
    @NotBlank(message = "playlistId 不能为空")
    private String playlistId;

    /** 会话时长（分钟），默认 30；null 表示无限（24h TTL 封顶） */
    private Integer durationMinutes;
}