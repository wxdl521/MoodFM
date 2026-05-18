package com.moodfm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodfm.common.exception.GlobalExceptionHandler;
import com.moodfm.common.util.JwtUtil;
import com.moodfm.domain.vo.SearchResultVO;
import com.moodfm.domain.vo.SongVO;
import com.moodfm.service.search.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SearchController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class SearchControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean SearchService searchService;
    @MockBean JwtUtil jwtUtil;
    @MockBean UserDetailsService userDetailsService;
    @MockBean StringRedisTemplate stringRedisTemplate;

    @Test
    @WithMockUser(username = "1")
    void search_keywordMode_returnsSongs() throws Exception {
        SongVO song = SongVO.builder()
                .id(1L).title("晴天").artist("周杰伦")
                .platform("netease").platformSongId("186001").build();
        SearchResultVO result = SearchResultVO.builder()
                .mode("keyword").query("晴天").songs(List.of(song)).build();

        when(searchService.search(eq(1L), eq("晴天"), eq("keyword"), eq(20)))
                .thenReturn(result);

        mockMvc.perform(get("/api/search").param("q", "晴天"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.mode").value("keyword"))
                .andExpect(jsonPath("$.data.songs[0].title").value("晴天"));
    }

    @Test
    @WithMockUser(username = "1")
    void search_blankQuery_returns400() throws Exception {
        mockMvc.perform(get("/api/search").param("q", "  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("搜索词不能为空"));
    }

    @Test
    @WithMockUser(username = "1")
    void search_moodMode_passesCorrectMode() throws Exception {
        SearchResultVO result = SearchResultVO.builder()
                .mode("mood").query("深夜忧郁").songs(List.of()).build();

        when(searchService.search(eq(1L), eq("深夜忧郁"), eq("mood"), eq(20)))
                .thenReturn(result);

        mockMvc.perform(get("/api/search").param("q", "深夜忧郁").param("mode", "mood"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.mode").value("mood"))
                .andExpect(jsonPath("$.data.songs").isArray());
    }

    @Test
    @WithMockUser(username = "1")
    void search_invalidMode_returns400() throws Exception {
        mockMvc.perform(get("/api/search").param("q", "test").param("mode", "fuzzy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("mode 仅支持 keyword / mood"));
    }
}
