package com.moodfm.controller;

import com.moodfm.common.result.R;
import com.moodfm.domain.dto.auth.LoginRequest;
import com.moodfm.domain.dto.auth.RegisterRequest;
import com.moodfm.domain.vo.LoginVO;
import com.moodfm.service.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "认证", description = "注册、登录、刷新 Token")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @Operation(summary = "注册")
    @PostMapping("/register")
    public R<LoginVO> register(@Valid @RequestBody RegisterRequest request) {
        return R.ok(userService.register(request));
    }

    @Operation(summary = "登录")
    @PostMapping("/login")
    public R<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return R.ok(userService.login(request));
    }

    @Operation(summary = "刷新 Access Token")
    @PostMapping("/refresh")
    public R<LoginVO> refresh(@RequestParam String refreshToken) {
        return R.ok(userService.refreshToken(refreshToken));
    }

    @Operation(summary = "登出")
    @PostMapping("/logout")
    public R<Void> logout(@RequestHeader("Authorization") String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            userService.logout(authHeader.substring(7));
        }
        return R.ok();
    }
}
