package com.moodfm.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("platform_bindings")
public class PlatformBinding {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** netease / qqmusic / spotify */
    private String platform;

    private String platformUserId;

    private String platformUsername;

    /** AES-256-GCM 加密后的 Cookie */
    private String cookieEncrypted;

    private Integer isDefault; // 1=默认平台

    private Integer isValid;   // 1=有效 0=失效

    private LocalDateTime lastValidatedAt;

    private LocalDateTime expiresAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
