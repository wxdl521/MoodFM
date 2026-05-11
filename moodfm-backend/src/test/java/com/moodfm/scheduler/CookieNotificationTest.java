package com.moodfm.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moodfm.domain.entity.PlatformBinding;
import com.moodfm.mapper.PlatformBindingMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CookieNotificationTest {

    @Mock
    private PlatformBindingMapper bindingMapper;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private CookieRefreshScheduler scheduler;

    @Test
    void expiringCookieSendsNotificationViaStomp() {
        PlatformBinding binding = new PlatformBinding();
        binding.setUserId(42L);
        binding.setPlatform("netease");
        binding.setExpiresAt(LocalDateTime.now().plusHours(24));

        when(bindingMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(binding));

        scheduler.checkCookieExpiry();

        ArgumentCaptor<String> destCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(destCaptor.capture(), payloadCaptor.capture());

        assertEquals("/topic/notify/42", destCaptor.getValue());
        @SuppressWarnings("unchecked")
        Map<String, String> payload = (Map<String, String>) payloadCaptor.getValue();
        assertEquals("cookie_invalid", payload.get("type"));
        assertEquals("netease", payload.get("platform"));
    }

    @Test
    void noExpiringCookiesDoesNotSendNotification() {
        when(bindingMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of());

        scheduler.checkCookieExpiry();

        verifyNoInteractions(messagingTemplate);
    }
}
