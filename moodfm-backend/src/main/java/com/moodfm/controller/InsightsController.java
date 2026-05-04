package com.moodfm.controller;

import com.moodfm.common.result.R;
import com.moodfm.domain.vo.*;
import com.moodfm.service.insights.InsightsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "数据洞察", description = "个人画像 / 日历 / 趋势接口")
@RestController
@RequestMapping("/api/insights")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class InsightsController {

    private final InsightsService insightsService;

    @Operation(summary = "获取月度心情日历")
    @GetMapping("/calendar")
    public R<CalendarMonthVO> getCalendar(
            @RequestParam int year,
            @RequestParam int month,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = Long.parseLong(userDetails.getUsername());
        return R.ok(insightsService.getCalendar(userId, year, month));
    }

    @Operation(summary = "获取某天详情（心情 + 当天最常听）")
    @GetMapping("/day/{date}")
    public R<DayDetailVO> getDayDetail(
            @PathVariable String date,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = Long.parseLong(userDetails.getUsername());
        return R.ok(insightsService.getDayDetail(userId, LocalDate.parse(date)));
    }

    @Operation(summary = "获取综合统计摘要")
    @GetMapping("/summary")
    public R<InsightsSummaryVO> getSummary(
            @RequestParam(defaultValue = "7") int days,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = Long.parseLong(userDetails.getUsername());
        return R.ok(insightsService.getSummary(userId, days));
    }

    @Operation(summary = "获取心情曲线数据")
    @GetMapping("/mood-trend")
    public R<MoodTrendVO> getMoodTrend(
            @RequestParam(defaultValue = "7") int days,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = Long.parseLong(userDetails.getUsername());
        return R.ok(insightsService.getMoodTrend(userId, days));
    }

    @Operation(summary = "获取流派分布雷达图数据")
    @GetMapping("/genre-radar")
    public R<List<GenreRadarItemVO>> getGenreRadar(
            @RequestParam(defaultValue = "7") int days,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = Long.parseLong(userDetails.getUsername());
        return R.ok(insightsService.getGenreRadar(userId, days));
    }

    @Operation(summary = "获取 TOP 艺人 + TOP 歌曲")
    @GetMapping("/top-items")
    public R<TopItemsVO> getTopItems(
            @RequestParam(defaultValue = "7") int days,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = Long.parseLong(userDetails.getUsername());
        return R.ok(insightsService.getTopItems(userId, days));
    }
}
