package com.moodfm.client.music;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
}
