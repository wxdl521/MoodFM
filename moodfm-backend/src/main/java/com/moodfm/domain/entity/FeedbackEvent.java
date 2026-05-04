package com.moodfm.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("feedback_events")
public class FeedbackEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long sessionId;

    private Long songId;

    /** play / skip / like / dislike / volume_up */
    private String eventType;

    /** JSON 附加数据 */
    private String eventData;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
