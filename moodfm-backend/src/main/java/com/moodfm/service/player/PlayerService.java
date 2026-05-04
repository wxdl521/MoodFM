package com.moodfm.service.player;

import com.moodfm.domain.dto.radio.MoodInputRequest;
import com.moodfm.domain.vo.RadioQueueVO;
import com.moodfm.domain.vo.SongVO;

import java.util.List;

public interface PlayerService {
    RadioQueueVO startRadio(Long userId, MoodInputRequest request);
    List<SongVO> getNextBatch(Long userId, Long sessionId);
    String getSongUrl(Long userId, String platform, String songId);
}
