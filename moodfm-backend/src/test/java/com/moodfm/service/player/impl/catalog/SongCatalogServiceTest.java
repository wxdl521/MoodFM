package com.moodfm.service.player.impl.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodfm.domain.entity.Song;
import com.moodfm.domain.vo.SongVO;
import com.moodfm.mapper.PlatformSongMappingMapper;
import com.moodfm.mapper.SongMapper;
import com.moodfm.service.embedding.EmbeddingService;
import com.moodfm.service.enrich.SongFeatureService;
import com.moodfm.service.player.impl.recall.SongEmbeddingTextBuilder;
import com.moodfm.service.vector.QdrantService;
import com.moodfm.service.vector.VectorRecallMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SongCatalogServiceTest {

    @Mock private SongMapper songMapper;
    @Mock private PlatformSongMappingMapper platformSongMappingMapper;
    @Mock private SongFeatureService songFeatureService;
    @Mock private EmbeddingService embeddingService;
    @Mock private QdrantService qdrantService;
    @Mock private VectorRecallMetrics vectorRecallMetrics;
    @Mock private SongEmbeddingTextBuilder songEmbeddingTextBuilder;

    @InjectMocks private SongCatalogService songCatalogService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(songCatalogService, "enrichTimeoutSeconds", 1);
        when(songEmbeddingTextBuilder.buildSongEmbeddingText(any())).thenReturn("");
    }

    @Test
    void persistSongs_enrichmentTimeout_usesFallbackWithinCap() throws Exception {
        SongVO vo = SongVO.builder()
                .title("Slow Song")
                .artist("SlowArtist")
                .platformSongId("999")
                .build();

        when(platformSongMappingMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(songMapper.selectList(any())).thenReturn(Collections.emptyList());

        when(songFeatureService.enrich(anyString(), anyString(), any())).thenAnswer(inv -> {
            Thread.sleep(3000);
            return "{\"source\":\"ai\"}";
        });

        String fallbackJson = "{\"valence\":0.5,\"energy\":0.5,\"genre\":\"未知\",\"language\":\"en\"," +
                "\"tempo_bucket\":\"mid\",\"mood_tags\":[],\"source\":\"fallback\",\"version\":1}";
        when(songFeatureService.fallbackFeatures(anyString(), anyString(), any()))
                .thenReturn(fallbackJson);

        Song[] inserted = {null};
        when(songMapper.insert(any(Song.class))).thenAnswer(inv -> {
            Song s = inv.getArgument(0);
            s.setId(42L);
            inserted[0] = s;
            return 1;
        });

        long start = System.currentTimeMillis();
        songCatalogService.persistSongs(List.of(vo), "netease");
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed < 2500, "persistSongs should respect enrich timeout cap; took " + elapsed + "ms");
        assertNotNull(inserted[0]);
        assertTrue(inserted[0].getFeatures().contains("\"source\":\"fallback\""));
    }
}