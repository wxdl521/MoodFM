package com.moodfm.common.constant;

public final class RedisKeys {

    private RedisKeys() {}

    // JWT
    public static final String JWT_BLACKLIST = "jwt:blacklist:%s";         // token jti
    public static final String USER_DEVICES = "user:devices:%d";           // userId

    // 用户
    public static final String LOGIN_FAIL_COUNT = "login:fail:%s";         // username
    public static final String LOGIN_LOCK = "login:lock:%s";               // username
    public static final String RATE_LIMIT = "ratelimit:%s:%s";             // userId:minute

    // Refresh Token
    public static final String REFRESH_TOKEN = "auth:refresh:%s";          // token

    // 短信验证码
    public static final String SMS_CODE = "sms:code:%s";                   // phone
    public static final String SMS_ATTEMPTS = "sms:attempts:%s";           // phone

    // 邮箱验证码
    public static final String EMAIL_VERIFY = "email:verify:%s";           // email
    public static final String EMAIL_ATTEMPTS = "email:attempts:%s";       // email

    // 验证码发送限流
    public static final String SMS_SEND_COOLDOWN = "sms:cooldown:%s";     // phone
    public static final String SMS_SEND_DAILY = "sms:daily:%s:%s";        // phone:yyyyMMdd
    public static final String EMAIL_SEND_COOLDOWN = "email:cooldown:%s"; // email
    public static final String EMAIL_SEND_DAILY = "email:daily:%s:%s";    // email:yyyyMMdd

    // 平台绑定
    public static final String QR_LOGIN_STATUS = "qrlogin:status:%s";      // platform:key

    /** 用户所有 refresh token 的集合，用于批量吊销（无需全局 KEYS 扫描） */
    public static final String USER_REFRESH_TOKENS = "auth:user_tokens:%d"; // userId

    // 播放队列
    public static final String USER_QUEUE = "queue:%d";                     // userId

    // 会话时长控制
    public static final String SESSION_TTL = "session:ttl:%d";              // sessionId
    /** 电台会话使用的音乐平台（与 SESSION_TTL 同寿命） */
    public static final String SESSION_PLATFORM = "session:platform:%d";    // sessionId

    /** 用户队列 re-rank 触发计数器 */
    public static final String QUEUE_RERANK = "queue:rerank:%d";  // userId

    public static String format(String template, Object... args) {
        return String.format(template, args);
    }
}
