package com.moodfm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moodfm.domain.entity.PlayRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface PlayRecordMapper extends BaseMapper<PlayRecord> {

    @Select("""
        SELECT DAY(played_at) AS day,
               COUNT(*) AS tracks,
               COALESCE(SUM(played_seconds), 0) DIV 60 AS minutes,
               COUNT(DISTINCT session_id) AS sessions
        FROM play_records
        WHERE user_id = #{userId}
          AND YEAR(played_at)  = #{year}
          AND MONTH(played_at) = #{month}
        GROUP BY DAY(played_at)
        """)
    List<Map<String, Object>> selectCalendarStats(
            @Param("userId") Long userId,
            @Param("year")   int year,
            @Param("month")  int month);

    @Select("""
        SELECT s.title,
               s.artist,
               s.duration_seconds AS durationSeconds,
               COUNT(*) AS playCount
        FROM play_records pr
        JOIN songs s ON pr.song_id = s.id
        WHERE pr.user_id = #{userId}
          AND DATE(pr.played_at) = #{date}
        GROUP BY pr.song_id, s.title, s.artist, s.duration_seconds
        ORDER BY playCount DESC, pr.played_at DESC
        LIMIT 5
        """)
    List<Map<String, Object>> selectDayTopTracks(
            @Param("userId") Long userId,
            @Param("date")   String date);

    @Select("""
        SELECT COUNT(*) AS tracks,
               COALESCE(SUM(played_seconds), 0) DIV 60 AS minutes,
               COUNT(DISTINCT session_id) AS sessions
        FROM play_records
        WHERE user_id = #{userId}
          AND DATE(played_at) = #{date}
        """)
    Map<String, Object> selectDayStats(
            @Param("userId") Long userId,
            @Param("date")   String date);

    @Select("""
        SELECT COALESCE(SUM(played_seconds), 0) AS totalSeconds
        FROM play_records
        WHERE user_id = #{userId}
          AND played_at >= DATE_SUB(NOW(), INTERVAL #{days} DAY)
        """)
    Map<String, Object> selectTotalSeconds(
            @Param("userId") Long userId,
            @Param("days")   int days);

    @Select("""
        SELECT
          SUM(CASE WHEN action = 'liked'   THEN 1 ELSE 0 END) AS likes,
          SUM(CASE WHEN action = 'skipped' THEN 1 ELSE 0 END) AS skips
        FROM play_records
        WHERE user_id = #{userId}
          AND played_at >= DATE_SUB(NOW(), INTERVAL #{days} DAY)
        """)
    Map<String, Object> selectFeedbackCounts(
            @Param("userId") Long userId,
            @Param("days")   int days);

    @Select("""
        SELECT s.artist,
               SUM(pr.played_seconds) AS totalSeconds,
               COUNT(*) AS playCount
        FROM play_records pr
        JOIN songs s ON pr.song_id = s.id
        WHERE pr.user_id = #{userId}
          AND pr.played_at >= DATE_SUB(NOW(), INTERVAL #{days} DAY)
        GROUP BY s.artist
        ORDER BY totalSeconds DESC
        LIMIT 5
        """)
    List<Map<String, Object>> selectTopArtists(
            @Param("userId") Long userId,
            @Param("days")   int days);

    @Select("""
        SELECT s.title, s.artist, COUNT(*) AS playCount
        FROM play_records pr
        JOIN songs s ON pr.song_id = s.id
        WHERE pr.user_id = #{userId}
          AND pr.played_at >= DATE_SUB(NOW(), INTERVAL #{days} DAY)
        GROUP BY pr.song_id, s.title, s.artist
        ORDER BY playCount DESC
        LIMIT 5
        """)
    List<Map<String, Object>> selectTopSongs(
            @Param("userId") Long userId,
            @Param("days")   int days);

    @Select("""
        SELECT JSON_UNQUOTE(JSON_EXTRACT(s.features, '$.genre')) AS genre,
               COUNT(*) AS cnt
        FROM play_records pr
        JOIN songs s ON pr.song_id = s.id
        WHERE pr.user_id = #{userId}
          AND pr.played_at >= DATE_SUB(NOW(), INTERVAL #{days} DAY)
          AND s.features IS NOT NULL
          AND JSON_EXTRACT(s.features, '$.genre') IS NOT NULL
        GROUP BY JSON_EXTRACT(s.features, '$.genre')
        ORDER BY cnt DESC
        LIMIT 8
        """)
    List<Map<String, Object>> selectGenreCounts(
            @Param("userId") Long userId,
            @Param("days")   int days);

    @Select("""
        SELECT DATE(pr.played_at) AS date,
               AVG(JSON_EXTRACT(s.features, '$.energy')) AS avgEnergy
        FROM play_records pr
        JOIN songs s ON pr.song_id = s.id
        WHERE pr.user_id = #{userId}
          AND pr.played_at >= DATE_SUB(NOW(), INTERVAL #{days} DAY)
          AND s.features IS NOT NULL
        GROUP BY DATE(pr.played_at)
        ORDER BY date
        """)
    List<Map<String, Object>> selectDailySongMood(
            @Param("userId") Long userId,
            @Param("days")   int days);

    @Select("""
        SELECT pr.id,
               pr.session_id    AS sessionId,
               pr.song_id       AS songId,
               pr.platform,
               pr.played_seconds AS playedSeconds,
               pr.total_seconds  AS totalSeconds,
               pr.action,
               pr.played_at     AS playedAt,
               s.title,
               s.artist,
               s.cover_url      AS coverUrl,
               ms.scene
        FROM play_records pr
        LEFT JOIN songs s  ON pr.song_id  = s.id
        LEFT JOIN mood_sessions ms ON pr.session_id = ms.id
        WHERE pr.user_id = #{userId}
          AND (#{scene} IS NULL OR ms.scene = #{scene})
        ORDER BY pr.played_at DESC
        LIMIT #{pageSize} OFFSET #{offset}
        """)
    List<Map<String, Object>> selectHistory(
            @Param("userId")   Long userId,
            @Param("scene")    String scene,
            @Param("pageSize") int pageSize,
            @Param("offset")   int offset);

    @Select("""
        SELECT COUNT(*)
        FROM play_records pr
        LEFT JOIN mood_sessions ms ON pr.session_id = ms.id
        WHERE pr.user_id = #{userId}
          AND (#{scene} IS NULL OR ms.scene = #{scene})
        """)
    long countHistory(
            @Param("userId") Long userId,
            @Param("scene")  String scene);

    @Select("""
        SELECT COUNT(DISTINCT song_id)                               AS tracks,
               COALESCE(SUM(played_seconds), 0)                     AS totalSeconds,
               SUM(CASE WHEN action = 'liked'   THEN 1 ELSE 0 END) AS likes,
               SUM(CASE WHEN action = 'skipped' THEN 1 ELSE 0 END) AS skips
        FROM play_records
        WHERE user_id = #{userId}
          AND played_at >= #{weekStart}
          AND played_at <  #{weekEnd}
        """)
    Map<String, Object> selectWeekStats(
            @Param("userId")    Long userId,
            @Param("weekStart") String weekStart,
            @Param("weekEnd")   String weekEnd);

    @Select("""
        SELECT s.title, s.artist, COUNT(*) AS playCount
        FROM play_records pr
        JOIN songs s ON pr.song_id = s.id
        WHERE pr.user_id = #{userId}
          AND pr.played_at >= #{weekStart}
          AND pr.played_at <  #{weekEnd}
        GROUP BY pr.song_id, s.title, s.artist
        ORDER BY playCount DESC
        LIMIT 1
        """)
    Map<String, Object> selectWeekTopSong(
            @Param("userId")    Long userId,
            @Param("weekStart") String weekStart,
            @Param("weekEnd")   String weekEnd);

    @Select("""
        SELECT s.title, s.artist,
               JSON_UNQUOTE(JSON_EXTRACT(s.features, '$.genre')) AS genre
        FROM play_records pr
        JOIN songs s ON pr.song_id = s.id
        WHERE pr.user_id = #{userId}
          AND pr.played_at >= #{weekStart}
          AND pr.played_at <  #{weekEnd}
          AND pr.song_id NOT IN (
                SELECT DISTINCT song_id
                FROM play_records
                WHERE user_id = #{userId} AND played_at < #{weekStart}
          )
        GROUP BY pr.song_id, s.title, s.artist, s.features
        ORDER BY MIN(pr.played_at)
        LIMIT 4
        """)
    List<Map<String, Object>> selectWeekNewDiscoveries(
            @Param("userId")    Long userId,
            @Param("weekStart") String weekStart,
            @Param("weekEnd")   String weekEnd);
}
