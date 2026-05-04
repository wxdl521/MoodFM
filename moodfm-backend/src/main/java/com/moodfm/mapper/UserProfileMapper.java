package com.moodfm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moodfm.domain.entity.UserProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserProfileMapper extends BaseMapper<UserProfile> {

    @Select("SELECT * FROM user_profiles WHERE user_id = #{userId}")
    UserProfile selectByUserId(@Param("userId") Long userId);
}
