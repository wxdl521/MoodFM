package com.moodfm.domain.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "账号不能为空")
    private String account; // 邮箱或手机号

    @NotBlank(message = "密码不能为空")
    private String password;

    private boolean rememberMe;
}
