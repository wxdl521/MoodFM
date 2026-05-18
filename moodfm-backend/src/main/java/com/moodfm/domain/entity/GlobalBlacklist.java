package com.moodfm.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("global_blacklist")
public class GlobalBlacklist {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String type;   // artist | song | keyword
    private String value;
    private String artist;
    private String scope;
    private String reason;
    private String addedBy;
    private LocalDateTime createdAt;
}
