package com.moodfm.controller;

import com.moodfm.common.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Tag(name = "管理员-配置", description = "Feature Flags / KV 配置 / AI 权重（Redis 存储）")
@RestController
@RequestMapping("/api/admin/config")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AdminConfigController {

    private final StringRedisTemplate redisTemplate;

    private static final String FLAGS_KEY   = "admin:feature_flags";
    private static final String KV_KEY      = "admin:kv_config";
    private static final String WEIGHTS_KEY = "admin:ai_weights";
    private static final String PROMPT_KEY  = "admin:ai_prompt";

    // ── Feature Flags ──────────────────────────────

    @Operation(summary = "获取所有功能开关")
    @GetMapping("/flags")
    public R<Map<String, String>> getFlags() {
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(FLAGS_KEY);
        if (raw.isEmpty()) return R.ok(defaultFlags());
        Map<String, String> result = new LinkedHashMap<>();
        raw.forEach((k, v) -> result.put(String.valueOf(k), String.valueOf(v)));
        return R.ok(result);
    }

    @Operation(summary = "更新单个功能开关")
    @PutMapping("/flags/{key}")
    public R<Void> setFlag(@PathVariable String key, @RequestBody Map<String, Boolean> body) {
        Boolean val = body.get("enabled");
        if (val == null) return R.fail(400, "缺少 enabled 字段");
        redisTemplate.opsForHash().put(FLAGS_KEY, key, val.toString());
        return R.ok();
    }

    // ── KV Config ──────────────────────────────────

    @Operation(summary = "获取所有 KV 配置")
    @GetMapping("/kv")
    public R<Map<String, String>> getKv() {
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(KV_KEY);
        if (raw.isEmpty()) return R.ok(defaultKv());
        Map<String, String> result = new LinkedHashMap<>();
        raw.forEach((k, v) -> result.put(String.valueOf(k), String.valueOf(v)));
        return R.ok(result);
    }

    @Operation(summary = "更新单个 KV 配置")
    @PutMapping("/kv/{key}")
    public R<Void> setKv(@PathVariable String key, @RequestBody Map<String, String> body) {
        String val = body.get("value");
        if (val == null) return R.fail(400, "缺少 value 字段");
        redisTemplate.opsForHash().put(KV_KEY, key, val);
        return R.ok();
    }

    // ── AI Weights ──────────────────────────────────

    @Operation(summary = "获取 AI 推荐权重")
    @GetMapping("/ai-weights")
    public R<Map<String, String>> getAiWeights() {
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(WEIGHTS_KEY);
        if (raw.isEmpty()) return R.ok(defaultWeights());
        Map<String, String> result = new LinkedHashMap<>();
        raw.forEach((k, v) -> result.put(String.valueOf(k), String.valueOf(v)));
        return R.ok(result);
    }

    @Operation(summary = "批量保存 AI 推荐权重")
    @PutMapping("/ai-weights")
    public R<Void> saveAiWeights(@RequestBody Map<String, String> weights) {
        redisTemplate.opsForHash().putAll(WEIGHTS_KEY, weights);
        return R.ok();
    }

    // ── AI Prompt ──────────────────────────────────

    @Operation(summary = "获取 Prompt 模板")
    @GetMapping("/ai-prompt")
    public R<Map<String, String>> getPrompt() {
        String val = redisTemplate.opsForValue().get(PROMPT_KEY);
        if (val == null) val = defaultPrompt();
        return R.ok(Map.of("prompt", val));
    }

    @Operation(summary = "保存 Prompt 模板")
    @PutMapping("/ai-prompt")
    public R<Void> savePrompt(@RequestBody Map<String, String> body) {
        String prompt = body.get("prompt");
        if (prompt == null) return R.fail(400, "缺少 prompt 字段");
        redisTemplate.opsForValue().set(PROMPT_KEY, prompt);
        return R.ok();
    }

    // ── 默认值 ──────────────────────────────────────

    private Map<String, String> defaultFlags() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("ai_recommendations", "true");
        m.put("weekly_report", "true");
        m.put("cross_platform_sync", "true");
        m.put("share_long_image", "true");
        m.put("qr_login", "true");
        m.put("cookie_login", "true");
        m.put("mood_calendar", "false");
        m.put("social_profile", "false");
        m.put("maintenance_mode", "false");
        return m;
    }

    private Map<String, String> defaultKv() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("recommendation.count", "15");
        m.put("recommendation.replan_after", "3");
        m.put("session.max_duration_hours", "4");
        m.put("cookie.expiry_warn_days", "3");
        m.put("qrcode.timeout_seconds", "90");
        m.put("report.generation_day", "1");
        m.put("blacklist.max_per_user", "200");
        return m;
    }

    private Map<String, String> defaultWeights() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("positivity", "65");
        m.put("energy", "48");
        m.put("diversity", "72");
        m.put("novelty", "40");
        m.put("feedback", "58");
        m.put("contextual", "82");
        return m;
    }

    private String defaultPrompt() {
        return "你是 MoodFM 的 AI 推荐引擎。根据用户当前的心情描述和上下文，推荐最合适的歌曲列表。\n\n" +
               "用户输入：{{user_input}}\n当前时间：{{current_time}}\n用户历史偏好：{{user_profile}}\n" +
               "当前场景：{{scene}}\n\n请输出 JSON 格式的推荐列表，包含歌曲名、艺人、推荐理由。" +
               "推荐数量：{{count}} 首。\n推荐时考虑用户黑名单：{{blacklist}}。";
    }
}
