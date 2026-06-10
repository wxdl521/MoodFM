package com.moodfm.service.user.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodfm.domain.entity.User;
import com.moodfm.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceDeleteAccountTest {

    @Mock
    UserMapper userMapper;

    @Mock
    StringRedisTemplate redisTemplate;

    @Mock
    SetOperations<String, String> setOps;

    UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userMapper, null, null, null, redisTemplate, new ObjectMapper());
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOps);
    }

    @Test
    void deleteAccount_usesLogicDelete_notStatusFlag() {
        when(setOps.members(anyString())).thenReturn(java.util.Set.of());

        userService.deleteAccount(42L);

        // 走 MyBatis-Plus 逻辑删除（设置 deleted=1），而不是把 status 改成 0
        verify(userMapper).deleteById(42L);
        verify(userMapper, never()).updateById(any(User.class));
        // 删除账号前必须吊销该用户的所有 refresh token（防止已删除账号的会话继续可用）
        verify(setOps).members(anyString());
    }
}
