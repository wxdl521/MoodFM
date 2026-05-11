package com.moodfm.client.music;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class SongApiClient {

    @Value("${app.music-adapter.url}")
    private String adapterUrl;

    private final ObjectMapper objectMapper;

    @CircuitBreaker(name = "music-adapter", fallbackMethod = "fallbackSongDetail")
    public JsonNode getSongDetail(String platform, String songId, String cookie) {
        try {
            String json = RestClient.create()
                    .get()
                    .uri(adapterUrl + "/{p}/song/detail?ids={id}", platform.toLowerCase(), songId)
                    .header("X-Cookie", cookie)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("getSongDetail failed for {}/{}", platform, songId, e);
            return objectMapper.createObjectNode();
        }
    }

    @CircuitBreaker(name = "music-adapter", fallbackMethod = "fallbackLyrics")
    public JsonNode getLyrics(String platform, String songId, String cookie) {
        try {
            String json = RestClient.create()
                    .get()
                    .uri(adapterUrl + "/{p}/lyric?id={id}", platform.toLowerCase(), songId)
                    .header("X-Cookie", cookie)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("getLyrics failed for {}/{}", platform, songId, e);
            return objectMapper.createObjectNode();
        }
    }

    @CircuitBreaker(name = "music-adapter", fallbackMethod = "fallbackSearch")
    public JsonNode searchSongs(String platform, String keywords, int limit) {
        try {
            String json = RestClient.create()
                    .get()
                    .uri(adapterUrl + "/{p}/search?keywords={kw}&limit={limit}", platform.toLowerCase(), keywords, limit)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("searchSongs failed: {}", keywords, e);
            return objectMapper.createObjectNode();
        }
    }

    @CircuitBreaker(name = "music-adapter", fallbackMethod = "fallbackLikedSongs")
    public JsonNode getUserLikedSongs(String platform, String cookie) {
        try {
            String json = RestClient.create()
                    .get()
                    .uri(adapterUrl + "/{p}/user/liked-songs", platform.toLowerCase())
                    .header("X-Cookie", cookie)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("getUserLikedSongs failed for {}", platform, e);
            return objectMapper.createObjectNode();
        }
    }

    /** 红心/取消红心歌曲 (like=true 红心, like=false 取消) */
    @CircuitBreaker(name = "music-adapter", fallbackMethod = "fallbackLikeSong")
    public boolean likeSong(String platform, String songId, boolean like, String cookie) {
        try {
            String json = RestClient.create()
                    .get()
                    .uri(adapterUrl + "/{p}/song/like?id={id}&like={like}",
                            platform.toLowerCase(), songId, like ? 1 : 0)
                    .header("X-Cookie", cookie)
                    .retrieve()
                    .body(String.class);
            JsonNode node = objectMapper.readTree(json);
            // Netease returns code 200 on success
            return node.path("code").asInt(0) == 200 || node.path("data").path("code").asInt(0) == 200;
        } catch (Exception e) {
            log.warn("likeSong failed for {}/{} like={}", platform, songId, like, e);
            return false;
        }
    }

    @CircuitBreaker(name = "music-adapter", fallbackMethod = "fallbackSimilar")
    public JsonNode getSimilarSongs(String platform, String songId, String cookie) {
        try {
            String json = RestClient.create()
                    .get()
                    .uri(adapterUrl + "/{p}/simi/song?id={id}", platform.toLowerCase(), songId)
                    .header("X-Cookie", cookie)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("getSimilarSongs failed for {}/{}", platform, songId, e);
            return objectMapper.createObjectNode();
        }
    }

    // ── Fallbacks ────────────────────────────────────────────────────────────

    private JsonNode fallbackSongDetail(String platform, String songId, String cookie, Exception e) {
        log.warn("Circuit open: getSongDetail {}/{}", platform, songId);
        return objectMapper.createObjectNode();
    }

    private JsonNode fallbackLyrics(String platform, String songId, String cookie, Exception e) {
        log.warn("Circuit open: getLyrics {}/{}", platform, songId);
        return objectMapper.createObjectNode();
    }

    private JsonNode fallbackSearch(String platform, String keywords, int limit, Exception e) {
        log.warn("Circuit open: searchSongs {}", keywords);
        return objectMapper.createObjectNode();
    }

    private JsonNode fallbackLikedSongs(String platform, String cookie, Exception e) {
        log.warn("Circuit open: getUserLikedSongs {}", platform);
        return objectMapper.createObjectNode();
    }

    private JsonNode fallbackSimilar(String platform, String songId, String cookie, Exception e) {
        log.warn("Circuit open: getSimilarSongs {}/{}", platform, songId);
        return objectMapper.createObjectNode();
    }

    private boolean fallbackLikeSong(String platform, String songId, boolean like, String cookie, Exception e) {
        log.warn("Circuit open: likeSong {}/{} like={}", platform, songId, like);
        return false;
    }
}
