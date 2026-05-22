package com.moodfm.domain.dto.radio;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StartFromSongRequest {
    @NotNull(message = "songId 不能为空")
    private Long songId;
}
