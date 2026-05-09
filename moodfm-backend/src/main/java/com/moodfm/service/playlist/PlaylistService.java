package com.moodfm.service.playlist;

import com.moodfm.domain.vo.PlaylistVO;
import java.util.List;

public interface PlaylistService {
    List<PlaylistVO> listPlaylists(Long userId);
    PlaylistVO getPlaylist(Long userId, String compositeId);
}
