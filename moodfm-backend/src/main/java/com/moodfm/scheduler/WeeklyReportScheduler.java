package com.moodfm.scheduler;

import com.moodfm.domain.entity.User;
import com.moodfm.mapper.UserMapper;
import com.moodfm.service.report.impl.WeeklyReportServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeeklyReportScheduler {

    private final UserMapper userMapper;
    private final WeeklyReportServiceImpl weeklyReportService;

    /** 每周一早 9 点为所有活跃用户生成上周周报 */
    @Scheduled(cron = "0 0 9 * * MON")
    public void generateWeeklyReports() {
        LocalDate today     = LocalDate.now();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY).minusWeeks(1);
        LocalDate weekEnd   = weekStart.plusWeeks(1);

        log.info("Weekly report scheduler: generating for week {}", weekStart);

        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>().eq(User::getStatus, 1));

        for (User user : users) {
            try {
                weeklyReportService.generateAndStore(user.getId(), weekStart, weekEnd);
            } catch (Exception e) {
                log.warn("Failed to generate weekly report for user {}: {}", user.getId(), e.getMessage());
            }
        }

        log.info("Weekly report scheduler done for {} users", users.size());
    }
}
