package com.moodfm.config;

import com.moodfm.controller.HealthController;
import com.moodfm.common.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = HealthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({WebMvcConfig.class, RateLimitInterceptor.class})
class RateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @MockBean
    private ValueOperations<String, String> valueOperations;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UserDetailsService userDetailsService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sameUserGetsTooManyRequestsOnSixtyFirstRequest() throws Exception {
        authenticateAs("42");
        AtomicLong requestCount = new AtomicLong();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(startsWith("ratelimit:42:")))
                .thenAnswer(invocation -> requestCount.incrementAndGet());

        for (int i = 0; i < 60; i++) {
            mockMvc.perform(get("/api/health"))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/health"))
                .andExpect(status().is(HttpStatus.TOO_MANY_REQUESTS.value()));
    }

    private void authenticateAs(String userId) {
        User principal = new User(userId, "password", List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

}
