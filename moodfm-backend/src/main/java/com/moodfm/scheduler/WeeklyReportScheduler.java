package com.moodfm.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodfm.domain.entity.User;
import com.moodfm.domain.entity.UserProfile;
import com.moodfm.domain.vo.NotificationPrefsVO;
import com.moodfm.mapper.UserMapper;
import com.moodfm.mapper.UserProfileMapper;
import com.moodfm.service.report.impl.WeeklyReportServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeeklyReportScheduler {

    private final UserMapper userMapper;
    private final WeeklyReportServiceImpl weeklyReportService;
    private final UserProfileMapper userProfileMapper;
    private final ObjectMapper objectMapper;

    /**
     * 每小时运行一次，检查当前是否匹配用户的周报推送偏好（day + hour）。
     * 默认：周一 9 点。
     */
    @Scheduled(cron = "0 0 * * * *")
    public void generateWeeklyReports() {
        LocalDate today = LocalDate.now();
        int currentHour = LocalTime.now().getHour();
        // Java DayOfWeek: MONDAY=1 ... SUNDAY=7; our VO uses 0=Mon ... 6=Sun
        int todayIndex = today.getDayOfWeek().getValue() - 1; // 0-based

        log.info("Weekly report scheduler check: day={} hour={}", todayIndex, currentHour);

        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>().eq(User::getStatus, 1));

        int generated = 0;
        for (User user : users) {
            try {
                NotificationPrefsVO prefs = loadNotificationPrefs(user.getId());

                // Check if weekly report is enabled
                if (!prefs.isWeeklyReport()) continue;

                // Check day preference (default 0 = Monday)
                int preferredDay = prefs.getWeeklyReportDay() != null ? prefs.getWeeklyReportDay() : 0;
                if (todayIndex != preferredDay) continue;

                // Check hour preference (default 9)
                int preferredHour = prefs.getWeeklyReportHour() != null ? prefs.getWeeklyReportHour() : 9;
                if (currentHour != preferredHour) continue;

                // Generate report for the previous week
                LocalDate weekStart = today.with(DayOfWeek.MONDAY).minusWeeks(1);
                LocalDate weekEnd = weekStart.plusWeeks(1);

                weeklyReportService.generateAndStore(user.getId(), weekStart, weekEnd);
                generated++;
            } catch (Exception e) {
                log.warn("Failed to generate weekly report for user {}: {}", user.getId(), e.getMessage());
            }
        }

        if (generated > 0) {
            log.info("Weekly report scheduler done: generated for {} users", generated);
        }
    }

    private NotificationPrefsVO loadNotificationPrefs(Long userId) {
        try {
            UserProfile profile = userProfileMapper.selectByUserId(userId);
            if (profile == null || profile.getNotificationPrefs() == null) {
                return NotificationPrefsVO.builder()
                        .weeklyReport(true).cookieExpiry(true).newFeatures(true)
                        .weeklyReportDay(0).weeklyReportHour(9).build();
            }
            return objectMapper.readValue(profile.getNotificationPrefs(), NotificationPrefsVO.class);
        } catch (Exception e) {
            log.warn("Failed to load notification prefs for user {}, using defaults", userId, e);
            return NotificationPrefsVO.builder()
                    .weeklyReport(true).cookieExpiry(true).newFeatures(true)
                    .weeklyReportDay(0).weeklyReportHour(9).build();
        }
    }
}
