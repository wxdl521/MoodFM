package com.moodfm.controller;

import com.moodfm.common.result.R;
import com.moodfm.domain.dto.feedback.PlaybackFeedbackDto;
import com.moodfm.domain.dto.radio.BatchFeedbackRequest;
import com.moodfm.domain.dto.radio.MoodInputRequest;
import com.moodfm.domain.vo.RadioQueueVO;
import com.moodfm.domain.vo.SessionSummaryVO;
import com.moodfm.domain.vo.SongVO;
import com.moodfm.mapper.MoodSessionMapper;
import com.moodfm.service.feedback.FeedbackService;
import com.moodfm.service.player.PlayerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Tag(name = "AI 电台", description = "心情电台核心接口")
@RestController
@RequestMapping("/api/radio")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class RadioController {

    private final PlayerService playerService;
    private final FeedbackService feedbackService;
    private final MoodSessionMapper moodSessionMapper;

    private Long uid(UserDetails ud) {
        return Long.parseLong(ud.getUsername());
    }

    // ── 核心播放接口 ──────────────────────────────────────────────────

    @Operation(summary = "启动电台（输入心情，返回歌曲队列）")
    @PostMapping("/start")
    public R<RadioQueueVO> startRadio(@Valid @RequestBody MoodInputRequest request,
                                      @AuthenticationPrincipal UserDetails ud) {
        return R.ok(playerService.startRadio(uid(ud), request));
    }

    @Operation(summary = "获取下一批歌曲")
    @GetMapping("/next")
    public R<List<SongVO>> getNext(@RequestParam Long sessionId,
                                   @AuthenticationPrincipal UserDetails ud) {
        return R.ok(playerService.getNextBatch(uid(ud), sessionId));
    }

    @Operation(summary = "获取歌曲播放地址")
    @GetMapping("/url")
    public R<String> getSongUrl(@RequestParam String platform,
                                @RequestParam String songId,
                                @AuthenticationPrincipal UserDetails ud) {
        return R.ok(playerService.getSongUrl(uid(ud), platform, songId));
    }

    // ── 反馈接口 ──────────────────────────────────────────────────────

    @Operation(summary = "单条反馈上报")
    @PostMapping("/feedback")
    public R<Void> feedback(@Valid @RequestBody PlaybackFeedbackDto dto,
                            @AuthenticationPrincipal UserDetails ud) {
        feedbackService.record(uid(ud), dto);
        return R.ok();
    }

    @Operation(summary = "批量反馈上报")
    @PostMapping("/feedback/batch")
    public R<Void> batchFeedback(@Valid @RequestBody BatchFeedbackRequest request,
                                 @AuthenticationPrincipal UserDetails ud) {
        Long userId = uid(ud);
        for (PlaybackFeedbackDto dto : request.getEvents()) {
            if (dto.getSessionId() != null && dto.getSongId() != null) {
                feedbackService.record(userId, dto);
            }
        }
        return R.ok();
    }

    // ── 历史会话 ──────────────────────────────────────────────────────

    @Operation(summary = "获取最近电台会话（断点续播用）")
    @GetMapping("/sessions")
    public R<List<SessionSummaryVO>> getSessions(
            @RequestParam(defaultValue = "5") int limit,
            @AuthenticationPrincipal UserDetails ud) {
        List<Map<String, Object>> rows = moodSessionMapper.selectRecentSessions(uid(ud), Math.min(limit, 20));
        List<SessionSummaryVO> result = rows.stream().map(r -> SessionSummaryVO.builder()
                .id(toLong(r.get("id")))
                .rawInput((String) r.get("rawInput"))
                .scene((String) r.get("scene"))
                .startedAt(r.get("startedAt") instanceof LocalDateTime ldt ? ldt : null)
                .build()).toList();
        return R.ok(result);
    }

    // ── 从歌单播放 (stub) ─────────────────────────────────────────────

    @Operation(summary = "从歌单启动电台")
    @PostMapping("/play-from-playlist")
    public R<Void> playFromPlaylist(@RequestBody Map<String, Object> body,
                                    @AuthenticationPrincipal UserDetails ud) {
        // PlaylistController 建成后在此接入
        return R.ok();
    }

    private Long toLong(Object val) {
        if (val == null) return null;
        if (val instanceof Long l) return l;
        if (val instanceof Number n) return n.longValue();
        try { return Long.parseLong(val.toString()); } catch (Exception e) { return null; }
    }
}
