package com.moodfm.common.result;

import lombok.Getter;

@Getter
public enum ResultCode {
    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或 Token 已过期"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    TOO_MANY_REQUESTS(429, "请求过于频繁"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    // 业务错误码 (1xxx)
    USER_NOT_FOUND(1001, "用户不存在"),
    USER_ALREADY_EXISTS(1002, "邮箱或手机号已注册"),
    WRONG_PASSWORD(1003, "密码错误"),
    ACCOUNT_LOCKED(1004, "账号已被锁定，请15分钟后再试"),
    ACCOUNT_DISABLED(1005, "账号已注销"),

    // 验证码错误码 (11xx)
    INVALID_SMS_CODE(1101, "短信验证码错误或已过期"),
    INVALID_EMAIL_CODE(1102, "邮箱验证码错误或已过期"),
    OTP_TOO_MANY_ATTEMPTS(1105, "验证码尝试次数过多，请重新获取"),

    // 平台绑定错误码 (2xxx)
    PLATFORM_NOT_BOUND(2001, "音乐平台账号未绑定"),
    PLATFORM_COOKIE_INVALID(2002, "平台账号 Cookie 已失效，请重新绑定"),
    COOKIE_INVALID(2003, "Cookie 无效或已过期，请重新获取"),
    PHONE_CODE_SEND_FAILED(2004, "短信验证码发送失败"),
    PHONE_CODE_VERIFY_FAILED(2005, "短信验证码错误或已过期"),

    // AI 错误码 (3xxx)
    RECALL_FAILED(3002, "歌曲召回失败");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
