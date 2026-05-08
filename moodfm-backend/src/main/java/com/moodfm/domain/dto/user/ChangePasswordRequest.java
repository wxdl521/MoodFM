package com.moodfm.domain.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordRequest {

    @NotBlank
    private String oldPassword;

    @NotBlank
    @Size(min = 8, message = "密码至少 8 位")
    private String newPassword;
}
