package com.moodfm.service.history;

import com.moodfm.domain.vo.HistoryPageVO;

public interface HistoryService {
    HistoryPageVO getHistory(Long userId, String scene, int page, int pageSize);
    void clearAll(Long userId);
}
