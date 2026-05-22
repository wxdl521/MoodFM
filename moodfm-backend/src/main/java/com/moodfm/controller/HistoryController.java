package com.moodfm.controller;

import com.moodfm.common.result.R;
import com.moodfm.common.util.SecurityUtil;
import com.moodfm.domain.vo.HistoryPageVO;
import com.moodfm.service.history.HistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@Tag(name = "播放历史", description = "用户播放记录查询")
@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class HistoryController {

    private final HistoryService historyService;

    @Operation(summary = "清除全部播放历史")
    @DeleteMapping("/all")
    public R<Void> clearHistory(@AuthenticationPrincipal UserDetails ud) {
        historyService.clearAll(SecurityUtil.getUserId(ud));
        return R.ok();
    }

    @Operation(summary = "获取播放历史（支持场景筛选、分页）")
    @GetMapping
    public R<HistoryPageVO> getHistory(
            @RequestParam(required = false) String scene,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @AuthenticationPrincipal UserDetails ud) {

        long userId = SecurityUtil.getUserId(ud);
        return R.ok(historyService.getHistory(userId, scene, page, pageSize));
    }
}
