package com.moodfm.service.user.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodfm.common.constant.RedisKeys;
import com.moodfm.common.exception.BizException;
import com.moodfm.common.result.ResultCode;
import com.moodfm.common.util.JwtUtil;
import com.moodfm.domain.dto.auth.LoginRequest;
import com.moodfm.domain.dto.auth.RegisterRequest;
import com.moodfm.domain.dto.user.ChangePasswordRequest;
import com.moodfm.domain.dto.user.PreferencesRequest;
import com.moodfm.domain.dto.user.UpdateProfileRequest;
import com.moodfm.domain.entity.PlatformBinding;
import com.moodfm.domain.entity.User;
import com.moodfm.domain.entity.UserProfile;
import com.moodfm.domain.vo.LoginVO;
import com.moodfm.domain.vo.PreferencesVO;
import com.moodfm.domain.vo.UserVO;
import com.moodfm.mapper.*;
import com.moodfm.mapper.UserMapper;
import com.moodfm.mapper.UserProfileMapper;
import com.moodfm.service.user.UserService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    @Value("${moodfm.upload-dir:${user.home}/moodfm-uploads}")
    private String uploadDir;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    private static final int MAX_LOGIN_FAIL = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final Duration FAIL_COUNT_TTL = Duration.ofMinutes(15);

    private static final int MAX_OTP_ATTEMPTS = 5;
    private static final Duration OTP_ATTEMPTS_TTL = Duration.ofMinutes(15);

    private static final java.util.regex.Pattern PASSWORD_PATTERN =
            java.util.regex.Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,}$");

    private static final long MAX_AVATAR_SIZE = 5 * 1024 * 1024; // 5 MB
    private static final java.util.Set<String> ALLOWED_AVATAR_TYPES = java.util.Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );
    private static final java.util.Map<String, String> MIME_TO_EXT = java.util.Map.of(
            "image/jpeg", "jpg",
            "image/png",  "png",
            "image/gif",  "gif",
            "image/webp", "webp"
    );

    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    private PlayRecordMapper playRecordMapper;
    @org.springframework.beans.factory.annotation.Autowired
    private FeedbackEventMapper feedbackEventMapper;
    @org.springframework.beans.factory.annotation.Autowired
    private MoodSessionMapper moodSessionMapper;
    @org.springframework.beans.factory.annotation.Autowired
    private WeeklyReportMapper weeklyReportMapper;
    @org.springframework.beans.factory.annotation.Autowired
    private PlatformBindingMapper platformBindingMapper;

    @Override
    public LoginVO register(RegisterRequest request) {
        if (request.getEmail() == null && request.getPhone() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "邮箱或手机号至少填一个");
        }

        // 若提供了手机号，必须先验证短信验证码
        if (request.getPhone() != null) {
            if (request.getPhoneCode() == null || request.getPhoneCode().isBlank()) {
                throw new BizException(ResultCode.BAD_REQUEST, "手机号注册需要提供验证码");
            }
            if (!verifySmsCode(request.getPhone(), request.getPhoneCode())) {
                throw new BizException(ResultCode.INVALID_SMS_CODE);
            }
        }

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(request.getEmail() != null, User::getEmail, request.getEmail())
                .or()
                .eq(request.getPhone() != null, User::getPhone, request.getPhone());

        if (userMapper.selectCount(wrapper) > 0) {
            throw new BizException(ResultCode.USER_ALREADY_EXISTS);
        }

        // The DB UNIQUE constraint on email/phone is the real guard against duplicates.
        // The check above is a best-effort early exit; a DuplicateKeyException on insert
        // handles the rare race between the SELECT and INSERT.
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setStatus(1);
        user.setRole("USER");
        user.setEmailVerified(false);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BizException(ResultCode.USER_ALREADY_EXISTS);
        }

        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), false);

        Claims refreshClaims = jwtUtil.parseToken(refreshToken);
        long ttlSeconds = (refreshClaims.getExpiration().getTime() - System.currentTimeMillis()) / 1000;
        redisTemplate.opsForValue().set(
                RedisKeys.format(RedisKeys.REFRESH_TOKEN, refreshToken),
                String.valueOf(user.getId()),
                Duration.ofSeconds(ttlSeconds)
        );

        return LoginVO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .accessTokenExpiresIn(1800)
                .user(toUserVO(user))
                .build();
    }

    @Override
    public LoginVO login(LoginRequest request) {
        String account = request.getAccount();
        checkLoginLock(account);

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, account)
                .or()
                .eq(User::getPhone, account));

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            recordLoginFail(account);
            throw new BizException(ResultCode.WRONG_PASSWORD);
        }

        if (user.getStatus() != 1) {
            throw new BizException(ResultCode.ACCOUNT_DISABLED);
        }

        clearLoginFail(account);

        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), request.isRememberMe());

        // 存储 refresh token
        Claims refreshClaims = jwtUtil.parseToken(refreshToken);
        long ttlSeconds = (refreshClaims.getExpiration().getTime() - System.currentTimeMillis()) / 1000;
        String refreshKey = RedisKeys.format(RedisKeys.REFRESH_TOKEN, refreshToken);
        redisTemplate.opsForValue().set(refreshKey, String.valueOf(user.getId()), Duration.ofSeconds(ttlSeconds));

        return LoginVO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .accessTokenExpiresIn(1800)
                .user(toUserVO(user))
                .build();
    }

    @Override
    public LoginVO refreshToken(String refreshToken) {
        String refreshKey = RedisKeys.format(RedisKeys.REFRESH_TOKEN, refreshToken);
        String userIdStr = redisTemplate.opsForValue().get(refreshKey);
        if (userIdStr == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }

        Long userId = Long.parseLong(userIdStr);
        User user = userMapper.selectById(userId);
        if (user == null || user.getStatus() != 1) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }

        redisTemplate.delete(refreshKey);

        String newAccessToken = jwtUtil.generateAccessToken(user.getId(), user.getUsername(), user.getRole());
        String newRefreshToken = jwtUtil.generateRefreshToken(user.getId(), false);

        Claims rc = jwtUtil.parseToken(newRefreshToken);
        long ttlSeconds = (rc.getExpiration().getTime() - System.currentTimeMillis()) / 1000;
        redisTemplate.opsForValue().set(
                RedisKeys.format(RedisKeys.REFRESH_TOKEN, newRefreshToken),
                String.valueOf(userId),
                Duration.ofSeconds(ttlSeconds)
        );

        return LoginVO.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .accessTokenExpiresIn(1800)
                .user(toUserVO(user))
                .build();
    }

    @Override
    public void logout(String accessToken, String refreshToken) {
        try {
            Claims claims = jwtUtil.parseToken(accessToken);
            long ttlMs = claims.getExpiration().getTime() - System.currentTimeMillis();
            if (ttlMs > 0) {
                String blacklistKey = RedisKeys.format(RedisKeys.JWT_BLACKLIST, claims.getId());
                redisTemplate.opsForValue().set(blacklistKey, "1", Duration.ofMillis(ttlMs));
            }
        } catch (Exception e) {
            log.debug("Logout with invalid access token, ignored");
        }
        // Revoke the refresh token so it cannot be used to obtain new access tokens
        if (refreshToken != null && !refreshToken.isBlank()) {
            redisTemplate.delete(RedisKeys.format(RedisKeys.REFRESH_TOKEN, refreshToken));
        }
    }

    @Override
    @Cacheable(value = "users", key = "#userId")
    public UserVO getCurrentUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BizException(ResultCode.USER_NOT_FOUND);
        return toUserVO(user);
    }

    @Override
    public User getById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BizException(ResultCode.USER_NOT_FOUND);
        return user;
    }

    private void checkLoginLock(String account) {
        String lockKey = RedisKeys.format(RedisKeys.LOGIN_LOCK, account);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(lockKey))) {
            throw new BizException(ResultCode.ACCOUNT_LOCKED);
        }
    }

    private void recordLoginFail(String account) {
        String failKey = RedisKeys.format(RedisKeys.LOGIN_FAIL_COUNT, account);
        Long count = redisTemplate.opsForValue().increment(failKey);
        redisTemplate.expire(failKey, FAIL_COUNT_TTL);
        if (count != null && count >= MAX_LOGIN_FAIL) {
            String lockKey = RedisKeys.format(RedisKeys.LOGIN_LOCK, account);
            redisTemplate.opsForValue().set(lockKey, "1", LOCK_DURATION);
        }
    }

    private void clearLoginFail(String account) {
        redisTemplate.delete(RedisKeys.format(RedisKeys.LOGIN_FAIL_COUNT, account));
        redisTemplate.delete(RedisKeys.format(RedisKeys.LOGIN_LOCK, account));
    }

    @Override
    public void savePreferences(Long userId, PreferencesRequest request) {
        try {
            String genreJson  = objectMapper.writeValueAsString(
                    request.getGenres() != null ? request.getGenres() : java.util.List.of());
            String langJson   = objectMapper.writeValueAsString(
                    request.getLanguages() != null ? request.getLanguages() : java.util.List.of());

            UserProfile existing = userProfileMapper.selectByUserId(userId);
            if (existing == null) {
                UserProfile profile = new UserProfile();
                profile.setUserId(userId);
                profile.setGenreWeights(genreJson);
                profile.setLanguagePreferences(langJson);
                userProfileMapper.insert(profile);
            } else {
                existing.setGenreWeights(genreJson);
                existing.setLanguagePreferences(langJson);
                userProfileMapper.updateById(existing);
            }
        } catch (Exception e) {
            log.warn("savePreferences failed for userId {}: {}", userId, e.getMessage());
            throw new BizException(500, "偏好保存失败");
        }
    }

    @Override
    @CacheEvict(value = "users", key = "#userId")
    public void updateProfile(Long userId, UpdateProfileRequest request) {
        User user = getById(userId);
        if (StringUtils.hasText(request.getUsername())) {
            user.setUsername(request.getUsername());
        }
        if (StringUtils.hasText(request.getPhone())) {
            // Check phone uniqueness before updating
            Long existing = userMapper.selectCount(
                    new LambdaQueryWrapper<User>()
                            .eq(User::getPhone, request.getPhone())
                            .ne(User::getId, userId));
            if (existing != null && existing > 0) {
                throw new BizException(ResultCode.USER_ALREADY_EXISTS, "该手机号已被其他用户绑定");
            }
            user.setPhone(request.getPhone());
        }
        userMapper.updateById(user);
    }

    @Override
    @CacheEvict(value = "users", key = "#userId")
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = getById(userId);
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new BizException(ResultCode.WRONG_PASSWORD, "原密码不正确");
        }
        // Validate new password strength (same rule as registration)
        if (!PASSWORD_PATTERN.matcher(request.getNewPassword()).matches()) {
            throw new BizException(ResultCode.BAD_REQUEST, "新密码需至少8位，且包含字母和数字");
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateById(user);
    }

    @Override
    @CacheEvict(value = "users", key = "#userId")
    public String uploadAvatar(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "文件不能为空");
        }
        if (file.getSize() > MAX_AVATAR_SIZE) {
            throw new BizException(ResultCode.BAD_REQUEST, "头像文件不能超过 5 MB");
        }
        String rawContentType = file.getContentType();
        String contentType = rawContentType != null ? rawContentType.split(";")[0].trim() : null;
        if (contentType == null || !ALLOWED_AVATAR_TYPES.contains(contentType)) {
            throw new BizException(ResultCode.BAD_REQUEST, "只支持 JPEG、PNG、GIF、WebP 格式的头像");
        }
        String ext = MIME_TO_EXT.get(contentType);
        if (ext == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "不支持的图像格式");
        }
        // Read old avatar URL before overwriting so we can clean up the file
        User user = getById(userId);
        String oldAvatarUrl = user.getAvatarUrl();

        String filename = userId + "_" + UUID.randomUUID().toString().replace("-", "") + "." + ext;
        try {
            Path dir = Paths.get(uploadDir, "avatars");
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(filename));
        } catch (IOException e) {
            throw new BizException(500, "头像上传失败: " + e.getMessage());
        }

        // Delete old avatar file to prevent unbounded disk growth
        if (oldAvatarUrl != null && oldAvatarUrl.startsWith("/uploads/avatars/")) {
            Path oldFile = Paths.get(uploadDir, "avatars", oldAvatarUrl.substring("/uploads/avatars/".length()));
            try {
                Files.deleteIfExists(oldFile);
            } catch (IOException e) {
                log.warn("删除旧头像失败 {}: {}", oldFile, e.getMessage());
            }
        }

        String avatarUrl = "/uploads/avatars/" + filename;
        user.setAvatarUrl(avatarUrl);
        userMapper.updateById(user);
        return avatarUrl;
    }

    @Override
    public PreferencesVO getPreferences(Long userId) {
        UserProfile profile = userProfileMapper.selectByUserId(userId);
        if (profile == null) {
            return PreferencesVO.builder().genres(List.of()).languages(List.of()).build();
        }
        try {
            List<String> genres = profile.getGenreWeights() != null
                    ? objectMapper.readValue(profile.getGenreWeights(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class))
                    : List.of();
            List<String> langs = profile.getLanguagePreferences() != null
                    ? objectMapper.readValue(profile.getLanguagePreferences(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class))
                    : List.of();
            return PreferencesVO.builder().genres(genres).languages(langs).build();
        } catch (Exception e) {
            return PreferencesVO.builder().genres(List.of()).languages(List.of()).build();
        }
    }

    // ── Notification Preferences ────────────────────────────────────

    @Override
    public com.moodfm.domain.vo.NotificationPrefsVO getNotificationPrefs(Long userId) {
        UserProfile profile = userProfileMapper.selectByUserId(userId);
        if (profile == null || profile.getNotificationPrefs() == null) {
            // 默认全部开启
            return com.moodfm.domain.vo.NotificationPrefsVO.builder()
                    .weeklyReport(true).cookieExpiry(true).newFeatures(true)
                    .weeklyReportDay(0).weeklyReportHour(9).build();
        }
        try {
            return objectMapper.readValue(profile.getNotificationPrefs(),
                    com.moodfm.domain.vo.NotificationPrefsVO.class);
        } catch (Exception e) {
            log.warn("Failed to parse notificationPrefs for user {}", userId, e);
            return com.moodfm.domain.vo.NotificationPrefsVO.builder()
                    .weeklyReport(true).cookieExpiry(true).newFeatures(true)
                    .weeklyReportDay(0).weeklyReportHour(9).build();
        }
    }

    @Override
    public void saveNotificationPrefs(Long userId, com.moodfm.domain.vo.NotificationPrefsVO prefs) {
        try {
            String json = objectMapper.writeValueAsString(prefs);
            UserProfile profile = userProfileMapper.selectByUserId(userId);
            if (profile == null) {
                profile = new UserProfile();
                profile.setUserId(userId);
                profile.setNotificationPrefs(json);
                userProfileMapper.insert(profile);
            } else {
                profile.setNotificationPrefs(json);
                userProfileMapper.updateById(profile);
            }
        } catch (Exception e) {
            log.warn("saveNotificationPrefs failed for userId {}: {}", userId, e.getMessage());
            throw new BizException(500, "通知偏好保存失败");
        }
    }

    @Override
    @CacheEvict(value = "users", key = "#userId")
    public void deleteAccount(Long userId) {
        // Revoke all refresh tokens for this user before soft-deleting
        revokeAllUserRefreshTokens(userId);
        User user = getById(userId);
        user.setStatus(0); // soft delete via @TableLogic
        userMapper.updateById(user);
    }

    @Override
    @CacheEvict(value = "users", key = "#userId")
    @Transactional
    public void deleteAllData(Long userId) {
        playRecordMapper.deleteByUserId(userId);
        feedbackEventMapper.deleteByUserId(userId);
        moodSessionMapper.deleteByUserId(userId);
        weeklyReportMapper.deleteByUserId(userId);
        platformBindingMapper.delete(new LambdaQueryWrapper<PlatformBinding>()
                .eq(PlatformBinding::getUserId, userId));
        userProfileMapper.delete(new LambdaQueryWrapper<UserProfile>()
                .eq(UserProfile::getUserId, userId));
    }

    @Override
    public void recordDevice(Long userId, String ip, String userAgent) {
        String key = RedisKeys.format(RedisKeys.USER_DEVICES, userId);
        String id = UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> device = Map.of(
                "id", id,
                "ip", ip != null ? ip : "unknown",
                "userAgent", userAgent != null ? userAgent : "unknown",
                "loginAt", LocalDateTime.now().toString()
        );
        try {
            String json = objectMapper.writeValueAsString(device);
            redisTemplate.opsForList().leftPush(key, json);
            redisTemplate.opsForList().trim(key, 0, 9); // keep last 10
            redisTemplate.expire(key, Duration.ofDays(30));
        } catch (Exception e) {
            log.warn("Failed to record device for user {}", userId, e);
        }
    }

    @Override
    public List<Map<String, Object>> getDevices(Long userId) {
        String key = RedisKeys.format(RedisKeys.USER_DEVICES, userId);
        List<String> entries = redisTemplate.opsForList().range(key, 0, -1);
        if (entries == null || entries.isEmpty()) return List.of();
        return entries.stream().<Map<String, Object>>map(e -> {
            try { return objectMapper.readValue(e, new TypeReference<>() {}); }
            catch (Exception ex) { return Map.of(); }
        }).filter(m -> !m.isEmpty()).toList();
    }

    // ── SMS OTP ───────────────────────────────────────────────────

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public void sendSmsCode(String phone) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        String key = RedisKeys.format(RedisKeys.SMS_CODE, phone);
        redisTemplate.opsForValue().set(key, code, Duration.ofMinutes(5));
        // Only log OTP codes in dev profile to avoid leaking secrets in production logs
        if (activeProfile.contains("dev") && log.isDebugEnabled()) {
            log.debug("[DEV] SMS verification code for {}: {}", phone, code);
        }
    }

    @Override
    public boolean verifySmsCode(String phone, String code) {
        // Brute-force protection: check attempt counter first
        String attemptsKey = RedisKeys.format(RedisKeys.SMS_ATTEMPTS, phone);
        String attemptsStr = redisTemplate.opsForValue().get(attemptsKey);
        if (attemptsStr != null && Integer.parseInt(attemptsStr) >= MAX_OTP_ATTEMPTS) {
            throw new BizException(ResultCode.OTP_TOO_MANY_ATTEMPTS);
        }

        String key = RedisKeys.format(RedisKeys.SMS_CODE, phone);
        String stored = redisTemplate.opsForValue().get(key);
        if (stored != null && stored.equals(code)) {
            redisTemplate.delete(key);
            // Reset attempt counter on success
            redisTemplate.delete(attemptsKey);
            return true;
        }
        // Increment failed attempt counter
        Long count = redisTemplate.opsForValue().increment(attemptsKey);
        if (count != null && count == 1) {
            redisTemplate.expire(attemptsKey, OTP_ATTEMPTS_TTL);
        }
        return false;
    }

    @Override
    public LoginVO loginByPhone(String phone, String code) {
        if (!verifySmsCode(phone, code)) {
            throw new BizException(ResultCode.INVALID_SMS_CODE);
        }

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, phone));

        if (user == null) {
            throw new BizException(ResultCode.USER_NOT_FOUND);
        }
        if (user.getStatus() != 1) {
            throw new BizException(ResultCode.ACCOUNT_DISABLED);
        }

        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), false);

        Claims refreshClaims = jwtUtil.parseToken(refreshToken);
        long ttlSeconds = (refreshClaims.getExpiration().getTime() - System.currentTimeMillis()) / 1000;
        redisTemplate.opsForValue().set(
                RedisKeys.format(RedisKeys.REFRESH_TOKEN, refreshToken),
                String.valueOf(user.getId()),
                Duration.ofSeconds(ttlSeconds)
        );

        return LoginVO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .accessTokenExpiresIn(1800)
                .user(toUserVO(user))
                .build();
    }

    // ── Email Verification ────────────────────────────────────────

    @Override
    public void sendEmailVerification(String email) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        String key = RedisKeys.format(RedisKeys.EMAIL_VERIFY, email);
        redisTemplate.opsForValue().set(key, code, Duration.ofMinutes(15));
        // Only log OTP codes in dev profile to avoid leaking secrets in production logs
        if (activeProfile.contains("dev") && log.isDebugEnabled()) {
            log.debug("[DEV] Email verification code for {}: {}", email, code);
        }
    }

    @Override
    public void verifyEmail(String email, String code) {
        // Brute-force protection: check attempt counter first
        String attemptsKey = RedisKeys.format(RedisKeys.EMAIL_ATTEMPTS, email);
        String attemptsStr = redisTemplate.opsForValue().get(attemptsKey);
        if (attemptsStr != null && Integer.parseInt(attemptsStr) >= MAX_OTP_ATTEMPTS) {
            throw new BizException(ResultCode.OTP_TOO_MANY_ATTEMPTS);
        }

        String key = RedisKeys.format(RedisKeys.EMAIL_VERIFY, email);
        String stored = redisTemplate.opsForValue().get(key);
        if (stored == null || !stored.equals(code)) {
            // Increment failed attempt counter
            Long count = redisTemplate.opsForValue().increment(attemptsKey);
            if (count != null && count == 1) {
                redisTemplate.expire(attemptsKey, OTP_ATTEMPTS_TTL);
            }
            throw new BizException(ResultCode.INVALID_EMAIL_CODE);
        }
        redisTemplate.delete(key);
        // Reset attempt counter on success
        redisTemplate.delete(attemptsKey);

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email));
        if (user == null) {
            throw new BizException(ResultCode.USER_NOT_FOUND);
        }
        user.setEmailVerified(true);
        userMapper.updateById(user);
    }

    // ── Device Revoke ─────────────────────────────────────────────

    @Override
    public void revokeDevice(Long userId, String deviceId) {
        String key = RedisKeys.format(RedisKeys.USER_DEVICES, userId);
        List<String> entries = redisTemplate.opsForList().range(key, 0, -1);
        if (entries == null) return;

        for (String entry : entries) {
            try {
                Map<String, Object> device = objectMapper.readValue(entry, new TypeReference<>() {});
                Object id = device.get("id");
                if (id != null && id.toString().equals(deviceId)) {
                    redisTemplate.opsForList().remove(key, 1, entry);
                    log.info("Revoked device {} for user {}", deviceId, userId);
                    return;
                }
            } catch (Exception e) {
                log.warn("Failed to parse device entry for revoke: {}", entry, e);
            }
        }
        log.warn("Device {} not found for user {}", deviceId, userId);
    }

    /**
     * Scan Redis for all refresh tokens belonging to the given user and delete them.
     * This prevents soft-deleted or deleted accounts from refreshing tokens.
     */
    private void revokeAllUserRefreshTokens(Long userId) {
        java.util.Set<String> keys = redisTemplate.keys(RedisKeys.format(RedisKeys.REFRESH_TOKEN, "*"));
        if (keys == null || keys.isEmpty()) return;
        for (String key : keys) {
            String storedUserId = redisTemplate.opsForValue().get(key);
            if (String.valueOf(userId).equals(storedUserId)) {
                redisTemplate.delete(key);
            }
        }
    }

    private UserVO toUserVO(User user) {
        return UserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
