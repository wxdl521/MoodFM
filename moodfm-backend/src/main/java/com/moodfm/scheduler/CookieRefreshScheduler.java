package com.moodfm.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moodfm.domain.entity.PlatformBinding;
import com.moodfm.mapper.PlatformBindingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CookieRefreshScheduler {

    private final PlatformBindingMapper bindingMapper;

    /**
     * 每 24 小时检查并标记快过期的 Cookie，触发刷新逻辑
     * 实际刷新实现根据各平台 API 不同，此处仅做日志示例
     */
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
            // TODO: 调用平台刷新接口，或通过 WebSocket 通知用户重新绑定
        }
    }
}
