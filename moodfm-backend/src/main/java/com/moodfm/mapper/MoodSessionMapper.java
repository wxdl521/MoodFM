package com.moodfm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moodfm.domain.entity.MoodSession;
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
}
