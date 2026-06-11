package com.moodfm.security;

import com.moodfm.config.CacheConfig;
import com.moodfm.controller.admin.AdminController;
import com.moodfm.domain.entity.User;
import com.moodfm.mapper.PlayRecordMapper;
import com.moodfm.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Verifies that admin mutations (setUserStatus / setUserRole) evict the
 * userDetails cache so that JwtAuthFilter picks up ban / role changes
 * immediately instead of waiting for the 2-minute TTL.
 *
 * Key assertion: after an admin mutation, loadUserByUsername("7") must
 * hit the DB again (selectById call #3); if the eviction were missing it
 * would be a cache hit and selectById would total only 2.
 */
@SpringJUnitConfig(classes = {CacheConfig.class, UserDetailsCacheEvictionTest.TestConfig.class})
class UserDetailsCacheEvictionTest {

    @Configuration
    @EnableCaching
    static class TestConfig {

        @Bean
        UserMapper userMapper() {
            return Mockito.mock(UserMapper.class);
        }

        @Bean
        PlayRecordMapper playRecordMapper() {
            return Mockito.mock(PlayRecordMapper.class);
        }

        @Bean
        UserDetailsServiceImpl userDetailsServiceImpl(UserMapper userMapper) {
            return new UserDetailsServiceImpl(userMapper);
        }

        @Bean
        AdminController adminController(UserMapper userMapper, PlayRecordMapper playRecordMapper) {
            return new AdminController(userMapper, playRecordMapper);
        }
    }

    /** Inject as interface — the impl is JDK-proxied by @Cacheable */
    @Autowired
    UserDetailsService userDetailsService;

    /** Inject as concrete class — CGLIB-proxied, so concrete injection works */
    @Autowired
    AdminController adminController;

    /** The mock — we verify call counts on it */
    @Autowired
    UserMapper userMapper;

    @Autowired
    CacheManager cacheManager;

    @BeforeEach
    void resetState() {
        // Reset mock invocation counts so each test starts from zero
        Mockito.reset(userMapper);
        // Clear the userDetails cache so tests don't share cached entries
        var cache = cacheManager.getCache("userDetails");
        if (cache != null) cache.clear();
    }

    @Test
    void setUserStatus_evictsUserDetailsCache() {
        User user = new User();
        user.setId(7L);
        user.setPasswordHash("hash");
        user.setStatus(1);
        user.setRole("USER");
        when(userMapper.selectById(7L)).thenReturn(user);
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        // Step 1: warm the cache — selectById call #1
        userDetailsService.loadUserByUsername("7");

        // Step 2: admin bans the user — selectById call #2 (inside setUserStatus),
        //         updateById, then @CacheEvict fires and evicts userDetails["7"]
        adminController.setUserStatus(7L, Map.of("status", 0));

        // Step 3: loadUserByUsername must be a cache MISS (evicted) — selectById call #3
        userDetailsService.loadUserByUsername("7");

        // Without the @CacheEvict, step 3 would be a hit → only 2 selectById calls.
        // times(3) proves the eviction happened and the key type matched ("7" == "7").
        verify(userMapper, times(3)).selectById(7L);
    }

    @Test
    void setUserRole_evictsUserDetailsCache() {
        User user = new User();
        user.setId(7L);
        user.setPasswordHash("hash");
        user.setStatus(1);
        user.setRole("USER");
        when(userMapper.selectById(7L)).thenReturn(user);
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        // Step 1: warm the cache — selectById call #1
        userDetailsService.loadUserByUsername("7");

        // Step 2: admin promotes the user — selectById call #2, evicts cache
        adminController.setUserRole(7L, Map.of("role", "ADMIN"));

        // Step 3: cache MISS → selectById call #3
        userDetailsService.loadUserByUsername("7");

        verify(userMapper, times(3)).selectById(7L);
    }
}
