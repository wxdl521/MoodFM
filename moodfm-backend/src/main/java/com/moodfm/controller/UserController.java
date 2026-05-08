package com.moodfm.controller;

import com.moodfm.common.result.R;
import com.moodfm.domain.dto.user.ChangePasswordRequest;
import com.moodfm.domain.dto.user.PreferencesRequest;
import com.moodfm.domain.dto.user.UpdateProfileRequest;
import com.moodfm.domain.vo.PreferencesVO;
import com.moodfm.domain.vo.UserVO;
import com.moodfm.service.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Tag(name = "用户", description = "用户信息管理")
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    private Long uid(UserDetails ud) {
        return Long.parseLong(ud.getUsername());
    }

    // ── 兼容 auth.js 的 GET /user/me ──────────────────────────────────
    @Operation(summary = "获取当前用户信息")
    @GetMapping("/me")
    public R<UserVO> me(@AuthenticationPrincipal UserDetails ud) {
        return R.ok(userService.getCurrentUser(uid(ud)));
    }

    // ── Profile ───────────────────────────────────────────────────────
    @Operation(summary = "获取个人资料（同 /me）")
    @GetMapping("/profile")
    public R<UserVO> getProfile(@AuthenticationPrincipal UserDetails ud) {
        return R.ok(userService.getCurrentUser(uid(ud)));
    }

    @Operation(summary = "更新个人资料")
    @PutMapping("/profile")
    public R<UserVO> updateProfile(@RequestBody UpdateProfileRequest request,
                                   @AuthenticationPrincipal UserDetails ud) {
        userService.updateProfile(uid(ud), request);
        return R.ok(userService.getCurrentUser(uid(ud)));
    }

    // ── Password ──────────────────────────────────────────────────────
    @Operation(summary = "修改密码")
    @PostMapping("/password")
    public R<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                  @AuthenticationPrincipal UserDetails ud) {
        userService.changePassword(uid(ud), request);
        return R.ok();
    }

    // ── Avatar ────────────────────────────────────────────────────────
    @Operation(summary = "上传头像")
    @PostMapping("/avatar")
    public R<Map<String, String>> uploadAvatar(@RequestParam("file") MultipartFile file,
                                               @AuthenticationPrincipal UserDetails ud) {
        String url = userService.uploadAvatar(uid(ud), file);
        return R.ok(Map.of("avatarUrl", url));
    }

    // ── Preferences ───────────────────────────────────────────────────
    @Operation(summary = "获取偏好设置")
    @GetMapping("/preferences")
    public R<PreferencesVO> getPreferences(@AuthenticationPrincipal UserDetails ud) {
        return R.ok(userService.getPreferences(uid(ud)));
    }

    @Operation(summary = "保存偏好设置（流派 / 语言）")
    @PutMapping("/preferences")
    public R<Void> savePreferences(@RequestBody PreferencesRequest request,
                                   @AuthenticationPrincipal UserDetails ud) {
        userService.savePreferences(uid(ud), request);
        return R.ok();
    }

    // ── Devices (stub) ────────────────────────────────────────────────
    @Operation(summary = "获取登录设备列表")
    @GetMapping("/devices")
    public R<List<Object>> getDevices(@AuthenticationPrincipal UserDetails ud) {
        return R.ok(List.of()); // 暂无设备表，返回空列表
    }

    @Operation(summary = "吊销指定设备")
    @DeleteMapping("/devices/{id}")
    public R<Void> revokeDevice(@PathVariable Long id,
                                @AuthenticationPrincipal UserDetails ud) {
        return R.ok(); // stub
    }

    // ── Account ───────────────────────────────────────────────────────
    @Operation(summary = "注销账号")
    @DeleteMapping("/account")
    public R<Void> deleteAccount(@AuthenticationPrincipal UserDetails ud) {
        userService.deleteAccount(uid(ud));
        return R.ok();
    }
}
