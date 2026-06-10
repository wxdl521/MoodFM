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

    private Integer status; // 1=正常 0=封禁（管理员操作）

    @TableLogic
    private Integer deleted; // 1=已注销（逻辑删除）

    private String role; // USER / ADMIN

    private Boolean emailVerified;

    private LocalDateTime lockUntil;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
