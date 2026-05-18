package com.moodfm.service.song;

import com.moodfm.domain.vo.LyricLineVO;
import com.moodfm.domain.vo.SongVO;

import java.util.List;

public interface SongService {
    List<SongVO> getLikedSongs(Long userId);
    boolean toggleLike(Long userId, Long songId);
    boolean isLiked(Long userId, Long songId);
    SongVO getSongDetail(Long userId, Long songId);
    List<SongVO> getSimilarSongs(Long userId, Long songId);
    List<LyricLineVO> getLyrics(Long userId, Long songId);
}
