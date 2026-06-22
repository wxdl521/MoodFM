package com.moodfm.client.music;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;

class MusicApiClientUnsupportedTest {

    private MockWebServer server;
    private MusicApiClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        client = new MusicApiClient(new ObjectMapper());
        String baseUrl = server.url("/").toString();
        ReflectionTestUtils.setField(client, "adapterUrl", baseUrl);
        ReflectionTestUtils.setField(client, "restClient", RestClient.builder().baseUrl(baseUrl).build());
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void isUnsupported_detectsAdapterFlag() throws Exception {
        JsonNode node = new ObjectMapper().readTree(
                "{\"code\":200,\"unsupported\":true,\"reason\":\"no API\",\"songs\":[]}");
        assertTrue(MusicApiClient.isUnsupported(node));
    }

    @Test
    void getRecommendSongs_preservesUnsupportedResponse() {
        server.enqueue(new MockResponse()
                .setBody("{\"code\":200,\"unsupported\":true,\"reason\":\"simi unavailable\",\"songs\":[]}")
                .addHeader("Content-Type", "application/json"));

        JsonNode result = client.getRecommendSongs("qqmusic", "cookie");

        assertTrue(MusicApiClient.isUnsupported(result));
        assertEquals("simi unavailable", result.path("reason").asText());
    }

    @Test
    void sendPhoneCodeRoutesToNeteaseEndpoint() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("{\"code\":200,\"data\":{\"ticket\":\"abc123\"}}")
                .addHeader("Content-Type", "application/json"));

        String ticket = client.sendPhoneCode("netease", "13800138000");

        assertEquals("abc123", ticket);
        assertTrue(server.takeRequest().getPath().contains("/netease/phone/code"));
    }

    @Test
    void verifyPhoneCodeRoutesToNeteaseEndpoint() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("{\"code\":803,\"cookie\":\"MUSIC_U=xxx\",\"account\":\"testuser\"}")
                .addHeader("Content-Type", "application/json"));

        String[] result = client.verifyPhoneCode("netease", "13800138000", "123456", "ticket1");

        assertNotNull(result);
        assertEquals("MUSIC_U=xxx", result[0]);
        assertEquals("testuser", result[1]);
        assertTrue(server.takeRequest().getPath().contains("/netease/phone/verify"));
    }
}