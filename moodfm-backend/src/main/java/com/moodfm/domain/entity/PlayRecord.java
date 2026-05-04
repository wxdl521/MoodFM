package com.moodfm.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("play_records")
public class PlayRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long sessionId;

    private Long songId;

    private String platform;

    private Integer playedSeconds;

    private Integer totalSeconds;

    /** completed / skipped / liked / disliked */
    private String action;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime playedAt;
}
