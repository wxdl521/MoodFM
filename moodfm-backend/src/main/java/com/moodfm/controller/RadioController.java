package com.moodfm.controller;

import com.moodfm.common.result.R;
import com.moodfm.domain.dto.radio.MoodInputRequest;
import com.moodfm.domain.vo.RadioQueueVO;
import com.moodfm.domain.vo.SongVO;
import com.moodfm.service.player.PlayerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "AI 电台", description = "心情电台核心接口")
@RestController
@RequestMapping("/api/radio")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class RadioController {

    private final PlayerService playerService;

    @Operation(summary = "启动电台（输入心情，返回歌曲队列）")
    @PostMapping("/start")
    public R<RadioQueueVO> startRadio(@Valid @RequestBody MoodInputRequest request,
                                       @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = Long.parseLong(userDetails.getUsername());
        return R.ok(playerService.startRadio(userId, request));
    }

    @Operation(summary = "获取下一批歌曲")
    @GetMapping("/next")
    public R<List<SongVO>> getNext(@RequestParam Long sessionId,
                                    @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = Long.parseLong(userDetails.getUsername());
        return R.ok(playerService.getNextBatch(userId, sessionId));
    }

    @Operation(summary = "获取歌曲播放地址")
    @GetMapping("/url")
    public R<String> getSongUrl(@RequestParam String platform,
                                 @RequestParam String songId,
                                 @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = Long.parseLong(userDetails.getUsername());
        return R.ok(playerService.getSongUrl(userId, platform, songId));
    }
}
