package com.moodfm.domain.vo;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class PlaylistVO {
    private String id;          // "{platform}:{platformPlaylistId}"
    private String name;
    private String description;
    private String coverUrl;
    private String platform;
    private Integer trackCount;
    private List<SongVO> tracks; // 仅 detail 接口返回，list 接口为 null
}
