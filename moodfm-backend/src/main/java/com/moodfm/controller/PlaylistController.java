package com.moodfm.controller;

import com.moodfm.common.result.R;
import com.moodfm.common.util.SecurityUtil;
import com.moodfm.domain.vo.PlaylistVO;
import com.moodfm.service.playlist.PlaylistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "歌单", description = "用户歌单接口")
@RestController
@RequestMapping("/api/playlists")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class PlaylistController {

    private final PlaylistService playlistService;

    @Operation(summary = "获取用户所有歌单")
    @GetMapping
    public R<List<PlaylistVO>> listPlaylists(@AuthenticationPrincipal UserDetails ud) {
        return R.ok(playlistService.listPlaylists(SecurityUtil.getUserId(ud)));
    }

    @Operation(summary = "获取歌单详情（含曲目）")
    @GetMapping("/{id}")
    public R<PlaylistVO> getPlaylist(@PathVariable String id,
                                     @AuthenticationPrincipal UserDetails ud) {
        return R.ok(playlistService.getPlaylist(SecurityUtil.getUserId(ud), id));
    }
}
