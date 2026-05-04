package com.moodfm.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Component
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.access-token-expire-ms}")
    private long accessTokenExpireMs;

    @Value("${app.jwt.refresh-token-expire-ms}")
    private long refreshTokenExpireMs;

    @Value("${app.jwt.remember-me-expire-ms}")
    private long rememberMeExpireMs;

    public String generateAccessToken(Long userId, String username, String role) {
        return buildToken(userId, username, role, accessTokenExpireMs, "access");
    }

    public String generateRefreshToken(Long userId, boolean rememberMe) {
        long expiry = rememberMe ? rememberMeExpireMs : refreshTokenExpireMs;
        return buildToken(userId, null, null, expiry, "refresh");
    }

    private String buildToken(Long userId, String username, String role, long expireMs, String type) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .claims(Map.of(
                        "type", type,
                        "username", username != null ? username : "",
                        "role", role != null ? role : ""
                ))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expireMs))
                .signWith(getKey())
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getUserId(String token) {
        return Long.parseLong(parseToken(token).getSubject());
    }

    public String getJti(String token) {
        return parseToken(token).getId();
    }

    public boolean isAccessToken(String token) {
        return "access".equals(parseToken(token).get("type", String.class));
    }

    public boolean isExpired(String token) {
        return parseToken(token).getExpiration().before(new Date());
    }

    private SecretKey getKey() {
        // 直接用 UTF-8 字节，兼容 hex/任意字符串格式的 secret（至少 32 字节即可）
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
