package com.moodfm.service.user;

import com.moodfm.domain.dto.auth.LoginRequest;
import com.moodfm.domain.dto.auth.RegisterRequest;
import com.moodfm.domain.dto.user.PreferencesRequest;
import com.moodfm.domain.entity.User;
import com.moodfm.domain.vo.LoginVO;
import com.moodfm.domain.vo.UserVO;

public interface UserService {

    LoginVO register(RegisterRequest request);

    LoginVO login(LoginRequest request);

    LoginVO refreshToken(String refreshToken);

    void logout(String accessToken);

    UserVO getCurrentUser(Long userId);

    User getById(Long userId);

    void savePreferences(Long userId, PreferencesRequest request);
}
