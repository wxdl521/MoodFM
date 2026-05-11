package com.moodfm.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moodfm.domain.entity.PlatformBinding;
import com.moodfm.domain.vo.NotificationPrefsVO;
import com.moodfm.mapper.PlatformBindingMapper;
import com.moodfm.service.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CookieRefreshScheduler {

    private final PlatformBindingMapper bindingMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserService userService;

    @Scheduled(fixedDelay = 86400000) // 24h
    public void checkCookieExpiry() {
        LocalDateTime threshold = LocalDateTime.now().plusHours(48);
        List<PlatformBinding> expiringSoon = bindingMapper.selectList(
                new LambdaQueryWrapper<PlatformBinding>()
                        .eq(PlatformBinding::getIsValid, 1)
                        .lt(PlatformBinding::getExpiresAt, threshold)
                        .isNotNull(PlatformBinding::getExpiresAt)
        );

        if (!expiringSoon.isEmpty()) {
            log.info("Found {} platform bindings expiring within 48h", expiringSoon.size());
            for (PlatformBinding binding : expiringSoon) {
                // 检查用户是否启用了 cookieExpiry 通知
                try {
                    NotificationPrefsVO prefs = userService.getNotificationPrefs(binding.getUserId());
                    if (!prefs.isCookieExpiry()) {
                        log.debug("User {} has cookieExpiry notification disabled, skipping", binding.getUserId());
                        continue;
                    }
                } catch (Exception e) {
                    log.warn("Failed to check notification prefs for user {}, sending anyway", binding.getUserId(), e);
                }
                messagingTemplate.convertAndSend(
                        "/topic/notify/" + binding.getUserId(),
                        Map.of("type", "cookie_invalid", "platform", binding.getPlatform())
                );
                log.info("Notified user {} about expiring {} cookie", binding.getUserId(), binding.getPlatform());
            }
        }
    }
}
