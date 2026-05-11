package com.moodfm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moodfm.domain.entity.FeedbackEvent;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FeedbackEventMapper extends BaseMapper<FeedbackEvent> {

    @Delete("DELETE FROM feedback_events WHERE user_id = #{userId}")
    void deleteByUserId(@Param("userId") Long userId);
}
