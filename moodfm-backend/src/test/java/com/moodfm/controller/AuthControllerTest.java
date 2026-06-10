package com.moodfm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodfm.common.exception.BizException;
import com.moodfm.common.exception.GlobalExceptionHandler;
import com.moodfm.common.result.ResultCode;
import com.moodfm.common.util.JwtUtil;
import com.moodfm.domain.dto.auth.LoginRequest;
import com.moodfm.service.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void loginReturnsAccountLockedCodeWhenServiceRejectsLockedAccount() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setAccount("user@example.com");
        request.setPassword("bad-password");

        when(userService.login(any(LoginRequest.class)))
                .thenThrow(new BizException(ResultCode.ACCOUNT_LOCKED));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value(ResultCode.ACCOUNT_LOCKED.getCode()))
                .andExpect(jsonPath("$.message").value(ResultCode.ACCOUNT_LOCKED.getMessage()));
    }

    @Test
    void loginWrongPasswordReturns400WithBizCode() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setAccount("user@example.com");
        request.setPassword("wrong-password-1");

        when(userService.login(any(LoginRequest.class)))
                .thenThrow(new BizException(ResultCode.WRONG_PASSWORD));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ResultCode.WRONG_PASSWORD.getCode()));
    }
}
