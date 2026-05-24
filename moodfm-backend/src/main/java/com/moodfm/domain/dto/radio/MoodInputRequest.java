package com.moodfm.domain.dto.radio;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class MoodInputRequest {

    /** 自然语言输入，与 scene/moodWheel 三选一。前端历史字段名 moodText 通过 alias 兼容 */
    @JsonAlias("moodText")
    private String text;

    /** 场景预设: commute/study/workout/sleep/party/writing */
    private String scene;

    /** 心情色盘: 0.0-1.0 */
    @Min(0) @Max(1)
    private Double valence;

    @Min(0) @Max(1)
    private Double energy;

    /** 电台时长（分钟），默认 30；显式传 null 表示"无限"（不设置 TTL） */
    @Min(10) @Max(120)
    private Integer durationMinutes = 30;
}
