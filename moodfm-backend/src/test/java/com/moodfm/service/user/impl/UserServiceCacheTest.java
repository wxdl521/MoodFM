package com.moodfm.service.user.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodfm.domain.entity.User;
import com.moodfm.mapper.UserMapper;
import com.moodfm.mapper.UserProfileMapper;
import com.moodfm.domain.vo.UserVO;
import com.moodfm.common.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@SpringBootTest
@ExtendWith(SpringExtension.class)
class UserServiceCacheTest {

    @SpyBean
    private UserMapper userMapper;

    @Autowired
    private com.moodfm.service.user.UserService userService;

    @Test
    void secondCallHitsCacheAndSkipsMapper() {
        // First call: populates cache
        UserVO first = userService.getCurrentUser(1L);
        // Second call: should come from cache
        UserVO second = userService.getCurrentUser(1L);

        assertEquals(first.getId(), second.getId());
        // selectById should only be called once (first call), second hit from Caffeine cache
        verify(userMapper, times(1)).selectById(1L);
    }
}
