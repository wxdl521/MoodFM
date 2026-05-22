package com.moodfm.service.user.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodfm.common.constant.RedisKeys;
import com.moodfm.common.exception.BizException;
import com.moodfm.common.result.ResultCode;
import com.moodfm.common.util.JwtUtil;
import com.moodfm.domain.dto.auth.LoginRequest;
import com.moodfm.domain.entity.User;
import com.moodfm.mapper.UserMapper;
import com.moodfm.mapper.UserProfileMapper;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplLoginLockTest {

    private static final String ACCOUNT = "user@example.com";

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserProfileMapper userProfileMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(
                userMapper,
                userProfileMapper,
                passwordEncoder,
                jwtUtil,
                redisTemplate,
                new ObjectMapper()
        );
    }

    @Test
    void fifthFailedLoginLocksAccountForFifteenMinutes() {
        String failKey = RedisKeys.format(RedisKeys.LOGIN_FAIL_COUNT, ACCOUNT);
        String lockKey = RedisKeys.format(RedisKeys.LOGIN_LOCK, ACCOUNT);

        when(redisTemplate.hasKey(lockKey)).thenReturn(false);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user());
        when(passwordEncoder.matches("bad-password", "hash")).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(failKey)).thenReturn(5L);

        BizException ex = assertThrows(BizException.class, () -> userService.login(loginRequest("bad-password")));

        assertEquals(ResultCode.WRONG_PASSWORD.getCode(), ex.getCode());
        verify(redisTemplate).expire(failKey, Duration.ofMinutes(15));
        verify(valueOperations).set(lockKey, "1", Duration.ofMinutes(15));
    }

    @Test
    void lockedAccountFailsBeforePasswordCheck() {
        String lockKey = RedisKeys.format(RedisKeys.LOGIN_LOCK, ACCOUNT);
        when(redisTemplate.hasKey(lockKey)).thenReturn(true);

        BizException ex = assertThrows(BizException.class, () -> userService.login(loginRequest("any-password")));

        assertEquals(ResultCode.ACCOUNT_LOCKED.getCode(), ex.getCode());
        verifyNoInteractions(userMapper, passwordEncoder, jwtUtil);
    }

    @Test
    void successfulLoginClearsFailureAndLockKeys() {
        String failKey = RedisKeys.format(RedisKeys.LOGIN_FAIL_COUNT, ACCOUNT);
        String lockKey = RedisKeys.format(RedisKeys.LOGIN_LOCK, ACCOUNT);
        String refreshKey = RedisKeys.format(RedisKeys.REFRESH_TOKEN, "refresh-token");
        Claims claims = mock(Claims.class);

        when(redisTemplate.hasKey(lockKey)).thenReturn(false);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user());
        when(passwordEncoder.matches("correct-password", "hash")).thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(jwtUtil.generateAccessToken(1L, "listener", "USER")).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken(1L, false)).thenReturn("refresh-token");
        when(jwtUtil.parseToken("refresh-token")).thenReturn(claims);
        when(claims.getExpiration()).thenReturn(new Date(System.currentTimeMillis() + 60_000));

        userService.login(loginRequest("correct-password"));

        verify(redisTemplate).delete(failKey);
        verify(redisTemplate).delete(lockKey);
        verify(valueOperations).set(eq(refreshKey), eq("1"), any(Duration.class));
    }

    private LoginRequest loginRequest(String password) {
        LoginRequest request = new LoginRequest();
        request.setAccount(ACCOUNT);
        request.setPassword(password);
        return request;
    }

    private User user() {
        User user = new User();
        user.setId(1L);
        user.setUsername("listener");
        user.setEmail(ACCOUNT);
        user.setPasswordHash("hash");
        user.setRole("USER");
        user.setStatus(1);
        return user;
    }
}
