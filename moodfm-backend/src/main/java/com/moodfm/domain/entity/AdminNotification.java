package com.moodfm.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("admin_notification")
public class AdminNotification {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String body;
    private String type;
    private String targetGroup;
    private String status;
    private LocalDateTime scheduledAt;
    private LocalDateTime sentAt;
    private Integer sentCount;
    private Integer openedCount;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
