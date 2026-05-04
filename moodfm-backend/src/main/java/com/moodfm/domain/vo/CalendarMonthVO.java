package com.moodfm.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CalendarMonthVO {
    private int year;
    private int month;
    private int firstDayOfWeek;  // 0=Sunday … 6=Saturday
    private int daysInMonth;
    private List<CalendarDayVO> days;
}
