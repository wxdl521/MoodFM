package com.moodfm.common.result;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ResultCode {
    SUCCESS(200, "操作成功", HttpStatus.OK),
    BAD_REQUEST(400, "请求参数错误", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(401, "未登录或 Token 已过期", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(403, "无权限", HttpStatus.FORBIDDEN),
    NOT_FOUND(404, "资源不存在", HttpStatus.NOT_FOUND),
    TOO_MANY_REQUESTS(429, "请求过于频繁", HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL_ERROR(500, "服务器内部错误", HttpStatus.INTERNAL_SERVER_ERROR),

    // 业务错误码 (1xxx) —— 登录类失败用 400，401 保留给 token 失效（前端会触发刷新流程）
    USER_NOT_FOUND(1001, "用户不存在", HttpStatus.NOT_FOUND),
    USER_ALREADY_EXISTS(1002, "邮箱或手机号已注册", HttpStatus.CONFLICT),
    WRONG_PASSWORD(1003, "密码错误", HttpStatus.BAD_REQUEST),
    ACCOUNT_LOCKED(1004, "账号已被锁定，请15分钟后再试", HttpStatus.LOCKED),
    ACCOUNT_DISABLED(1005, "账号已被禁用，请联系管理员", HttpStatus.FORBIDDEN),

    // 验证码错误码 (11xx)
    INVALID_SMS_CODE(1101, "短信验证码错误或已过期", HttpStatus.BAD_REQUEST),
    INVALID_EMAIL_CODE(1102, "邮箱验证码错误或已过期", HttpStatus.BAD_REQUEST),
    OTP_TOO_MANY_ATTEMPTS(1105, "验证码尝试次数过多，请重新获取", HttpStatus.TOO_MANY_REQUESTS),

    // 平台绑定错误码 (2xxx)
    PLATFORM_NOT_BOUND(2001, "音乐平台账号未绑定", HttpStatus.BAD_REQUEST),
    PLATFORM_COOKIE_INVALID(2002, "平台账号 Cookie 已失效，请重新绑定", HttpStatus.BAD_REQUEST),
    COOKIE_INVALID(2003, "Cookie 无效或已过期，请重新获取", HttpStatus.BAD_REQUEST),
    PHONE_CODE_SEND_FAILED(2004, "短信验证码发送失败", HttpStatus.BAD_GATEWAY),
    PHONE_CODE_VERIFY_FAILED(2005, "短信验证码错误或已过期", HttpStatus.BAD_REQUEST),

    // AI 错误码 (3xxx)
    RECALL_FAILED(3002, "歌曲召回失败", HttpStatus.BAD_GATEWAY);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ResultCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
