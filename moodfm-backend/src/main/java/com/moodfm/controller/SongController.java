package com.moodfm.controller;

import com.moodfm.common.result.R;
import com.moodfm.domain.vo.LyricLineVO;
import com.moodfm.domain.vo.SongVO;
import com.moodfm.service.song.SongService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "歌曲", description = "歌曲详情与红心接口")
@RestController
@RequestMapping("/api/songs")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class SongController {

    private final SongService songService;

    private Long uid(UserDetails ud) {
        return Long.parseLong(ud.getUsername());
    }

    @Operation(summary = "获取用户红心歌曲列表")
    @GetMapping("/liked")
    public R<List<SongVO>> getLikedSongs(@AuthenticationPrincipal UserDetails ud) {
        return R.ok(songService.getLikedSongs(uid(ud)));
    }

    @Operation(summary = "查询红心状态")
    @GetMapping("/{id}/liked")
    public R<Map<String, Boolean>> isLiked(@PathVariable Long id,
                                           @AuthenticationPrincipal UserDetails ud) {
        return R.ok(Map.of("liked", songService.isLiked(uid(ud), id)));
    }

    @Operation(summary = "切换红心状态")
    @PostMapping("/{id}/like")
    public R<Map<String, Boolean>> toggleLike(@PathVariable Long id,
                                              @AuthenticationPrincipal UserDetails ud) {
        boolean liked = songService.toggleLike(uid(ud), id);
        return R.ok(Map.of("liked", liked));
    }

    @Operation(summary = "获取歌曲详情")
    @GetMapping("/{id}")
    public R<SongVO> getSongDetail(@PathVariable Long id,
                                   @AuthenticationPrincipal UserDetails ud) {
        return R.ok(songService.getSongDetail(uid(ud), id));
    }

    @Operation(summary = "获取相似歌曲")
    @GetMapping("/{id}/similar")
    public R<List<SongVO>> getSimilarSongs(@PathVariable Long id,
                                           @AuthenticationPrincipal UserDetails ud) {
        return R.ok(songService.getSimilarSongs(uid(ud), id));
    }

    @Operation(summary = "获取歌词")
    @GetMapping("/{id}/lyrics")
    public R<List<LyricLineVO>> getLyrics(@PathVariable Long id,
                                          @AuthenticationPrincipal UserDetails ud) {
        return R.ok(songService.getLyrics(uid(ud), id));
    }

    @Operation(summary = "获取歌曲播放地址")
    @GetMapping("/{id}/audio-url")
    public R<Map<String, String>> getAudioUrl(@PathVariable Long id,
                                               @AuthenticationPrincipal UserDetails ud) {
        String url = songService.getAudioUrl(uid(ud), id);
        if (url == null || url.isBlank()) {
            return R.fail(404, "暂无可用播放地址");
        }
        Map<String, String> body = new java.util.HashMap<>();
        body.put("url", url);
        return R.ok(body);
    }

    @Operation(summary = "批量获取歌曲播放地址")
    @PostMapping("/batch-audio-urls")
    public R<Map<Long, String>> batchAudioUrls(@RequestBody List<Long> songIds,
                                                @AuthenticationPrincipal UserDetails ud) {
        return R.ok(songService.getAudioUrls(uid(ud), songIds));
    }
}
