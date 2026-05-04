package com.moodfm.service.insights;

import com.moodfm.domain.vo.*;

import java.time.LocalDate;
import java.util.List;

public interface InsightsService {
    CalendarMonthVO getCalendar(Long userId, int year, int month);
    DayDetailVO getDayDetail(Long userId, LocalDate date);
    InsightsSummaryVO getSummary(Long userId, int days);
    MoodTrendVO getMoodTrend(Long userId, int days);
    List<GenreRadarItemVO> getGenreRadar(Long userId, int days);
    TopItemsVO getTopItems(Long userId, int days);
}
