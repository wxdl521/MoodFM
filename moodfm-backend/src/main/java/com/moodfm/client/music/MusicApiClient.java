package com.moodfm.client.music;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class MusicApiClient {

    @Value("${app.music-adapter.url}")
    private String adapterUrl;

    @Value("${app.music-adapter.secret:}")
    private String adapterSecret;

    private final ObjectMapper objectMapper;

    private RestClient restClient;

    @PostConstruct
    void init() {
        RestClient.Builder builder = RestClient.builder().baseUrl(adapterUrl);
        if (adapterSecret != null && !adapterSecret.isBlank()) {
            builder.defaultHeader("X-Adapter-Secret", adapterSecret);
        }
        this.restClient = builder.build();
    }

    /** 平台名转小写，匹配 adapter 路由（NETEASE → netease） */
    private String route(String platform) {
        return platform.toLowerCase();
    }

    // -------- 二维码登录 --------

    @CircuitBreaker(name = "music-adapter", fallbackMethod = "fallbackQrKey")
    public String generateQrKey(String platform) {
        try {
            String json = restClient
                    .get()
                    .uri("/{p}/qr/key", route(platform))
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
            String json = restClient
                    .get()
                    .uri("/{p}/qr/create?key={key}", route(platform), key)
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
            String json = restClient
                    .get()
                    .uri("/{p}/qr/check?key={key}", route(platform), key)
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
            String json = restClient
                    .get()
                    .uri("/{p}/user/liked-songs", route(platform))
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
            String json = restClient
                    .get()
                    .uri("/{p}/recommend/songs", route(platform))
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
    public JsonNode searchSongs(String platform, String keywords, int limit, String cookie) {
        try {
            var req = restClient
                    .get()
                    .uri("/{p}/search?keywords={kw}&limit={limit}", route(platform), keywords, limit);
            if (cookie != null && !cookie.isBlank()) {
                req = req.header("X-Cookie", cookie);
            }
            String json = req.retrieve().body(String.class);
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("Search failed for keywords: {}", keywords, e);
            return objectMapper.createObjectNode();
        }
    }

    @CircuitBreaker(name = "music-adapter")
    public String getSongUrl(String platform, String songId, String cookie) {
        try {
            String json = restClient
                    .get()
                    .uri("/{p}/song/url?id={id}", route(platform), songId)
                    .header("X-Cookie", cookie)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(json).path("data").path(0).path("url").asText();
        } catch (Exception e) {
            log.warn("Failed to get song URL for {}", songId, e);
            return null;
        }
    }

    /**
     * Batch fetch play URLs for multiple songs in a single API call.
     * NetEase song_url_v1 supports comma-separated IDs.
     *
     * @return map of platformSongId -> playUrl (entries with no URL are omitted)
     */
    @CircuitBreaker(name = "music-adapter", fallbackMethod = "fallbackSongUrls")
    public Map<String, String> getSongUrls(String platform, List<String> songIds, String cookie) {
        Map<String, String> urlMap = new HashMap<>();
        if (songIds == null || songIds.isEmpty()) return urlMap;
        try {
            // Build URI manually to avoid URL-encoding commas in the id parameter
            String idsParam = String.join(",", songIds);
            String url = adapterUrl + "/" + route(platform) + "/song/url?id=" + idsParam;
            String json = restClient
                    .get()
                    .uri(java.net.URI.create(url))
                    .header("X-Cookie", cookie)
                    .retrieve()
                    .body(String.class);
            com.fasterxml.jackson.databind.JsonNode data = objectMapper.readTree(json).path("data");
            if (data.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode item : data) {
                    // QQ Music IDs are alphanumeric strings (songmid), not numeric
                    String id = item.path("id").asText("");
                    if (id.isEmpty()) {
                        id = String.valueOf(item.path("id").asLong(0));
                    }
                    String songUrl = item.path("url").asText("");
                    if (!id.isEmpty() && !"0".equals(id) && !songUrl.isEmpty()) {
                        urlMap.put(id, songUrl);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to batch get song URLs for {} songs", songIds.size(), e);
        }
        return urlMap;
    }

    /**
     * Validate a cookie by calling the platform's profile endpoint.
     * Returns the platform username if valid, null otherwise.
     */
    public String validateCookie(String platform, String cookie) {
        try {
            String json = restClient
                    .get()
                    .uri("/{p}/user/profile", route(platform))
                    .header("X-Cookie", cookie)
                    .retrieve()
                    .body(String.class);
            JsonNode node = objectMapper.readTree(json);
            if (node.path("valid").asBoolean(false)) {
                return node.path("username").asText(null);
            }
        } catch (Exception e) {
            log.warn("Cookie validation failed for platform {}: {}", platform, e.getMessage());
        }
        return null;
    }

    /**
     * Send SMS verification code for phone-based platform login.
     * Returns the ticket string used in the verify step.
     */
    public String sendPhoneCode(String platform, String phone) {
        try {
            String json = restClient
                    .post()
                    .uri("/{p}/phone/code", route(platform))
                    .body(Map.of("phone", phone))
                    .retrieve()
                    .body(String.class);
            JsonNode node = objectMapper.readTree(json);
            if (node.path("code").asInt() == 200) {
                return node.path("data").path("ticket").asText(null);
            }
        } catch (Exception e) {
            log.error("Failed to send phone code for platform {}", platform, e);
        }
        return null;
    }

    /**
     * Verify SMS code and retrieve cookie + username.
     * Returns a two-element array: [cookie, username], or null on failure.
     */
    public String[] verifyPhoneCode(String platform, String phone, String code, String ticket) {
        try {
            String json = restClient
                    .post()
                    .uri("/{p}/phone/verify", route(platform))
                    .body(Map.of("phone", phone, "code", code, "ticket", ticket))
                    .retrieve()
                    .body(String.class);
            JsonNode node = objectMapper.readTree(json);
            if (node.path("code").asInt() == 803) {
                String cookie = node.path("cookie").asText(null);
                String username = node.path("account").asText("");
                if (cookie != null && !cookie.isBlank()) {
                    return new String[]{cookie, username};
                }
            }
        } catch (Exception e) {
            log.error("Failed to verify phone code for platform {}", platform, e);
        }
        return null;
    }

    /** 获取用户歌单列表 */
    @CircuitBreaker(name = "music-adapter", fallbackMethod = "fallbackNode")
    public JsonNode getUserPlaylists(String platform, String cookie) {
        try {
            String json = restClient
                    .get()
                    .uri("/{p}/user/playlists", route(platform))
                    .header("X-Cookie", cookie)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("Failed to get user playlists from {}", platform, e);
            return objectMapper.createObjectNode();
        }
    }

    /** 获取歌单曲目 */
    @CircuitBreaker(name = "music-adapter", fallbackMethod = "fallbackNode")
    public JsonNode getPlaylistTracks(String platform, String playlistId, String cookie) {
        try {
            String json = restClient
                    .get()
                    .uri("/{p}/playlist/tracks?id={id}", route(platform), playlistId)
                    .header("X-Cookie", cookie)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("Failed to get playlist tracks from {}", platform, e);
            return objectMapper.createObjectNode();
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

    private JsonNode fallbackSongs(String platform, String keywords, int limit, String cookie, Exception e) {
        return objectMapper.createObjectNode();
    }

    private JsonNode fallbackNode(Exception e) {
        return objectMapper.createObjectNode();
    }

    private JsonNode fallbackNode(String platform, String cookie, Exception e) {
        return objectMapper.createObjectNode();
    }

    private JsonNode fallbackNode(String platform, String id, String cookie, Exception e) {
        return objectMapper.createObjectNode();
    }

    private Map<String, String> fallbackSongUrls(String platform, List<String> songIds, String cookie, Exception e) {
        log.warn("Song URL batch fetch fallback triggered", e);
        return Map.of();
    }
}
