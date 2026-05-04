package com.moodfm.controller;

import com.moodfm.common.result.R;
import com.moodfm.domain.dto.user.PreferencesRequest;
import com.moodfm.domain.vo.UserVO;
import com.moodfm.service.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@Tag(name = "用户", description = "用户信息管理")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/me")
    public R<UserVO> me(@AuthenticationPrincipal UserDetails userDetails) {
        Long userId = Long.parseLong(userDetails.getUsername());
        return R.ok(userService.getCurrentUser(userId));
    }

    @Operation(summary = "保存用户偏好（流派 / 语言）")
    @PutMapping("/preferences")
    public R<Void> savePreferences(
            @RequestBody PreferencesRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = Long.parseLong(userDetails.getUsername());
        userService.savePreferences(userId, request);
        return R.ok();
    }
}
