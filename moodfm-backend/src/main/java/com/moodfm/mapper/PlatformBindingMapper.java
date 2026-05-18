package com.moodfm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moodfm.domain.entity.PlatformBinding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface PlatformBindingMapper extends BaseMapper<PlatformBinding> {

    @Select("""
        SELECT pb.user_id AS userId,
               u.username,
               pb.platform,
               pb.expires_at AS expiresAt,
               DATEDIFF(pb.expires_at, NOW()) AS daysLeft
        FROM platform_bindings pb
        JOIN users u ON pb.user_id = u.id
        WHERE pb.expires_at <= DATE_ADD(NOW(), INTERVAL 7 DAY)
        ORDER BY pb.expires_at ASC
        LIMIT 50
        """)
    List<Map<String, Object>> selectExpiringCookies();

    @Select("SELECT * FROM platform_bindings WHERE is_valid = 1")
    List<PlatformBinding> findAllValid();

    @Update("UPDATE platform_bindings SET is_valid = 0, last_validated_at = NOW() WHERE id = #{id}")
    void markInvalid(Long id);

    @Update("UPDATE platform_bindings SET last_validated_at = NOW() WHERE id = #{id}")
    void updateLastValidated(Long id);
}
