package com.moodfm.domain.dto.auth;

import lombok.Data;

@Data
public class LogoutRequest {

    private String refreshToken;
}
