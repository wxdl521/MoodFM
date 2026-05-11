package com.moodfm.controller;

import com.moodfm.common.result.R;
import com.moodfm.domain.entity.WeeklyReport;
import com.moodfm.domain.vo.AnnualReportVO;
import com.moodfm.domain.vo.WeeklyReportVO;
import com.moodfm.service.report.AnnualReportService;
import com.moodfm.service.report.WeeklyReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "周报", description = "周报生成与获取")
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class ReportsController {

    private final WeeklyReportService weeklyReportService;
    private final AnnualReportService annualReportService;

    @Operation(summary = "获取指定周报")
    @GetMapping("/weekly/{id}")
    public R<WeeklyReportVO> getReport(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = Long.parseLong(userDetails.getUsername());
        return R.ok(weeklyReportService.getReport(userId, id));
    }

    @Operation(summary = "列出所有周报")
    @GetMapping("/weekly")
    public R<List<WeeklyReport>> listReports(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = Long.parseLong(userDetails.getUsername());
        return R.ok(weeklyReportService.listReports(userId));
    }

    @Operation(summary = "生成/获取本周（上周）周报")
    @PostMapping("/weekly/generate")
    public R<WeeklyReportVO> generateCurrentWeek(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = Long.parseLong(userDetails.getUsername());
        WeeklyReportVO vo = weeklyReportService.generateOrGetCurrentWeek(userId);
        if (vo == null) return R.fail(400, "本周尚无足够收听数据");
        return R.ok(vo);
    }

    @Operation(summary = "获取年度报告")
    @GetMapping("/annual/{year}")
    public R<AnnualReportVO> getAnnualReport(
            @PathVariable int year,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = Long.parseLong(userDetails.getUsername());
        return R.ok(annualReportService.getAnnualReport(userId, year));
    }
}
