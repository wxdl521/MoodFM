package com.moodfm.service.player.impl.recall.filter;

import com.moodfm.domain.entity.GlobalBlacklist;
import com.moodfm.domain.vo.SongVO;
import com.moodfm.mapper.GlobalBlacklistMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GlobalBlacklistFilterTest {

    @Mock private GlobalBlacklistMapper globalBlacklistMapper;

    private GlobalBlacklistFilter filter;

    @BeforeEach
    void setUp() {
        filter = new GlobalBlacklistFilter(globalBlacklistMapper);
    }

    @Test
    void artistType_exactCaseInsensitive_isRemoved() {
        when(globalBlacklistMapper.selectList(any())).thenReturn(List.of(entry("artist", "BannedArtist")));

        List<SongVO> candidates = List.of(
                SongVO.builder().platformSongId("p1").title("Song").artist("BANNEDARTIST").build(),
                SongVO.builder().platformSongId("p2").title("Song").artist("GoodArtist").build());

        List<String> ids = filter.filter(null, candidates).stream().map(SongVO::getPlatformSongId).toList();
        assertEquals(List.of("p2"), ids, "exact artist match (case-insensitive) removed");
    }

    @Test
    void songType_exactTitle_isRemoved() {
        when(globalBlacklistMapper.selectList(any())).thenReturn(List.of(entry("song", "Banned Song")));

        List<SongVO> candidates = List.of(
                SongVO.builder().platformSongId("p1").title("banned song").artist("A").build(),
                SongVO.builder().platformSongId("p2").title("Other Song").artist("B").build());

        List<String> ids = filter.filter(null, candidates).stream().map(SongVO::getPlatformSongId).toList();
        assertEquals(List.of("p2"), ids, "exact song title match (case-insensitive) removed");
    }

    @Test
    void keywordType_substring_isRemoved() {
        when(globalBlacklistMapper.selectList(any())).thenReturn(List.of(entry("keyword", "explicit")));

        List<SongVO> candidates = List.of(
                SongVO.builder().platformSongId("p1").title("Explicit Content").artist("A").build(),
                SongVO.builder().platformSongId("p2").title("Clean Song").artist("B").build());

        List<String> ids = filter.filter(null, candidates).stream().map(SongVO::getPlatformSongId).toList();
        assertEquals(List.of("p2"), ids, "keyword substring match removed");
    }

    @Test
    void emptyBlacklist_returnsUnchanged() {
        when(globalBlacklistMapper.selectList(any())).thenReturn(List.of());

        List<SongVO> candidates = List.of(
                SongVO.builder().platformSongId("p1").title("Anything").artist("Anyone").build());

        assertSame(candidates, filter.filter(null, candidates), "empty blacklist filters nothing");
    }

    @Test
    void userIdIgnored_sameResultForNullAndValue() {
        when(globalBlacklistMapper.selectList(any())).thenReturn(List.of(entry("artist", "BannedArtist")));

        List<SongVO> candidates = List.of(
                SongVO.builder().platformSongId("p1").title("Song").artist("BannedArtist").build(),
                SongVO.builder().platformSongId("p2").title("Song").artist("GoodArtist").build());

        List<String> withNull  = filter.filter(null, candidates).stream().map(SongVO::getPlatformSongId).toList();
        List<String> withValue = filter.filter(99L,  candidates).stream().map(SongVO::getPlatformSongId).toList();

        assertEquals(withNull, withValue, "global blacklist must ignore userId");
        assertEquals(List.of("p2"), withValue);
    }

    private GlobalBlacklist entry(String type, String value) {
        GlobalBlacklist g = new GlobalBlacklist();
        g.setType(type);
        g.setValue(value);
        return g;
    }
}
