package com.moodfm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moodfm.domain.entity.MoodSession;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;



@Mapper
public interface MoodSessionMapper extends BaseMapper<MoodSession> {

    @Select("""
        SELECT DATE(started_at) AS date,
               mood_params      AS moodParams
        FROM mood_sessions
        WHERE user_id = #{userId}
          AND YEAR(started_at)  = #{year}
          AND MONTH(started_at) = #{month}
          AND mood_params IS NOT NULL
        ORDER BY started_at
        """)
    List<Map<String, Object>> selectMonthMoodParams(
            @Param("userId") Long userId,
            @Param("year")   int year,
            @Param("month")  int month);

    @Select("""
        SELECT DATE(started_at) AS date,
               mood_params      AS moodParams
        FROM mood_sessions
        WHERE user_id = #{userId}
          AND started_at >= DATE_SUB(NOW(), INTERVAL #{days} DAY)
          AND mood_params IS NOT NULL
        ORDER BY started_at
        """)
    List<Map<String, Object>> selectRecentMoodParams(
            @Param("userId") Long userId,
            @Param("days")   int days);

    @Select("""
        SELECT DATE(started_at) AS date,
               mood_params      AS moodParams
        FROM mood_sessions
        WHERE user_id = #{userId}
          AND started_at >= #{weekStart}
          AND started_at <  #{weekEnd}
          AND mood_params IS NOT NULL
        ORDER BY started_at
        """)
    List<Map<String, Object>> selectRecentMoodParamsByDateRange(
            @Param("userId")    Long userId,
            @Param("weekStart") String weekStart,
            @Param("weekEnd")   String weekEnd);

    @Select("""
        SELECT mood_params AS moodParams
        FROM mood_sessions
        WHERE user_id = #{userId}
          AND DATE(started_at) = #{date}
          AND mood_params IS NOT NULL
        ORDER BY started_at
        """)
    List<Map<String, Object>> selectDayMoodParams(
            @Param("userId") Long userId,
            @Param("date")   String date);

    @Select("""
        SELECT id, raw_input AS rawInput, scene, started_at AS startedAt
        FROM mood_sessions
        WHERE user_id = #{userId}
        ORDER BY started_at DESC
        LIMIT #{limit}
        """)
    List<Map<String, Object>> selectRecentSessions(
            @Param("userId") Long userId,
            @Param("limit")  int limit);

    @Delete("DELETE FROM mood_sessions WHERE user_id = #{userId}")
    void deleteByUserId(@Param("userId") Long userId);

    /** 心情主旋律统计：只取用户主动输入心情的会话（排除 song-seed 默认会话） */
    @Select("""
        SELECT DATE(started_at) AS date,
               mood_params      AS moodParams
        FROM mood_sessions
        WHERE user_id = #{userId}
          AND started_at >= DATE_SUB(NOW(), INTERVAL #{days} DAY)
          AND mood_params IS NOT NULL
          AND scene != 'song-seed'
        ORDER BY started_at
        """)
    List<Map<String, Object>> selectRealMoodParams(
            @Param("userId") Long userId,
            @Param("days")   int days);

    /** 流派偏好统计：从心情分析结果的 preferred_genres 聚合 */
    @Select("""
        SELECT g.genre, COUNT(*) AS cnt
        FROM mood_sessions ms,
        JSON_TABLE(
            JSON_EXTRACT(ms.mood_params, '$.preferred_genres'),
            '$[*]' COLUMNS(genre VARCHAR(50) PATH '$')
        ) g
        WHERE ms.user_id = #{userId}
          AND ms.started_at >= DATE_SUB(NOW(), INTERVAL #{days} DAY)
          AND ms.mood_params IS NOT NULL
          AND JSON_EXTRACT(ms.mood_params, '$.preferred_genres') IS NOT NULL
          AND ms.scene != 'song-seed'
        GROUP BY g.genre
        ORDER BY cnt DESC
        LIMIT 8
        """)
    List<Map<String, Object>> selectPreferredGenreCounts(
            @Param("userId") Long userId,
            @Param("days")   int days);

    @Select("""
        SELECT
          SUM(CASE WHEN valence > 0.6  AND energy > 0.6  THEN 1 ELSE 0 END) AS pos_high,
          SUM(CASE WHEN valence > 0.6  AND energy <= 0.6 THEN 1 ELSE 0 END) AS pos_low,
          SUM(CASE WHEN valence BETWEEN 0.3 AND 0.6 AND energy > 0.6  THEN 1 ELSE 0 END) AS neu_high,
          SUM(CASE WHEN valence BETWEEN 0.3 AND 0.6 AND energy <= 0.6 THEN 1 ELSE 0 END) AS neu_low,
          SUM(CASE WHEN valence < 0.3  AND energy > 0.6  THEN 1 ELSE 0 END) AS neg_high,
          SUM(CASE WHEN valence < 0.3  AND energy <= 0.6 THEN 1 ELSE 0 END) AS neg_low
        FROM (
          SELECT
            CAST(JSON_UNQUOTE(JSON_EXTRACT(mood_params, '$.mood.valence')) AS DECIMAL(4,3)) AS valence,
            CAST(JSON_UNQUOTE(JSON_EXTRACT(mood_params, '$.mood.energy'))  AS DECIMAL(4,3)) AS energy
          FROM mood_sessions
          WHERE started_at >= DATE_SUB(NOW(), INTERVAL #{days} DAY)
            AND mood_params IS NOT NULL
            AND JSON_EXTRACT(mood_params, '$.mood.valence') IS NOT NULL
        ) t
        """)
    Map<String, Object> selectAdminMoodDist(@Param("days") int days);
}
