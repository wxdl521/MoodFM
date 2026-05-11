package com.moodfm.config;

import com.moodfm.common.constant.RedisKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitInterceptorTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RateLimitInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new RateLimitInterceptor(redisTemplate);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void blocksAuthenticatedUserAfterSixtyRequestsInCurrentMinute() throws Exception {
        authenticateAs("42");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(startsWith("ratelimit:42:"))).thenReturn(61L);

        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(new MockHttpServletRequest(), response, new Object());

        assertFalse(allowed);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), response.getStatus());
    }

    @Test
    void setsSixtySecondTtlForFirstRequestInCurrentMinute() throws Exception {
        authenticateAs("42");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(startsWith("ratelimit:42:"))).thenReturn(1L);

        boolean allowed = interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object());

        assertTrue(allowed);
        verify(redisTemplate).expire(startsWith("ratelimit:42:"), eq(Duration.ofSeconds(60)));
    }

    @Test
    void skipsAnonymousRequests() throws Exception {
        boolean allowed = interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object());

        assertTrue(allowed);
        verifyNoInteractions(redisTemplate);
    }

    private void authenticateAs(String userId) {
        User principal = new User(userId, "password", List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
