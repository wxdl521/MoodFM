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
