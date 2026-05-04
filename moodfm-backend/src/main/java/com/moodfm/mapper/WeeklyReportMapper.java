package com.moodfm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moodfm.domain.entity.WeeklyReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface WeeklyReportMapper extends BaseMapper<WeeklyReport> {

    @Select("SELECT * FROM weekly_reports WHERE id = #{id} AND user_id = #{userId}")
    WeeklyReport selectByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Select("SELECT * FROM weekly_reports WHERE user_id = #{userId} ORDER BY week_start DESC LIMIT 1")
    WeeklyReport selectLatestByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM weekly_reports WHERE user_id = #{userId} ORDER BY week_start DESC LIMIT 12")
    List<WeeklyReport> selectListByUserId(@Param("userId") Long userId);

    @Select("""
        SELECT * FROM weekly_reports
        WHERE user_id = #{userId} AND week_start = #{weekStart}
        """)
    WeeklyReport selectByUserIdAndWeekStart(@Param("userId") Long userId, @Param("weekStart") String weekStart);
}
