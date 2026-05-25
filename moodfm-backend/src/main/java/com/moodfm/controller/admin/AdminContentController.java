package com.moodfm.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moodfm.common.result.R;
import com.moodfm.domain.entity.*;
import com.moodfm.mapper.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Tag(name = "管理员-内容", description = "黑名单 / 通知 / 审计日志")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AdminContentController {

    private final GlobalBlacklistMapper blacklistMapper;
    private final AdminNotificationMapper notificationMapper;
    private final AdminAuditLogMapper auditLogMapper;

    // ── 黑名单 ──────────────────────────────────────

    @Operation(summary = "查询全局黑名单")
    @GetMapping("/blacklist")
    public R<List<GlobalBlacklist>> listBlacklist(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String q) {
        var wrapper = new LambdaQueryWrapper<GlobalBlacklist>()
                .orderByDesc(GlobalBlacklist::getCreatedAt);
        if (type != null && !type.isBlank()) wrapper.eq(GlobalBlacklist::getType, type);
        if (q != null && !q.isBlank())
            wrapper.and(w -> w.like(GlobalBlacklist::getValue, q).or().like(GlobalBlacklist::getArtist, q));
        return R.ok(blacklistMapper.selectList(wrapper));
    }

    @Operation(summary = "添加全局黑名单")
    @PostMapping("/blacklist")
    public R<GlobalBlacklist> addBlacklist(@RequestBody GlobalBlacklist item) {
        if (item.getAddedBy() == null) item.setAddedBy("admin");
        item.setCreatedAt(LocalDateTime.now());
        blacklistMapper.insert(item);
        writeAudit("admin", "添加" + typeLabel(item.getType()) + "黑名单：" + item.getValue(),
                "音乐管理", "info");
        return R.ok(item);
    }

    @Operation(summary = "删除全局黑名单条目")
    @DeleteMapping("/blacklist/{id}")
    public R<Void> removeBlacklist(@PathVariable Long id) {
        GlobalBlacklist item = blacklistMapper.selectById(id);
        if (item == null) return R.fail(404, "条目不存在");
        blacklistMapper.deleteById(id);
        writeAudit("admin", "移除" + typeLabel(item.getType()) + "黑名单：" + item.getValue(),
                "音乐管理", "ok");
        return R.ok();
    }

    private String typeLabel(String type) {
        if ("artist".equals(type)) return "歌手";
        if ("song".equals(type)) return "歌曲";
        return "关键词";
    }

    // ── 通知 ──────────────────────────────────────

    @Operation(summary = "通知列表")
    @GetMapping("/notifications")
    public R<List<AdminNotification>> listNotifications() {
        return R.ok(notificationMapper.selectList(
                new LambdaQueryWrapper<AdminNotification>().orderByDesc(AdminNotification::getCreatedAt)));
    }

    @Operation(summary = "创建/保存通知（草稿或排期）")
    @PostMapping("/notifications")
    public R<AdminNotification> createNotification(@RequestBody AdminNotification notif) {
        notif.setCreatedBy("admin");
        notif.setCreatedAt(LocalDateTime.now());
        notif.setUpdatedAt(LocalDateTime.now());
        if (notif.getSentCount() == null) notif.setSentCount(0);
        if (notif.getOpenedCount() == null) notif.setOpenedCount(0);
        if (notif.getStatus() == null) notif.setStatus("draft");
        notificationMapper.insert(notif);
        return R.ok(notif);
    }

    @Operation(summary = "发送通知（立即）")
    @PostMapping("/notifications/{id}/send")
    public R<Void> sendNotification(@PathVariable Long id) {
        AdminNotification notif = notificationMapper.selectById(id);
        if (notif == null) return R.fail(404, "通知不存在");
        notif.setStatus("sent");
        notif.setSentAt(LocalDateTime.now());
        // 模拟发送数量（实际接入推送服务后替换）
        notif.setSentCount(1000);
        notif.setUpdatedAt(LocalDateTime.now());
        notificationMapper.updateById(notif);
        writeAudit("admin", "发送通知：" + notif.getTitle(), "通知管理", "info");
        return R.ok();
    }

    @Operation(summary = "删除通知")
    @DeleteMapping("/notifications/{id}")
    public R<Void> deleteNotification(@PathVariable Long id) {
        notificationMapper.deleteById(id);
        return R.ok();
    }

    // ── 审计日志 ──────────────────────────────────────

    @Operation(summary = "审计日志列表")
    @GetMapping("/audit-log")
    public R<List<AdminAuditLog>> listAuditLog(
            @RequestParam(defaultValue = "50") int limit) {
        int size = Math.min(limit, 200);
        return R.ok(auditLogMapper.selectList(
                new LambdaQueryWrapper<AdminAuditLog>()
                        .orderByDesc(AdminAuditLog::getCreatedAt)
                        .last("LIMIT " + size)));
    }

    // 供其他 Controller 调用
    private void writeAudit(String operator, String operation, String module, String level) {
        AdminAuditLog log = new AdminAuditLog();
        log.setOperator(operator);
        log.setOperation(operation);
        log.setModule(module);
        log.setLevel(level);
        log.setCreatedAt(LocalDateTime.now());
        auditLogMapper.insert(log);
    }
}
