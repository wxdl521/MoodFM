package com.moodfm.service.user;

import com.moodfm.domain.dto.auth.LoginRequest;
import com.moodfm.domain.dto.auth.RegisterRequest;
import com.moodfm.domain.dto.user.ChangePasswordRequest;
import com.moodfm.domain.dto.user.PreferencesRequest;
import com.moodfm.domain.dto.user.UpdateProfileRequest;
import com.moodfm.domain.entity.User;
import com.moodfm.domain.vo.LoginVO;
import com.moodfm.domain.vo.PreferencesVO;
import com.moodfm.domain.vo.UserVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface UserService {

    // ── SMS OTP ───────────────────────────────────────────────────
    void sendSmsCode(String phone);
    boolean verifySmsCode(String phone, String code);
    LoginVO loginByPhone(String phone, String code);

    // ── Email Verification ────────────────────────────────────────
    void sendEmailVerification(String email);
    void verifyEmail(String email, String code);

    // ── Device Revoke ─────────────────────────────────────────────
    void revokeDevice(Long userId, String deviceId);

    LoginVO register(RegisterRequest request);

    LoginVO login(LoginRequest request);

    LoginVO refreshToken(String refreshToken);

    void logout(String accessToken, String refreshToken);

    UserVO getCurrentUser(Long userId);

    User getById(Long userId);

    void savePreferences(Long userId, PreferencesRequest request);

    void updateProfile(Long userId, UpdateProfileRequest request);

    void changePassword(Long userId, ChangePasswordRequest request);

    String uploadAvatar(Long userId, MultipartFile file);

    PreferencesVO getPreferences(Long userId);

    // ── Notification Preferences ──────────────────────────────────
    com.moodfm.domain.vo.NotificationPrefsVO getNotificationPrefs(Long userId);

    void saveNotificationPrefs(Long userId, com.moodfm.domain.vo.NotificationPrefsVO prefs);

    void deleteAccount(Long userId);

    void deleteAllData(Long userId);

    void recordDevice(Long userId, String ip, String userAgent);

    List<Map<String, Object>> getDevices(Long userId);
}
