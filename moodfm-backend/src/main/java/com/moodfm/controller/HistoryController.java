package com.moodfm.controller;

import com.moodfm.common.result.R;
import com.moodfm.domain.vo.PlayRecordVO;
import com.moodfm.mapper.PlayRecordMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Tag(name = "播放历史", description = "用户播放记录查询")
@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class HistoryController {

    private final PlayRecordMapper playRecordMapper;

    @Operation(summary = "获取播放历史（支持场景筛选、分页）")
    @GetMapping
    public R<List<PlayRecordVO>> getHistory(
            @RequestParam(required = false) String scene,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @AuthenticationPrincipal UserDetails ud) {

        long userId = Long.parseLong(ud.getUsername());
        int size = Math.min(pageSize, 50);
        int offset = (Math.max(page, 1) - 1) * size;

        List<Map<String, Object>> rows = playRecordMapper.selectHistory(userId, scene, size, offset);

        List<PlayRecordVO> result = rows.stream().map(r -> PlayRecordVO.builder()
                .id(toLong(r.get("id")))
                .sessionId(toLong(r.get("sessionId")))
                .songId(toString(r.get("songId")))
                .platform(toString(r.get("platform")))
                .title(toString(r.get("title")))
                .artist(toString(r.get("artist")))
                .coverUrl(toString(r.get("coverUrl")))
                .playedSeconds(toInt(r.get("playedSeconds")))
                .totalSeconds(toInt(r.get("totalSeconds")))
                .action(toString(r.get("action")))
                .playedAt(r.get("playedAt") instanceof LocalDateTime ldt ? ldt : null)
                .scene(toString(r.get("scene")))
                .build()).toList();

        return R.ok(result);
    }

    private Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Long l) return l;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return null; }
    }

    private Integer toInt(Object v) {
        if (v == null) return null;
        if (v instanceof Integer i) return i;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return null; }
    }

    private String toString(Object v) {
        return v == null ? null : v.toString();
    }
}
