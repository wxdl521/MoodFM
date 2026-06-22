package com.moodfm.service.platform.impl;

import com.moodfm.client.music.MusicApiClient;
import com.moodfm.common.exception.BizException;
import com.moodfm.common.util.AesUtil;
import com.moodfm.domain.entity.PlatformBinding;
import com.moodfm.domain.vo.PlatformBindingVO;
import com.moodfm.mapper.PlatformBindingMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlatformBindingServicePhoneTest {

    @Mock private PlatformBindingMapper bindingMapper;
    @Mock private MusicApiClient musicApiClient;
    @Mock private AesUtil aesUtil;
    @Mock private StringRedisTemplate redisTemplate;

    @InjectMocks private PlatformBindingServiceImpl service;

    @Test
    void sendPhoneCode_returnsTicketFromAdapter() {
        when(musicApiClient.sendPhoneCode("netease", "13800138000")).thenReturn("ticket-abc");

        String ticket = service.sendPhoneCode(1L, "netease", "13800138000");

        assertEquals("ticket-abc", ticket);
    }

    @Test
    void sendPhoneCode_throwsWhenAdapterFails() {
        when(musicApiClient.sendPhoneCode(anyString(), anyString())).thenReturn(null);

        assertThrows(BizException.class, () -> service.sendPhoneCode(1L, "netease", "138"));
    }

    @Test
    void bindByPhone_savesCookieAndReturnsVo() {
        when(musicApiClient.verifyPhoneCode("netease", "13800138000", "123456", "ticket-abc"))
                .thenReturn(new String[]{"MUSIC_U=abc", "netease_user"});
        when(aesUtil.encrypt("MUSIC_U=abc")).thenReturn("enc-cookie");

        PlatformBinding saved = new PlatformBinding();
        saved.setId(10L);
        saved.setUserId(1L);
        saved.setPlatform("netease");
        saved.setPlatformUsername("netease_user");
        saved.setIsValid(1);
        saved.setIsDefault(1);

        when(bindingMapper.selectOne(any())).thenReturn(null, saved);
        when(bindingMapper.selectCount(any())).thenReturn(0L);

        PlatformBindingVO vo = service.bindByPhone(1L, "netease", "13800138000", "123456", "ticket-abc");

        assertNotNull(vo);
        assertEquals("netease", vo.getPlatform());
        verify(bindingMapper).insert(any(PlatformBinding.class));
    }
}