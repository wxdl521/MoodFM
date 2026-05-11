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
import com.moodfm.security.JwtAuthFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
        when(playRecordMapper.selectHistory(eq(1L), isNull(), eq(Integer.MAX_VALUE), eq(0)))
                .thenReturn(List.of(
                        Map.of("title", "Test Song", "artist", "Test Artist",
                                "platform", "netease", "action", "completed",
                                "playedSeconds", 120, "totalSeconds", 240,
                                "scene", "深夜", "playedAt", "2026-05-10T22:00:00")));

        mockMvc.perform(get("/api/export/csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=moodfm-playrecords.csv"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN));
    }
}
