package com.moodfm.client.music;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class MusicApiClient {

    @Value("${app.music-adapter.url}")
    private String adapterUrl;

    private final ObjectMapper objectMapper;

    /** 平台名转小写，匹配 adapter 路由（NETEASE → netease） */
    private String route(String platform) {
        return platform.toLowerCase();
    }

    // -------- 二维码登录 --------

    @CircuitBreaker(name = "music-adapter", fallbackMethod = "fallbackQrKey")
    public String generateQrKey(String platform) {
        try {
            String json = RestClient.create()
                    .get()
                    .uri(adapterUrl + "/{p}/qr/key", route(platform))
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(json).path("data").path("unikey").asText();
        } catch (Exception e) {
            log.error("Failed to generate QR key for {}", platform, e);
            throw new RuntimeException("获取二维码失败", e);
        }
    }

    @CircuitBreaker(name = "music-adapter", fallbackMethod = "fallbackQrImage")
    public Map<String, String> createQrCode(String platform, String key) {
        try {
            String json = RestClient.create()
                    .get()
                    .uri(adapterUrl + "/{p}/qr/create?key={key}", route(platform), key)
                    .retrieve()
                    .body(String.class);
            JsonNode node = objectMapper.readTree(json).path("data");
            return Map.of(
                    "qrimg", node.path("qrimg").asText(),
                    "qrurl", node.path("qrurl").asText()
            );
        } catch (Exception e) {
            log.error("Failed to create QR code", e);
            throw new RuntimeException("生成二维码图片失败", e);
        }
    }

    @CircuitBreaker(name = "music-adapter")
    public JsonNode checkQrStatus(String platform, String key) {
        try {
            String json = RestClient.create()
                    .get()
                    .uri(adapterUrl + "/{p}/qr/check?key={key}", route(platform), key)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.error("Failed to check QR status", e);
            throw new RuntimeException("检查二维码状态失败", e);
        }
    }

    // -------- 用户歌单/红心 --------

    @CircuitBreaker(name = "music-adapter", fallbackMethod = "fallbackSongs")
    public JsonNode getUserLikedSongs(String platform, String cookie) {
        try {
            String json = RestClient.create()
                    .get()
                    .uri(adapterUrl + "/{p}/user/liked-songs", route(platform))
                    .header("X-Cookie", cookie)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("Failed to get liked songs from {}", platform, e);
            return objectMapper.createObjectNode();
        }
    }

    @CircuitBreaker(name = "music-adapter", fallbackMethod = "fallbackSongs")
    public JsonNode getRecommendSongs(String platform, String cookie) {
        try {
            String json = RestClient.create()
                    .get()
                    .uri(adapterUrl + "/{p}/recommend/songs", route(platform))
                    .header("X-Cookie", cookie)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("Failed to get recommend songs from {}", platform, e);
            return objectMapper.createObjectNode();
        }
    }

    @CircuitBreaker(name = "music-adapter", fallbackMethod = "fallbackSongs")
    public JsonNode searchSongs(String platform, String keywords, int limit) {
        try {
            String json = RestClient.create()
                    .get()
                    .uri(adapterUrl + "/{p}/search?keywords={kw}&limit={limit}", route(platform), keywords, limit)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("Search failed for keywords: {}", keywords, e);
            return objectMapper.createObjectNode();
        }
    }

    @CircuitBreaker(name = "music-adapter")
    public String getSongUrl(String platform, String songId, String cookie) {
        try {
            String json = RestClient.create()
                    .get()
                    .uri(adapterUrl + "/{p}/song/url?id={id}", route(platform), songId)
                    .header("X-Cookie", cookie)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(json).path("data").path(0).path("url").asText();
        } catch (Exception e) {
            log.warn("Failed to get song URL for {}", songId, e);
            return null;
        }
    }

    // -------- Fallbacks --------

    private String fallbackQrKey(String platform, Exception e) {
        throw new RuntimeException("音乐适配服务暂时不可用");
    }

    private Map<String, String> fallbackQrImage(String platform, String key, Exception e) {
        throw new RuntimeException("音乐适配服务暂时不可用");
    }

    private JsonNode fallbackSongs(String platform, String cookie, Exception e) {
        return objectMapper.createObjectNode();
    }

    private JsonNode fallbackSongs(String platform, String keywords, int limit, Exception e) {
        return objectMapper.createObjectNode();
    }
}
