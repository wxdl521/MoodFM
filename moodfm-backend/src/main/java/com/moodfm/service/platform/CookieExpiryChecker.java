package com.moodfm.service.platform;

import com.moodfm.client.music.MusicApiClient;
import com.moodfm.common.util.AesUtil;
import com.moodfm.domain.entity.PlatformBinding;
import com.moodfm.mapper.PlatformBindingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CookieExpiryChecker {

    private final PlatformBindingMapper platformBindingMapper;
    private final MusicApiClient musicApiClient;
    private final AesUtil aesUtil;

    @Scheduled(cron = "0 0 */6 * * *")
    public void checkAllBindings() {
        List<PlatformBinding> bindings = platformBindingMapper.findAllValid();
        if (bindings.isEmpty()) return;

        log.info("Cookie expiry check: validating {} bindings", bindings.size());
        int invalidCount = 0;

        for (PlatformBinding b : bindings) {
            try {
                String decryptedCookie = aesUtil.decrypt(b.getCookieEncrypted());
                String username = musicApiClient.validateCookie(b.getPlatform(), decryptedCookie);
                if (username == null) {
                    platformBindingMapper.markInvalid(b.getId());
                    invalidCount++;
                    log.info("Marked binding {} (userId={}, platform={}) as invalid",
                            b.getId(), b.getUserId(), b.getPlatform());
                } else {
                    platformBindingMapper.updateLastValidated(b.getId());
                }
            } catch (Exception e) {
                log.warn("Failed to validate binding {} for userId={}", b.getId(), b.getUserId(), e);
            }
        }

        log.info("Cookie expiry check complete: {}/{} bindings marked invalid", invalidCount, bindings.size());
    }
}
