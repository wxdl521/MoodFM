package com.moodfm.domain.dto.user;

import lombok.Data;

@Data
public class UpdateProfileRequest {
    private String username;
    private String phone;
}
