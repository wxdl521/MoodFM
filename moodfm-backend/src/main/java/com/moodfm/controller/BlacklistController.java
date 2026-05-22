package com.moodfm.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodfm.common.result.R;
import com.moodfm.common.util.SecurityUtil;
import com.moodfm.domain.entity.UserProfile;
import com.moodfm.mapper.UserProfileMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Tag(name = "黑名单", description = "黑名单管理")
@RestController
@RequestMapping("/api/user/blacklist")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class BlacklistController {

    private final UserProfileMapper userProfileMapper;
    private final ObjectMapper objectMapper;

    // ── GET — returns all blacklist entries ──────────────────────────────

    @Operation(summary = "获取黑名单列表")
    @GetMapping
    public R<List<Map<String, Object>>> getAll(@AuthenticationPrincipal UserDetails ud) {
        UserProfile profile = userProfileMapper.selectByUserId(SecurityUtil.getUserId(ud));
        if (profile == null) return R.ok(Collections.emptyList());

        List<Map<String, Object>> result = new ArrayList<>();
        parseEntries(profile.getBlacklistArtists(), "artist").forEach(e -> {
            e.put("artistLabel", e.get("value"));
            result.add(e);
        });
        parseEntries(profile.getBlacklistSongs(), "song").forEach(result::add);
        parseEntries(profile.getBlacklistKeywords(), "keyword").forEach(result::add);
        return R.ok(result);
    }

    // ── POST — add a blacklist entry ─────────────────────────────────────

    @Data
    public static class AddBlacklistRequest {
        private String type;    // artist | song | keyword
        private String value;   // artist name / songId / keyword
        private String label;   // display label (song title, etc.)
    }

    @Operation(summary = "添加黑名单条目")
    @PostMapping
    public R<Map<String, Object>> add(@RequestBody AddBlacklistRequest request,
                                      @AuthenticationPrincipal UserDetails ud) {
        Long userId = SecurityUtil.getUserId(ud);
        String type = request.getType();
        String value = request.getValue();
        if (type == null || value == null) return R.fail(400, "type and value are required");

        switch (type) {
            case "artist"  -> addToBlacklist(userId, "blacklistArtists", value);
            case "song"    -> {
                Map<String, String> entry = new LinkedHashMap<>();
                entry.put("id", value);
                entry.put("title", request.getLabel() != null ? request.getLabel() : value);
                addToBlacklist(userId, "blacklistSongs", entry);
            }
            case "keyword" -> addToBlacklist(userId, "blacklistKeywords", value);
            default        -> { return R.fail(400, "invalid type: " + type); }
        }

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", type + ":" + value);
        entry.put("type", type);
        entry.put("value", value);
        entry.put("label", request.getLabel());
        entry.put("addedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return R.ok(entry);
    }

    // ── DELETE — remove a blacklist entry ────────────────────────────────

    @Operation(summary = "移除黑名单条目")
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable String id,
                          @AuthenticationPrincipal UserDetails ud) {
        Long userId = SecurityUtil.getUserId(ud);
        int colonIdx = id.indexOf(':');
        if (colonIdx <= 0) return R.fail(400, "invalid id format");

        String type  = id.substring(0, colonIdx);
        String value = id.substring(colonIdx + 1);

        switch (type) {
            case "artist"  -> removeFromBlacklist(userId, "blacklistArtists", value, false);
            case "song"    -> removeFromBlacklist(userId, "blacklistSongs", value, true);
            case "keyword" -> removeFromBlacklist(userId, "blacklistKeywords", value, false);
            default        -> { return R.fail(400, "invalid type: " + type); }
        }
        return R.ok();
    }

    // ── JSON helpers ─────────────────────────────────────────────────────

    private List<Map<String, Object>> parseEntries(String json, String type) {
        List<Map<String, Object>> entries = new ArrayList<>();
        if (json == null || json.isBlank() || "[]".equals(json)) return entries;
        try {
            List<Object> raw = objectMapper.readValue(json, new TypeReference<>() {});
            String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            for (Object item : raw) {
                Map<String, Object> entry = new LinkedHashMap<>();
                if (item instanceof String s) {
                    entry.put("id", type + ":" + s);
                    entry.put("type", type);
                    entry.put("value", s);
                    entry.put("label", s);
                } else if (item instanceof Map<?, ?> m) {
                    String songId = m.get("id") != null ? String.valueOf(m.get("id")) : "";
                    String title  = m.get("title") != null ? String.valueOf(m.get("title")) : songId;
                    entry.put("id", type + ":" + songId);
                    entry.put("type", type);
                    entry.put("value", songId);
                    entry.put("label", title);
                }
                entry.put("addedAt", now);
                entries.add(entry);
            }
        } catch (Exception e) {
            // silently skip malformed entries
        }
        return entries;
    }

    @SuppressWarnings("unchecked")
    private void addToBlacklist(Long userId, String column, Object value) {
        UserProfile profile = userProfileMapper.selectByUserId(userId);
        if (profile == null) return;
        String json = getBlacklistColumn(profile, column);
        List<Object> list;
        try {
            list = objectMapper.readValue(
                    (json == null || json.isBlank()) ? "[]" : json,
                    new TypeReference<>() {});
        } catch (Exception e) {
            list = new ArrayList<>();
        }
        list.add(value);
        try {
            String updated = objectMapper.writeValueAsString(list);
            setBlacklistColumn(profile, column, updated);
            profile.setUpdatedAt(LocalDateTime.now());
            userProfileMapper.updateById(profile);
        } catch (Exception e) {
            // serialization failure — skip silently
        }
    }

    @SuppressWarnings("unchecked")
    private void removeFromBlacklist(Long userId, String column, String value, boolean isSong) {
        UserProfile profile = userProfileMapper.selectByUserId(userId);
        if (profile == null) return;
        String json = getBlacklistColumn(profile, column);
        if (json == null || json.isBlank() || "[]".equals(json)) return;
        try {
            List<Object> list = objectMapper.readValue(json, new TypeReference<>() {});
            if (isSong) {
                list.removeIf(item -> item instanceof Map<?, ?> m
                        && value.equals(String.valueOf(m.get("id"))));
            } else {
                list.removeIf(item -> value.equals(item));
            }
            String updated = objectMapper.writeValueAsString(list);
            setBlacklistColumn(profile, column, updated);
            profile.setUpdatedAt(LocalDateTime.now());
            userProfileMapper.updateById(profile);
        } catch (Exception e) {
            // parse failure — skip silently
        }
    }

    private String getBlacklistColumn(UserProfile p, String column) {
        return switch (column) {
            case "blacklistArtists"  -> p.getBlacklistArtists();
            case "blacklistSongs"    -> p.getBlacklistSongs();
            case "blacklistKeywords" -> p.getBlacklistKeywords();
            default -> null;
        };
    }

    private void setBlacklistColumn(UserProfile p, String column, String value) {
        switch (column) {
            case "blacklistArtists"  -> p.setBlacklistArtists(value);
            case "blacklistSongs"    -> p.setBlacklistSongs(value);
            case "blacklistKeywords" -> p.setBlacklistKeywords(value);
        }
    }
}
