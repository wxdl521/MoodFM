package com.moodfm.service.player.impl.recall.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodfm.domain.entity.UserProfile;
import com.moodfm.domain.vo.SongVO;
import com.moodfm.mapper.UserProfileMapper;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BlacklistKeywordFilterTest {

    @Mock private UserProfileMapper userProfileMapper;

    private BlacklistKeywordFilter filter;

    @BeforeEach
    void setUp() {
        filter = new BlacklistKeywordFilter(userProfileMapper, new ObjectMapper());
    }

    @Test
    void userIdNull_shortCircuits_returnsCandidatesUnchanged_noMapperCalls() {
        List<SongVO> candidates = List.of(
                SongVO.builder().platformSongId("p1").title("Death March").artist("X").build());

        List<SongVO> result = filter.filter(null, candidates);

        assertSame(candidates, result);
        verifyNoInteractions(userProfileMapper);
    }

    @Test
    void titleOrArtistMatch_caseInsensitive_isRemoved() {
        // Keyword "DEATH" (upper) must match title "Death March" (lower); "war" matches artist "Warzone"
        when(userProfileMapper.selectByUserId(7L)).thenReturn(profile("[\"DEATH\",\"war\"]"));

        List<SongVO> candidates = List.of(
                SongVO.builder().platformSongId("p1").title("Death March").artist("A").build(),
                SongVO.builder().platformSongId("p2").title("Good Song").artist("Warzone").build(),
                SongVO.builder().platformSongId("p3").title("Happy Day").artist("JoyBand").build());

        List<SongVO> result = filter.filter(7L, candidates);

        List<String> ids = result.stream().map(SongVO::getPlatformSongId).toList();
        assertEquals(List.of("p3"), ids, "title and artist keyword hits must both be removed (case-insensitive)");
    }

    @Test
    void nullBlacklist_returnsUnchanged() {
        when(userProfileMapper.selectByUserId(7L)).thenReturn(profile(null));
        List<SongVO> candidates = List.of(SongVO.builder().platformSongId("p1").title("A").artist("X").build());
        assertSame(candidates, filter.filter(7L, candidates));
    }

    @Test
    void blankBlacklist_returnsUnchanged() {
        when(userProfileMapper.selectByUserId(7L)).thenReturn(profile("   "));
        List<SongVO> candidates = List.of(SongVO.builder().platformSongId("p1").title("A").artist("X").build());
        assertSame(candidates, filter.filter(7L, candidates));
    }

    @Test
    void emptyArrayBlacklist_returnsUnchanged() {
        when(userProfileMapper.selectByUserId(7L)).thenReturn(profile("[]"));
        List<SongVO> candidates = List.of(SongVO.builder().platformSongId("p1").title("Death").artist("X").build());
        List<SongVO> result = filter.filter(7L, candidates);
        assertEquals(1, result.size(), "empty keyword array filters nothing");
    }

    @Test
    void nullProfile_returnsUnchanged() {
        when(userProfileMapper.selectByUserId(7L)).thenReturn(null);
        List<SongVO> candidates = List.of(SongVO.builder().platformSongId("p1").title("Death").artist("X").build());
        assertSame(candidates, filter.filter(7L, candidates));
    }

    private UserProfile profile(String blacklistKeywordsJson) {
        UserProfile p = new UserProfile();
        p.setBlacklistKeywords(blacklistKeywordsJson);
        return p;
    }
}
