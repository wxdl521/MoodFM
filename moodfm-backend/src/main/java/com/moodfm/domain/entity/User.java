package com.moodfm.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("users")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String email;

    private String phone;

    private String passwordHash;

    private String avatarUrl;

    @TableLogic
    private Integer status; // 1=正常 0=软删除

    private String role; // USER / ADMIN

    private Boolean emailVerified;

    private Integer loginFailCount;

    private LocalDateTime lockUntil;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
