package com.moodfm.service.user.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.moodfm.domain.entity.User;
import com.moodfm.domain.entity.UserProfile;
import com.moodfm.domain.vo.LoginVO;
import com.moodfm.domain.vo.PreferencesVO;
import com.moodfm.domain.vo.UserVO;
import com.moodfm.mapper.UserMapper;
import com.moodfm.mapper.UserProfileMapper;
import com.moodfm.service.user.UserService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    @Value("${moodfm.upload-dir:${user.home}/moodfm-uploads}")
    private String uploadDir;

    private static final int MAX_LOGIN_FAIL = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final Duration FAIL_COUNT_TTL = Duration.ofMinutes(30);

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

    @Override
    public LoginVO register(RegisterRequest request) {
        if (request.getEmail() == null && request.getPhone() == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "邮箱或手机号至少填一个");
        }

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(request.getEmail() != null, User::getEmail, request.getEmail())
                .or()
                .eq(request.getPhone() != null, User::getPhone, request.getPhone());

        if (userMapper.selectCount(wrapper) > 0) {
            throw new BizException(ResultCode.USER_ALREADY_EXISTS);
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setStatus(1);
        user.setRole("USER");
        userMapper.insert(user);

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
    public void logout(String accessToken) {
        try {
            Claims claims = jwtUtil.parseToken(accessToken);
            long ttlMs = claims.getExpiration().getTime() - System.currentTimeMillis();
            if (ttlMs > 0) {
                String blacklistKey = RedisKeys.format(RedisKeys.JWT_BLACKLIST, claims.getId());
                redisTemplate.opsForValue().set(blacklistKey, "1", Duration.ofMillis(ttlMs));
            }
        } catch (Exception e) {
            log.debug("Logout with invalid token, ignored");
        }
    }

    @Override
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
    public void updateProfile(Long userId, UpdateProfileRequest request) {
        User user = getById(userId);
        if (StringUtils.hasText(request.getUsername())) {
            user.setUsername(request.getUsername());
        }
        if (StringUtils.hasText(request.getPhone())) {
            user.setPhone(request.getPhone());
        }
        userMapper.updateById(user);
    }

    @Override
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = getById(userId);
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new BizException(ResultCode.WRONG_PASSWORD, "原密码不正确");
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateById(user);
    }

    @Override
    public String uploadAvatar(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "文件不能为空");
        }
        if (file.getSize() > MAX_AVATAR_SIZE) {
            throw new BizException(ResultCode.BAD_REQUEST, "头像文件不能超过 5 MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_AVATAR_TYPES.contains(contentType)) {
            throw new BizException(ResultCode.BAD_REQUEST, "只支持 JPEG、PNG、GIF、WebP 格式的头像");
        }
        String ext = MIME_TO_EXT.get(contentType);
        String filename = userId + "_" + UUID.randomUUID().toString().replace("-", "") + "." + ext;
        try {
            Path dir = Paths.get(uploadDir, "avatars");
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(filename));
        } catch (IOException e) {
            throw new BizException(500, "头像上传失败: " + e.getMessage());
        }
        String avatarUrl = "/uploads/avatars/" + filename;
        User user = getById(userId);
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

    @Override
    public void deleteAccount(Long userId) {
        User user = getById(userId);
        user.setStatus(0); // soft delete via @TableLogic
        userMapper.updateById(user);
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
