package com.moodfm.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moodfm.common.result.R;
import com.moodfm.domain.entity.User;
import com.moodfm.mapper.PlayRecordMapper;
import com.moodfm.mapper.UserMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Tag(name = "管理员", description = "用户管理（需 ADMIN 角色）")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final UserMapper userMapper;
    private final PlayRecordMapper playRecordMapper;

    @Operation(summary = "系统概览统计")
    @GetMapping("/stats")
    public R<Map<String, Object>> getStats() {
        long totalUsers = userMapper.selectCount(null);

        String today = LocalDate.now().toString();

        // Active today: count distinct users who played something today
        List<Object> activeUserIds = playRecordMapper.selectObjs(
                new LambdaQueryWrapper<com.moodfm.domain.entity.PlayRecord>()
                        .select(com.moodfm.domain.entity.PlayRecord::getUserId)
                        .apply("DATE(played_at) = {0}", today)
                        .groupBy(com.moodfm.domain.entity.PlayRecord::getUserId));
        long activeToday = activeUserIds.size();

        long totalPlays = playRecordMapper.selectCount(null);

        // Total listening seconds → hours (single SUM query instead of full table scan)
        long totalSeconds = playRecordMapper.sumAllPlayedSeconds();
        double totalListeningHours = totalSeconds / 3600.0;

        long newUsersToday = userMapper.countNewUsersOnDate(today);

        return R.ok(Map.of(
                "totalUsers", totalUsers,
                "activeToday", activeToday,
                "totalPlays", totalPlays,
                "totalListeningHours", Math.round(totalListeningHours * 10.0) / 10.0,
                "newUsersToday", newUsersToday
        ));
    }

    @Operation(summary = "用户列表")
    @GetMapping("/users")
    public R<List<Map<String, Object>>> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        int size = Math.min(pageSize, 100);
        int offset = (Math.max(page, 1) - 1) * size;

        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .select(User::getId, User::getUsername, User::getEmail, User::getPhone,
                                User::getRole, User::getStatus, User::getCreatedAt)
                        .orderByDesc(User::getCreatedAt)
                        .last("LIMIT " + size + " OFFSET " + offset));

        List<Map<String, Object>> result = users.stream().map(u -> Map.<String, Object>of(
                "id", u.getId(),
                "username", u.getUsername() != null ? u.getUsername() : "",
                "email", u.getEmail() != null ? u.getEmail() : "",
                "phone", u.getPhone() != null ? u.getPhone() : "",
                "role", u.getRole() != null ? u.getRole() : "USER",
                "status", u.getStatus(),
                "createdAt", u.getCreatedAt() != null ? u.getCreatedAt().toString() : ""
        )).toList();

        return R.ok(result);
    }

    @Operation(summary = "启用/禁用用户")
    @PutMapping("/users/{id}/status")
    public R<Void> setUserStatus(
            @PathVariable Long id,
            @RequestBody Map<String, Integer> body) {
        Integer status = body.get("status");
        if (status == null || (status != 0 && status != 1)) {
            return R.fail(400, "status 必须为 0 或 1");
        }
        User user = userMapper.selectById(id);
        if (user == null) return R.fail(404, "用户不存在");
        user.setStatus(status);
        userMapper.updateById(user);
        return R.ok();
    }

    @Operation(summary = "分配/修改用户角色")
    @PutMapping("/users/{id}/role")
    public R<Void> setUserRole(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String role = body.get("role");
        if (role == null || (!role.equals("ADMIN") && !role.equals("USER"))) {
            return R.fail(400, "role 必须为 ADMIN 或 USER");
        }
        User user = userMapper.selectById(id);
        if (user == null) return R.fail(404, "用户不存在");
        user.setRole(role);
        userMapper.updateById(user);
        return R.ok();
    }

    @Operation(summary = "最近注册用户（Dashboard 用）")
    @GetMapping("/users/recent")
    public R<List<Map<String, Object>>> getRecentUsers(
            @RequestParam(defaultValue = "5") int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);

        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .select(User::getId, User::getUsername, User::getEmail,
                                User::getCreatedAt, User::getRole)
                        .orderByDesc(User::getCreatedAt)
                        .last("LIMIT " + safeLimit));

        List<Map<String, Object>> result = users.stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername() != null ? u.getUsername() : "");
            m.put("email", u.getEmail() != null ? u.getEmail() : "");
            m.put("createdAt", u.getCreatedAt() != null ? u.getCreatedAt().toString() : "");
            m.put("role", u.getRole() != null ? u.getRole() : "USER");
            return m;
        }).toList();

        return R.ok(result);
    }

    @Operation(summary = "最近 N 天每日 DAU 数据（折线图）")
    @GetMapping("/dashboard/activity")
    public R<Map<String, Object>> getDashboardActivity(
            @RequestParam(defaultValue = "7") int days) {
        int safeDays = Math.min(Math.max(days, 1), 90);

        // Query grouped DAU from play_records
        List<Map<String, Object>> rows = playRecordMapper.selectDailyDau(safeDays);

        // Build a date→dau lookup from DB results
        DateTimeFormatter labelFmt = DateTimeFormatter.ofPattern("MM/dd");
        Map<String, Long> dauByDate = rows.stream().collect(Collectors.toMap(
                r -> r.get("date").toString(),
                r -> ((Number) r.get("dau")).longValue()
        ));

        // Fill every day in the requested range (zero if no data)
        List<String> labels = new ArrayList<>();
        List<Long> dauList = new ArrayList<>();
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(safeDays - 1L);
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            labels.add(d.format(labelFmt));
            dauList.add(dauByDate.getOrDefault(d.toString(), 0L));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("labels", labels);
        data.put("dau", dauList);
        return R.ok(data);
    }
}
