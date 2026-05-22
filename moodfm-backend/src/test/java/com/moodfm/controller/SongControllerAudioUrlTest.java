package com.moodfm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodfm.common.exception.GlobalExceptionHandler;
import com.moodfm.common.util.JwtUtil;
import com.moodfm.service.song.SongService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SongController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class SongControllerAudioUrlTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean SongService songService;
    @MockBean JwtUtil jwtUtil;
    @MockBean UserDetailsService userDetailsService;
    @MockBean StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(1L);
    }

    @Test
    @WithMockUser(username = "1")
    void getAudioUrl_returnsUrl_whenResolved() throws Exception {
        when(songService.getAudioUrl(1L, 42L))
                .thenReturn("https://cdn.example.com/song/42.mp3");

        mockMvc.perform(get("/api/songs/42/audio-url"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").value("https://cdn.example.com/song/42.mp3"));
    }

    @Test
    @WithMockUser(username = "1")
    void getAudioUrl_returns404_whenNotResolved() throws Exception {
        when(songService.getAudioUrl(1L, 99L)).thenReturn(null);

        mockMvc.perform(get("/api/songs/99/audio-url"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @WithMockUser(username = "1")
    void batchAudioUrls_returnsMap() throws Exception {
        when(songService.getAudioUrls(eq(1L), anyList()))
                .thenReturn(Map.of(1L, "https://cdn.example.com/1.mp3",
                                   2L, "https://cdn.example.com/2.mp3"));

        mockMvc.perform(post("/api/songs/batch-audio-urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(1, 2, 3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data['1']").value("https://cdn.example.com/1.mp3"))
                .andExpect(jsonPath("$.data['2']").value("https://cdn.example.com/2.mp3"));
    }
}
