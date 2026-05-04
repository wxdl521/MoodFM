package com.moodfm.domain.dto.radio;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class MoodInputRequest {

    /** 自然语言输入，与 scene/moodWheel 三选一 */
    private String text;

    /** 场景预设: commute/study/workout/sleep/party/writing */
    private String scene;

    /** 心情色盘: 0.0-1.0 */
    @Min(0) @Max(1)
    private Double valence;

    @Min(0) @Max(1)
    private Double energy;

    /** 电台时长（分钟），默认 30 */
    @Min(10) @Max(120)
    private Integer durationMinutes = 30;
}
