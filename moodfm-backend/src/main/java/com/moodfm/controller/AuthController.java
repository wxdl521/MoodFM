package com.moodfm.controller;

import com.moodfm.common.result.R;
import com.moodfm.domain.dto.auth.*;
import com.moodfm.domain.vo.LoginVO;
import com.moodfm.service.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
    public R<LoginVO> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpReq) {
        LoginVO vo = userService.login(request);
        String ip = httpReq.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) ip = httpReq.getRemoteAddr();
        userService.recordDevice(vo.getUser().getId(), ip, httpReq.getHeader("User-Agent"));
        return R.ok(vo);
    }

    @Operation(summary = "刷新 Access Token")
    @PostMapping("/refresh")
    public R<LoginVO> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return R.ok(userService.refreshToken(request.getRefreshToken()));
    }

    @Operation(summary = "登出")
    @PostMapping("/logout")
    public R<Void> logout(@RequestHeader("Authorization") String authHeader,
                          @RequestBody(required = false) LogoutRequest request) {
        String accessToken = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            accessToken = authHeader.substring(7);
        }
        String refreshToken = (request != null) ? request.getRefreshToken() : null;
        userService.logout(accessToken, refreshToken);
        return R.ok();
    }

    // ── SMS OTP ───────────────────────────────────────────────────

    @Operation(summary = "发送短信验证码")
    @PostMapping("/sms/send")
    public R<Void> sendSmsCode(@Valid @RequestBody SmsSendRequest request) {
        userService.sendSmsCode(request.getPhone());
        return R.ok();
    }

    @Operation(summary = "验证短信验证码")
    @PostMapping("/sms/verify")
    public R<Void> verifySmsCode(@Valid @RequestBody SmsVerifyRequest request) {
        userService.verifySmsCode(request.getPhone(), request.getCode());
        return R.ok();
    }

    @Operation(summary = "手机号验证码登录")
    @PostMapping("/login/phone")
    public R<LoginVO> loginByPhone(@Valid @RequestBody PhoneLoginRequest request,
                                   HttpServletRequest httpReq) {
        LoginVO vo = userService.loginByPhone(request.getPhone(), request.getCode());
        String ip = httpReq.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) ip = httpReq.getRemoteAddr();
        userService.recordDevice(vo.getUser().getId(), ip, httpReq.getHeader("User-Agent"));
        return R.ok(vo);
    }

    // ── Email Verification ────────────────────────────────────────

    @Operation(summary = "发送邮箱验证码")
    @PostMapping("/email/send-verification")
    public R<Void> sendEmailVerification(@Valid @RequestBody EmailSendRequest request) {
        userService.sendEmailVerification(request.getEmail());
        return R.ok();
    }

    @Operation(summary = "验证邮箱验证码")
    @PostMapping("/email/verify")
    public R<Void> verifyEmail(@Valid @RequestBody EmailVerifyRequest request) {
        userService.verifyEmail(request.getEmail(), request.getCode());
        return R.ok();
    }
}
