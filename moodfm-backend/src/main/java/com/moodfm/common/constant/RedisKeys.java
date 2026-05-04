package com.moodfm.common.constant;

public final class RedisKeys {

    private RedisKeys() {}

    // JWT
    public static final String JWT_BLACKLIST = "jwt:blacklist:%s";         // token jti
    public static final String USER_DEVICES = "user:devices:%d";           // userId

    // 用户
    public static final String LOGIN_FAIL_COUNT = "login:fail:%s";         // username
    public static final String LOGIN_LOCK = "login:lock:%s";               // username

    // Refresh Token
    public static final String REFRESH_TOKEN = "auth:refresh:%s";          // token

    // 平台绑定
    public static final String QR_LOGIN_KEY = "qrlogin:key:%s";            // platform:key
    public static final String QR_LOGIN_STATUS = "qrlogin:status:%s";      // platform:key

    // 播放队列
    public static final String USER_QUEUE = "queue:%d";                     // userId

    // AI 缓存
    public static final String MOOD_ANALYSIS = "mood:%s";                  // input hash

    // 歌曲 URL 缓存
    public static final String SONG_URL = "song:url:%s:%s";               // platform:songId

    // 平台 API 缓存
    public static final String API_CACHE = "api:%s:%s:%s";                // platform:endpoint:paramsHash

    public static String format(String template, Object... args) {
        return String.format(template, args);
    }
}
