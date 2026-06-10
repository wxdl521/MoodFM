package com.moodfm.service.user.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodfm.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceOtpRateLimitTest {

    @Mock
    StringRedisTemplate redisTemplate;

    @Mock
    ValueOperations<String, String> valueOps;

    UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(null, null, null, null, redisTemplate, new ObjectMapper());
        ReflectionTestUtils.setField(userService, "activeProfile", "test");
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void sendSmsCode_blockedDuringCooldown() {
        when(redisTemplate.hasKey(startsWith("sms:cooldown:"))).thenReturn(true);

        assertThatThrownBy(() -> userService.sendSmsCode("13800000000"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("频繁");
    }

    @Test
    void sendSmsCode_blockedAfterDailyLimit() {
        when(redisTemplate.hasKey(startsWith("sms:cooldown:"))).thenReturn(false);
        when(valueOps.increment(startsWith("sms:daily:"))).thenReturn(11L);

        assertThatThrownBy(() -> userService.sendSmsCode("13800000000"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("上限");

        // 关键保证：被每日上限拒绝的请求不得设置冷却 key（否则会消耗冷却窗口）
        verify(valueOps, never()).set(startsWith("sms:cooldown:"), anyString(), any(Duration.class));
    }

    @Test
    void sendSmsCode_firstSendStoresCodeAndCooldown() {
        when(redisTemplate.hasKey(startsWith("sms:cooldown:"))).thenReturn(false);
        when(valueOps.increment(startsWith("sms:daily:"))).thenReturn(1L);

        userService.sendSmsCode("13800000000");

        verify(valueOps).set(startsWith("sms:code:"), anyString(), eq(Duration.ofMinutes(5)));
        verify(valueOps).set(startsWith("sms:cooldown:"), eq("1"), eq(Duration.ofSeconds(60)));
    }

    @Test
    void sendEmailVerification_blockedDuringCooldown() {
        when(redisTemplate.hasKey(startsWith("email:cooldown:"))).thenReturn(true);

        assertThatThrownBy(() -> userService.sendEmailVerification("a@b.com"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("频繁");
    }
}
