package com.moodfm.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("weekly_reports")
public class WeeklyReport {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private LocalDate weekStart;

    private LocalDate weekEnd;

    /** 完整 WeeklyReportVO 序列化为 JSON */
    private String data;

    private String aiSummary;

    private LocalDateTime generatedAt;
}
