package com.moodfm.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("platform_song_mapping")
public class PlatformSongMapping {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long songId;

    private String platform;

    private String platformSongId;
}
