package com.moodfm.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mood_sessions")
public class MoodSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String rawInput;

    /** JSON 格式的 MoodParams */
    private String moodParams;

    private String scene;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime startedAt;

    private Integer durationMinutes;
}
