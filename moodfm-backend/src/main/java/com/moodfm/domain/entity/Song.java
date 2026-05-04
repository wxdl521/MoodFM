package com.moodfm.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("songs")
public class Song {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String artist;

    private String album;

    private Integer durationSeconds;

    private String coverUrl;

    /** JSON: {genre, bpm, energy, language, valence} */
    private String features;

    /** M2 阶段使用 */
    private String qdrantPointId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
