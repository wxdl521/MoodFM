package com.moodfm.controller;

import com.moodfm.common.result.R;
import com.moodfm.service.playlist.SmartPlaylistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "智能歌单", description = "基于规则自动生成的智能歌单")
@RestController
@RequestMapping("/api/playlists/smart")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class SmartPlaylistController {

    private final SmartPlaylistService smartPlaylistService;

    private Long uid(UserDetails ud) {
        return Long.parseLong(ud.getUsername());
    }

    @Operation(summary = "获取所有智能歌单概览")
    @GetMapping
    public R<List<Map<String, Object>>> listSmartPlaylists(
            @AuthenticationPrincipal UserDetails ud) {
        return R.ok(smartPlaylistService.generateSmartPlaylists(uid(ud)));
    }

    @Operation(summary = "获取指定智能歌单的曲目")
    @GetMapping("/{type}")
    public R<Map<String, Object>> getSmartPlaylist(
            @PathVariable String type,
            @AuthenticationPrincipal UserDetails ud) {
        return R.ok(smartPlaylistService.getSmartPlaylist(uid(ud), type));
    }
}
