package com.moodfm.service.report;

import com.moodfm.domain.entity.WeeklyReport;
import com.moodfm.domain.vo.WeeklyReportVO;

import java.util.List;

public interface WeeklyReportService {

    /** 按 ID 获取某份周报（鉴权：必须属于 userId） */
    WeeklyReportVO getReport(Long userId, Long reportId);

    /** 获取/生成本周周报（幂等）；无数据时返回 null */
    WeeklyReportVO generateOrGetCurrentWeek(Long userId);

    /** 列出用户所有周报（轻量列表） */
    List<WeeklyReport> listReports(Long userId);
}
