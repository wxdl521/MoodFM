package com.moodfm.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PreferencesVO {
    private List<String> genres;
    private List<String> languages;
    private String defaultScene;
}
