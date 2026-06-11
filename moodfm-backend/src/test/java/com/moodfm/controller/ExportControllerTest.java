package com.moodfm.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodfm.domain.entity.FeedbackEvent;
import com.moodfm.domain.entity.MoodSession;
import com.moodfm.domain.entity.PlayRecord;
import com.moodfm.domain.entity.User;
import com.moodfm.mapper.FeedbackEventMapper;
import com.moodfm.mapper.MoodSessionMapper;
import com.moodfm.mapper.PlayRecordMapper;
import com.moodfm.mapper.UserMapper;
import com.moodfm.common.util.JwtUtil;
import com.moodfm.security.JwtAuthFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ExportController.class)
@AutoConfigureMockMvc(addFilters = false)
class ExportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserMapper userMapper;

    @MockBean
    private PlayRecordMapper playRecordMapper;

    @MockBean
    private FeedbackEventMapper feedbackEventMapper;

    @MockBean
    private MoodSessionMapper moodSessionMapper;

    @MockBean
    private JwtAuthFilter jwtAuthFilter;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(1L);
    }

    @Test
    @WithMockUser(username = "1")
    void exportJsonReturnsAttachment() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setEmail("test@example.com");
        user.setCreatedAt(LocalDateTime.now());

        when(userMapper.selectById(1L)).thenReturn(user);
        when(playRecordMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(feedbackEventMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(moodSessionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        mockMvc.perform(get("/api/export/json"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=moodfm-export.json"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.user.username").value("testuser"))
                .andExpect(jsonPath("$.playRecords").isArray())
                .andExpect(jsonPath("$.feedbackEvents").isArray())
                .andExpect(jsonPath("$.moodSessions").isArray());
    }

    @Test
    @WithMockUser(username = "1")
    void exportCsvReturnsAttachment() throws Exception {
        when(playRecordMapper.selectHistory(eq(1L), isNull(), eq(100_001), eq(0)))
                .thenReturn(List.of(
                        Map.of("title", "Test Song", "artist", "Test Artist",
                                "platform", "netease", "action", "completed",
                                "playedSeconds", 120, "totalSeconds", 240,
                                "scene", "深夜", "playedAt", "2026-05-10T22:00:00")));

        mockMvc.perform(get("/api/export/csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=moodfm-playrecords.csv"))
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(containsString("Test Song")));
    }
}
