package com.moodfm.domain.dto.user;

import lombok.Data;

import java.util.List;

@Data
public class PreferencesRequest {
    /** 用户选择的流派，e.g. ["Ambient","Folk"] */
    private List<String> genres;
    /** 用户选择的语言，e.g. ["中文","English"] */
    private List<String> languages;
    /** 默认场景，e.g. "深夜" */
    private String defaultScene;
}
