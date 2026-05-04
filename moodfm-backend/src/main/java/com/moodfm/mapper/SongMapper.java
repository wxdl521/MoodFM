package com.moodfm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moodfm.domain.entity.Song;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SongMapper extends BaseMapper<Song> {
}
