package com.moodfm.domain.vo;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class SearchResultVO {
    private String mode;       // "keyword" or "mood"
    private String query;
    private List<SongVO> songs;
    private String notice;     // nullable — e.g. "请先绑定平台"
}
