package com.moodfm.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_profiles")
public class UserProfile {

    /** 与 users.id 同值，非自增 */
    @TableId
    private Long userId;

    /** JSON 数组，e.g. ["Ambient","Folk","Indie"] */
    private String genreWeights;

    /** JSON 数组，e.g. ["中文","English"] */
    private String languagePreferences;

    private String artistWeights;
    private String blacklistArtists;
    private String blacklistSongs;
    private String blacklistKeywords;
    private String qdrantUserVectorId;

    private LocalDateTime updatedAt;
}
