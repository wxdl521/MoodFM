package com.moodfm.service.song.impl;

import com.moodfm.domain.vo.SongVO;
import com.moodfm.service.song.SongService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// Integration test: needs a live MySQL + Redis (and seeded data); excluded from CI via -DexcludedGroups=integration. Run locally against a populated dev DB.
@Tag("integration")
@SpringBootTest
@ExtendWith(SpringExtension.class)
class SongServiceCacheTest {

    @Autowired
    private SongService songService;

    @Test
    void getSongDetailReturnsConsistentResultAcrossCalls() {
        // Use a userId/songId that exists in test data
        SongVO first = songService.getSongDetail(1L, 1L);
        SongVO second = songService.getSongDetail(1L, 1L);

        assertNotNull(first);
        assertEquals(first.getId(), second.getId());
        assertEquals(first.getTitle(), second.getTitle());
    }
}
