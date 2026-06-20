package com.moodfm.service.feedback.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moodfm.common.exception.BizException;
import com.moodfm.common.result.ResultCode;
import com.moodfm.domain.dto.feedback.PlaybackFeedbackDto;
import com.moodfm.domain.entity.PlatformBinding;
import com.moodfm.domain.entity.PlatformSongMapping;
import com.moodfm.domain.entity.PlayRecord;
import com.moodfm.mapper.FeedbackEventMapper;
import com.moodfm.mapper.PlayRecordMapper;
import com.moodfm.mapper.PlatformSongMappingMapper;
import com.moodfm.service.platform.PlatformBindingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure-Mockito unit tests for {@link FeedbackServiceImpl#resolvePlatform} via
 * the public {@code record} method.  No Spring context is needed.
 */
@ExtendWith(MockitoExtension.class)
class FeedbackServiceImplTest {

    @Mock private FeedbackEventMapper feedbackEventMapper;
    @Mock private PlayRecordMapper playRecordMapper;
    @Mock private PlatformSongMappingMapper platformSongMappingMapper;
    @Mock private PlatformBindingService platformBindingService;

    @InjectMocks
    private FeedbackServiceImpl feedbackService;

    private static final Long USER_ID   = 1L;
    private static final Long SONG_ID   = 100L;
    private static final Long SESSION_ID = 9L;

    /** Build a minimal skip DTO — the event type that writes a play_record. */
    private PlaybackFeedbackDto skipDto(String platform) {
        PlaybackFeedbackDto dto = new PlaybackFeedbackDto();
        dto.setSessionId(SESSION_ID);
        dto.setSongId(SONG_ID);
        dto.setEventType("skip");
        dto.setPlayedSeconds(10);
        dto.setTotalSeconds(200);
        dto.setPlatform(platform);
        return dto;
    }

    private PlatformSongMapping mapping(String platform) {
        PlatformSongMapping m = new PlatformSongMapping();
        m.setSongId(SONG_ID);
        m.setPlatform(platform);
        m.setPlatformSongId("psid-" + platform);
        return m;
    }

    private PlatformBinding binding(String platform) {
        PlatformBinding b = new PlatformBinding();
        b.setUserId(USER_ID);
        b.setPlatform(platform);
        return b;
    }

    // ── Test cases ────────────────────────────────────────────────────────────

    /**
     * Step 1: dto.platform is set → use it directly; mapper and binding not consulted.
     */
    @Test
    void record_usesDtoPlatform_whenPresent() {
        PlaybackFeedbackDto dto = skipDto("qqmusic");

        feedbackService.record(USER_ID, dto);

        ArgumentCaptor<PlayRecord> captor = ArgumentCaptor.forClass(PlayRecord.class);
        verify(playRecordMapper).insert(captor.capture());
        assertEquals("qqmusic", captor.getValue().getPlatform());
    }

    /**
     * Step 2: dto.platform is null, exactly one mapping row → use that platform.
     */
    @Test
    void record_usesMappingPlatform_whenDtoPlatformNullAndSingleMapping() {
        PlaybackFeedbackDto dto = skipDto(null);
        when(platformSongMappingMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(mapping("qqmusic")));

        feedbackService.record(USER_ID, dto);

        ArgumentCaptor<PlayRecord> captor = ArgumentCaptor.forClass(PlayRecord.class);
        verify(playRecordMapper).insert(captor.capture());
        assertEquals("qqmusic", captor.getValue().getPlatform());
    }

    /**
     * Step 2→3: dto.platform null, multiple mapping rows → ambiguous, skip to binding.
     * Binding returns "netease".
     */
    @Test
    void record_usesDefaultBinding_whenMultipleMappingsExist() {
        PlaybackFeedbackDto dto = skipDto(null);
        when(platformSongMappingMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(mapping("netease"), mapping("qqmusic")));
        when(platformBindingService.getDefaultBinding(USER_ID)).thenReturn(binding("netease"));

        feedbackService.record(USER_ID, dto);

        ArgumentCaptor<PlayRecord> captor = ArgumentCaptor.forClass(PlayRecord.class);
        verify(playRecordMapper).insert(captor.capture());
        assertEquals("netease", captor.getValue().getPlatform());
    }

    /**
     * Step 2→3→4: dto.platform null, no mapping rows, getDefaultBinding throws
     * BizException(PLATFORM_NOT_BOUND) → write "unknown", play_record still inserted.
     */
    @Test
    void record_writesUnknown_whenMappingEmptyAndBindingThrows() {
        PlaybackFeedbackDto dto = skipDto(null);
        when(platformSongMappingMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of());
        when(platformBindingService.getDefaultBinding(USER_ID))
                .thenThrow(new BizException(ResultCode.PLATFORM_NOT_BOUND));

        // Must not throw — fire-and-forget contract.
        feedbackService.record(USER_ID, dto);

        ArgumentCaptor<PlayRecord> captor = ArgumentCaptor.forClass(PlayRecord.class);
        verify(playRecordMapper).insert(captor.capture());
        assertEquals("unknown", captor.getValue().getPlatform());
    }

    /**
     * Optional: blank/whitespace platform string is treated as missing → enters chain.
     * With no mapping and no binding → "unknown".
     */
    @Test
    void record_treatsBlankPlatformAsMissing_andFallsThrough() {
        PlaybackFeedbackDto dto = skipDto("   ");
        when(platformSongMappingMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of());
        when(platformBindingService.getDefaultBinding(USER_ID))
                .thenThrow(new BizException(ResultCode.PLATFORM_NOT_BOUND));

        feedbackService.record(USER_ID, dto);

        ArgumentCaptor<PlayRecord> captor = ArgumentCaptor.forClass(PlayRecord.class);
        verify(playRecordMapper).insert(captor.capture());
        assertEquals("unknown", captor.getValue().getPlatform());
    }
}
