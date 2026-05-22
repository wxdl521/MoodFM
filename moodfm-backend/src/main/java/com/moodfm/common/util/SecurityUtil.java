package com.moodfm.common.util;

import org.springframework.security.core.userdetails.UserDetails;

public final class SecurityUtil {

    private SecurityUtil() {}

    /** 从 Spring Security UserDetails 中解析用户 ID（username 存储的是 userId）。 */
    public static Long getUserId(UserDetails userDetails) {
        return Long.parseLong(userDetails.getUsername());
    }
}
