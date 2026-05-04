package com.moodfm.domain.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginVO {
    private String accessToken;
    private String refreshToken;
    private long accessTokenExpiresIn; // 秒
    private UserVO user;
}
