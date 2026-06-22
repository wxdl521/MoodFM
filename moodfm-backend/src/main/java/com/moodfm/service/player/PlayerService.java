package com.moodfm.service.player;

import com.moodfm.domain.dto.radio.MoodInputRequest;
import com.moodfm.domain.vo.RadioQueueVO;
import com.moodfm.domain.vo.SessionSummaryVO;
import com.moodfm.domain.vo.SongVO;

import java.util.List;

public interface PlayerService {
    List<SessionSummaryVO> getRecentSessions(Long userId, int limit);

    RadioQueueVO startRadio(Long userId, MoodInputRequest request);
    RadioQueueVO startRadioFromSong(Long userId, Long songId);
    RadioQueueVO startRadioFromPlaylist(Long userId, String playlistId, Integer durationMinutes);
    List<SongVO> getNextBatch(Long userId, Long sessionId);
    String getSongUrl(Long userId, String platform, String songId);

    /**
     * 动态重排检查：每消费 3 首歌，重新召回 + AI 排序 next 5 首。
     * 从 RadioController 反馈接口调用。
     */
    void reRankIfNeeded(Long userId, Long sessionId);

    /**
     * 更新当前会话的时长（分钟）。存入 Redis TTL key。
     */
    void setSessionDuration(Long sessionId, int minutes);

    /**
     * 检查会话是否已过期。过期返回 true。
     */
    boolean isSessionExpired(Long sessionId);
}
