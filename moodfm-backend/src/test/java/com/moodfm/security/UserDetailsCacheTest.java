package com.moodfm.security;

import com.moodfm.config.CacheConfig;
import com.moodfm.domain.entity.User;
import com.moodfm.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.mockito.Mockito.*;

@SpringJUnitConfig(classes = {CacheConfig.class, UserDetailsCacheTest.TestConfig.class})
class UserDetailsCacheTest {

    @Configuration
    @EnableCaching
    static class TestConfig {
        @Bean
        UserMapper userMapper() {
            return Mockito.mock(UserMapper.class);
        }

        @Bean
        UserDetailsServiceImpl userDetailsService(UserMapper userMapper) {
            return new UserDetailsServiceImpl(userMapper);
        }
    }

    @Autowired
    UserDetailsService userDetailsService;

    @Autowired
    UserMapper userMapper;

    @Test
    void loadUserByUsername_isCachedWithinTtl() {
        User user = new User();
        user.setId(7L);
        user.setPasswordHash("hash");
        user.setStatus(1);
        user.setRole("USER");
        when(userMapper.selectById(7L)).thenReturn(user);

        userDetailsService.loadUserByUsername("7");
        userDetailsService.loadUserByUsername("7");

        verify(userMapper, times(1)).selectById(7L);
    }
}
