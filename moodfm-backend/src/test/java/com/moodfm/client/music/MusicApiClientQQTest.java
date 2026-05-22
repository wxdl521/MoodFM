package com.moodfm.client.music;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class MusicApiClientQQTest {

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
    void searchSongsRoutesToQqmusicEndpoint() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("{\"code\":200,\"songs\":[{\"songmid\":\"001\",\"songname\":\"Test\",\"singer\":[{\"name\":\"Artist\"}]}]}")
                .addHeader("Content-Type", "application/json"));

        JsonNode result = client.searchSongs("qqmusic", "test query", 10, null);

        RecordedRequest req = server.takeRequest();
        String path = req.getPath();
        assertTrue(path.contains("/qqmusic/search"), "Expected /qqmusic/search but got: " + path);
        assertTrue(path.contains("keywords=test%20query"));
        assertNotNull(result);
    }

    @Test
    void getRecommendSongsRoutesToQqmusicEndpoint() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("{\"code\":200,\"songs\":[]}")
                .addHeader("Content-Type", "application/json"));

        client.getRecommendSongs("qqmusic", "cookie");

        RecordedRequest req = server.takeRequest();
        String path = req.getPath();
        assertTrue(path.contains("/qqmusic/recommend/songs"), "Expected /qqmusic/recommend/songs but got: " + path);
    }

    @Test
    void getUserLikedSongsRoutesToQqmusicEndpoint() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("{\"code\":200,\"songs\":[]}")
                .addHeader("Content-Type", "application/json"));

        client.getUserLikedSongs("qqmusic", "cookie");

        RecordedRequest req = server.takeRequest();
        String path = req.getPath();
        assertTrue(path.contains("/qqmusic/user/liked-songs"), "Expected /qqmusic/user/liked-songs but got: " + path);
    }
}
