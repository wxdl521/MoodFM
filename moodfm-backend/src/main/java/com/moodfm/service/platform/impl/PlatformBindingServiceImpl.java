package com.moodfm.service.platform.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.moodfm.client.music.MusicApiClient;
import com.moodfm.common.constant.RedisKeys;
import com.moodfm.common.exception.BizException;
import com.moodfm.common.result.ResultCode;
import com.moodfm.common.util.AesUtil;
import com.moodfm.domain.entity.PlatformBinding;
import com.moodfm.domain.vo.PlatformBindingVO;
import com.moodfm.mapper.PlatformBindingMapper;
import com.moodfm.service.platform.PlatformBindingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformBindingServiceImpl implements PlatformBindingService {

    private final PlatformBindingMapper bindingMapper;
    private final MusicApiClient musicApiClient;
    private final AesUtil aesUtil;
    private final StringRedisTemplate redisTemplate;

    @Override
    public Map<String, Object> generateQrCode(Long userId, String platform) {
        String key = musicApiClient.generateQrKey(platform);
        Map<String, String> qrData = musicApiClient.createQrCode(platform, key);

        String statusKey = RedisKeys.format(RedisKeys.QR_LOGIN_STATUS, platform + ":" + key);
        redisTemplate.opsForValue().set(statusKey, "waiting", Duration.ofSeconds(90));

        return Map.of(
                "key", key,
                "qrimg", qrData.getOrDefault("qrimg", ""),
                "qrurl", qrData.getOrDefault("qrurl", "")
        );
    }

    @Override
    @CacheEvict(value = "platformMappings", key = "#userId")
    public Map<String, Object> checkQrStatus(Long userId, String platform, String key) {
        JsonNode status = musicApiClient.checkQrStatus(platform, key);
        int code = status.path("code").asInt();

        return switch (code) {
            case 800 -> {
                // 二维码过期
                yield Map.of("status", "expired", "message", "二维码已过期");
            }
            case 801 -> {
                // 等待扫码
                yield Map.of("status", "waiting", "message", "等待扫码");
            }
            case 802 -> {
                // 已扫码待确认
                yield Map.of("status", "scanned", "message", "已扫码，请在手机上确认");
            }
            case 803 -> {
                // 登录成功，获取 cookie
                String cookie = status.path("cookie").asText("");
                // cookie 字段有时是数组（adapter 兼容格式）
                if (cookie.isBlank() && status.path("cookie").isArray() && status.path("cookie").size() > 0) {
                    cookie = status.path("cookie").get(0).asText("");
                }
                if (!cookie.isBlank()) {
                    saveBinding(userId, platform, cookie, status);
                    yield Map.of("status", "success", "message", "绑定成功");
                } else {
                    // 适配器未能提取 Cookie（已知 v4.x library 缺陷）
                    log.warn("QR code 803 received but cookie is empty for platform={}, userId={}", platform, userId);
                    yield Map.of("status", "error", "message", "已扫码确认，但凭证获取失败——请改用 Cookie 方式绑定");
                }
            }
            default -> Map.of("status", "unknown", "message", "未知状态");
        };
    }

    @Override
    @CacheEvict(value = "platformMappings", key = "#userId")
    public void bindByCookie(Long userId, String platform, String cookie) {
        saveBinding(userId, platform, cookie, null);
    }

    @Override
    @CacheEvict(value = "platformMappings", key = "#userId")
    public void unbind(Long userId, String platform) {
        bindingMapper.delete(new LambdaQueryWrapper<PlatformBinding>()
                .eq(PlatformBinding::getUserId, userId)
                .eq(PlatformBinding::getPlatform, platform));
    }

    @Override
    public List<PlatformBindingVO> listBindings(Long userId) {
        return bindingMapper.selectList(new LambdaQueryWrapper<PlatformBinding>()
                        .eq(PlatformBinding::getUserId, userId))
                .stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public PlatformBinding getValidBinding(Long userId, String platform) {
        PlatformBinding binding = bindingMapper.selectOne(new LambdaQueryWrapper<PlatformBinding>()
                .eq(PlatformBinding::getUserId, userId)
                .eq(PlatformBinding::getPlatform, platform)
                .eq(PlatformBinding::getIsValid, 1));
        if (binding == null) throw new BizException(ResultCode.PLATFORM_COOKIE_INVALID);
        return binding;
    }

    @Override
    @Cacheable(value = "platformMappings", key = "#userId")
    public PlatformBinding getDefaultBinding(Long userId) {
        PlatformBinding binding = bindingMapper.selectOne(new LambdaQueryWrapper<PlatformBinding>()
                .eq(PlatformBinding::getUserId, userId)
                .eq(PlatformBinding::getIsDefault, 1)
                .eq(PlatformBinding::getIsValid, 1));
        if (binding == null) {
            // 没有默认平台，取第一个有效绑定
            binding = bindingMapper.selectOne(new LambdaQueryWrapper<PlatformBinding>()
                    .eq(PlatformBinding::getUserId, userId)
                    .eq(PlatformBinding::getIsValid, 1)
                    .last("LIMIT 1"));
        }
        if (binding == null) throw new BizException(ResultCode.PLATFORM_NOT_BOUND);
        return binding;
    }

    @Override
    public List<PlatformBinding> listAllValidBindings(Long userId) {
        return bindingMapper.selectList(new LambdaQueryWrapper<PlatformBinding>()
                .eq(PlatformBinding::getUserId, userId)
                .eq(PlatformBinding::getIsValid, 1));
    }

    @Override
    @CacheEvict(value = "platformMappings", key = "#userId")
    public void setDefault(Long userId, String platform) {
        bindingMapper.update(new LambdaUpdateWrapper<PlatformBinding>()
                .eq(PlatformBinding::getUserId, userId)
                .set(PlatformBinding::getIsDefault, 0));
        bindingMapper.update(new LambdaUpdateWrapper<PlatformBinding>()
                .eq(PlatformBinding::getUserId, userId)
                .eq(PlatformBinding::getPlatform, platform)
                .set(PlatformBinding::getIsDefault, 1));
    }

    private void saveBinding(Long userId, String platform, String cookie, JsonNode extra) {
        String encryptedCookie = aesUtil.encrypt(cookie);

        PlatformBinding existing = bindingMapper.selectOne(new LambdaQueryWrapper<PlatformBinding>()
                .eq(PlatformBinding::getUserId, userId)
                .eq(PlatformBinding::getPlatform, platform));

        if (existing != null) {
            existing.setCookieEncrypted(encryptedCookie);
            existing.setIsValid(1);
            existing.setLastValidatedAt(LocalDateTime.now());
            bindingMapper.updateById(existing);
        } else {
            PlatformBinding binding = new PlatformBinding();
            binding.setUserId(userId);
            binding.setPlatform(platform);
            binding.setCookieEncrypted(encryptedCookie);
            binding.setIsValid(1);
            binding.setIsDefault(0);
            binding.setLastValidatedAt(LocalDateTime.now());

            if (extra != null) {
                binding.setPlatformUserId(extra.path("userId").asText(null));
                binding.setPlatformUsername(extra.path("account").asText(null));
            }

            // 如果是第一个绑定，设为默认
            long count = bindingMapper.selectCount(new LambdaQueryWrapper<PlatformBinding>()
                    .eq(PlatformBinding::getUserId, userId));
            if (count == 0) binding.setIsDefault(1);

            bindingMapper.insert(binding);
        }
    }

    private PlatformBindingVO toVO(PlatformBinding b) {
        return PlatformBindingVO.builder()
                .id(b.getId())
                .platform(b.getPlatform())
                .platformUsername(b.getPlatformUsername())
                .valid(b.getIsValid() == 1)
                .isDefault(b.getIsDefault() == 1)
                .lastValidatedAt(b.getLastValidatedAt())
                .build();
    }
}
