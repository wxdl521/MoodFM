package com.moodfm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moodfm.domain.entity.FeedbackEvent;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FeedbackEventMapper extends BaseMapper<FeedbackEvent> {

    @Delete("DELETE FROM feedback_events WHERE user_id = #{userId}")
    void deleteByUserId(@Param("userId") Long userId);

    @Select("""
        SELECT COUNT(*) FROM feedback_events
        WHERE user_id = #{userId}
          AND event_type = 'like'
          AND created_at >= DATE_SUB(NOW(), INTERVAL #{days} DAY)
        """)
    long countLikes(@Param("userId") Long userId, @Param("days") int days);
}
