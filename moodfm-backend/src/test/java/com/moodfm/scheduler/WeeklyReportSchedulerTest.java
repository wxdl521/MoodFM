package com.moodfm.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodfm.domain.entity.User;
import com.moodfm.domain.entity.UserProfile;
import com.moodfm.domain.vo.NotificationPrefsVO;
import com.moodfm.mapper.UserMapper;
import com.moodfm.mapper.UserProfileMapper;
import com.moodfm.service.report.impl.WeeklyReportServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure Mockito unit test for {@link WeeklyReportScheduler}. No Spring context.
 *
 * Strategy: The scheduler reads {@code LocalDate.now()} / {@code LocalTime.now()}
 * internally. We freeze time with {@link MockedStatic} so cron-style matching is
 * deterministic. The downstream {@link WeeklyReportServiceImpl} is mocked entirely;
 * its own behaviour is out of scope here.
 *
 * Pinned "now": 2026-05-25 (Monday) at 09:00. With default prefs (day=0=Mon, hour=9)
 * this matches the schedule window, so the service should be invoked.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WeeklyReportSchedulerTest {

    @Mock private UserMapper userMapper;
    @Mock private WeeklyReportServiceImpl weeklyReportService;
    @Mock private UserProfileMapper userProfileMapper;

    // Use a real ObjectMapper so prefs JSON round-trips correctly.
    private final ObjectMapper objectMapper = new ObjectMapper();

    private WeeklyReportScheduler scheduler;

    // Frozen time: Monday 2026-05-25 09:00.
    private static final LocalDate MONDAY = LocalDate.of(2026, 5, 25);
    private static final LocalTime NINE_AM = LocalTime.of(9, 0);

    private MockedStatic<LocalDate> dateMock;
    private MockedStatic<LocalTime> timeMock;

    @BeforeEach
    void setUp() {
        scheduler = new WeeklyReportScheduler(
                userMapper, weeklyReportService, userProfileMapper, objectMapper);

        // Freeze "now" but allow other LocalDate/LocalTime factory calls (of, plus, etc.)
        // to delegate to the real implementations.
        dateMock = Mockito.mockStatic(LocalDate.class, Mockito.CALLS_REAL_METHODS);
        dateMock.when(LocalDate::now).thenReturn(MONDAY);

        timeMock = Mockito.mockStatic(LocalTime.class, Mockito.CALLS_REAL_METHODS);
        timeMock.when(LocalTime::now).thenReturn(NINE_AM);
    }

    @AfterEach
    void tearDown() {
        dateMock.close();
        timeMock.close();
    }

    // ─── Helpers ──────────────────────────────────────────────────

    private User user(long id) {
        User u = new User();
        u.setId(id);
        u.setUsername("u" + id);
        u.setStatus(1);
        return u;
    }

    private UserProfile profile(long userId, NotificationPrefsVO prefs) throws Exception {
        UserProfile p = new UserProfile();
        p.setUserId(userId);
        if (prefs != null) {
            p.setNotificationPrefs(objectMapper.writeValueAsString(prefs));
        }
        return p;
    }

    @SuppressWarnings("unchecked")
    private void mockActiveUsers(List<User> users) {
        when(userMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(users);
    }

    // ─── Test cases ───────────────────────────────────────────────

    /** Case 1: no active users → service must not be invoked. */
    @Test
    void noActiveUsersDoesNotCallService() {
        mockActiveUsers(new ArrayList<>());

        scheduler.generateWeeklyReports();

        verify(weeklyReportService, never())
                .generateAndStore(anyLong(), any(LocalDate.class), any(LocalDate.class));
    }

    /**
     * Case 2: when the downstream service throws (e.g. AI failure), the exception is
     * swallowed and other users continue to be processed. The real impl already has a
     * fallback narrative path; here we simulate the worst case where the call itself
     * blows up and verify the scheduler stays resilient.
     */
    @Test
    void serviceExceptionIsSwallowedAndOtherUsersStillProcessed() throws Exception {
        User u1 = user(1L);
        User u2 = user(2L);
        mockActiveUsers(List.of(u1, u2));

        // Both users have a profile that matches the schedule window.
        NotificationPrefsVO prefs = NotificationPrefsVO.builder()
                .weeklyReport(true).weeklyReportDay(0).weeklyReportHour(9).build();
        when(userProfileMapper.selectByUserId(1L)).thenReturn(profile(1L, prefs));
        when(userProfileMapper.selectByUserId(2L)).thenReturn(profile(2L, prefs));

        when(weeklyReportService.generateAndStore(eq(1L), any(), any()))
                .thenThrow(new RuntimeException("AI down"));

        scheduler.generateWeeklyReports();

        // u1 attempted (and threw), u2 still attempted.
        verify(weeklyReportService).generateAndStore(eq(1L), any(), any());
        verify(weeklyReportService).generateAndStore(eq(2L), any(), any());
    }

    /** Case 3: normal generation path — matching prefs trigger exactly one service call. */
    @Test
    void matchingPrefsTriggersGenerateAndStore() throws Exception {
        User u = user(42L);
        mockActiveUsers(List.of(u));

        NotificationPrefsVO prefs = NotificationPrefsVO.builder()
                .weeklyReport(true).weeklyReportDay(0).weeklyReportHour(9).build();
        when(userProfileMapper.selectByUserId(42L)).thenReturn(profile(42L, prefs));

        scheduler.generateWeeklyReports();

        verify(weeklyReportService, times(1))
                .generateAndStore(eq(42L), any(LocalDate.class), any(LocalDate.class));
    }

    /** Case 4: three active users, all opted-in & matching → three service calls. */
    @Test
    void multipleMatchingUsersAllTriggerService() throws Exception {
        List<User> users = List.of(user(1L), user(2L), user(3L));
        mockActiveUsers(users);

        NotificationPrefsVO prefs = NotificationPrefsVO.builder()
                .weeklyReport(true).weeklyReportDay(0).weeklyReportHour(9).build();
        for (User u : users) {
            when(userProfileMapper.selectByUserId(u.getId())).thenReturn(profile(u.getId(), prefs));
        }

        scheduler.generateWeeklyReports();

        verify(weeklyReportService, times(3))
                .generateAndStore(anyLong(), any(LocalDate.class), any(LocalDate.class));
        for (User u : users) {
            verify(weeklyReportService).generateAndStore(eq(u.getId()), any(), any());
        }
    }

    /** Case 5: user opted out of weekly reports → no service call. */
    @Test
    void userOptedOutIsSkipped() throws Exception {
        User u = user(7L);
        mockActiveUsers(List.of(u));

        NotificationPrefsVO prefs = NotificationPrefsVO.builder()
                .weeklyReport(false).weeklyReportDay(0).weeklyReportHour(9).build();
        when(userProfileMapper.selectByUserId(7L)).thenReturn(profile(7L, prefs));

        scheduler.generateWeeklyReports();

        verify(weeklyReportService, never()).generateAndStore(anyLong(), any(), any());
    }

    /** Case 6: hour doesn't match user's preferred hour → no service call. */
    @Test
    void hourMismatchIsSkipped() throws Exception {
        User u = user(8L);
        mockActiveUsers(List.of(u));

        // Prefers 10:00 but frozen now is 09:00.
        NotificationPrefsVO prefs = NotificationPrefsVO.builder()
                .weeklyReport(true).weeklyReportDay(0).weeklyReportHour(10).build();
        when(userProfileMapper.selectByUserId(8L)).thenReturn(profile(8L, prefs));

        scheduler.generateWeeklyReports();

        verify(weeklyReportService, never()).generateAndStore(anyLong(), any(), any());
    }

    /** Case 7: day-of-week doesn't match user's preferred day → no service call. */
    @Test
    void dayMismatchIsSkipped() throws Exception {
        User u = user(9L);
        mockActiveUsers(List.of(u));

        // Prefers Tuesday (index 1) but frozen now is Monday (index 0).
        NotificationPrefsVO prefs = NotificationPrefsVO.builder()
                .weeklyReport(true).weeklyReportDay(1).weeklyReportHour(9).build();
        when(userProfileMapper.selectByUserId(9L)).thenReturn(profile(9L, prefs));

        scheduler.generateWeeklyReports();

        verify(weeklyReportService, never()).generateAndStore(anyLong(), any(), any());
    }

    /**
     * Case 8: when no profile row exists, defaults kick in (weeklyReport=true,
     * day=0, hour=9), which match the frozen window → service IS invoked.
     */
    @Test
    void missingProfileFallsBackToDefaultsAndTriggers() {
        User u = user(11L);
        mockActiveUsers(List.of(u));
        when(userProfileMapper.selectByUserId(11L)).thenReturn(null);

        scheduler.generateWeeklyReports();

        verify(weeklyReportService, times(1))
                .generateAndStore(eq(11L), any(LocalDate.class), any(LocalDate.class));
    }

    /**
     * Case 9: verify the week-range computation — should be the previous ISO week,
     * Monday-to-Monday (end is exclusive). Frozen "today" is Mon 2026-05-25, so the
     * previous week is Mon 2026-05-18 → Mon 2026-05-25.
     */
    @Test
    void weekRangeIsPreviousIsoWeek() throws Exception {
        User u = user(100L);
        mockActiveUsers(List.of(u));
        NotificationPrefsVO prefs = NotificationPrefsVO.builder()
                .weeklyReport(true).weeklyReportDay(0).weeklyReportHour(9).build();
        when(userProfileMapper.selectByUserId(100L)).thenReturn(profile(100L, prefs));

        scheduler.generateWeeklyReports();

        ArgumentCaptor<LocalDate> startCap = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> endCap   = ArgumentCaptor.forClass(LocalDate.class);
        verify(weeklyReportService).generateAndStore(eq(100L), startCap.capture(), endCap.capture());

        assertEquals(LocalDate.of(2026, 5, 18), startCap.getValue(), "weekStart = prev Monday");
        assertEquals(LocalDate.of(2026, 5, 25), endCap.getValue(),   "weekEnd   = this Monday (exclusive)");
    }

    // no idempotency check in current impl — scheduler relies on hourly cron + day/hour
    // gating to avoid double-runs within the same hour, and does not query for an
    // existing report before insert.
}
