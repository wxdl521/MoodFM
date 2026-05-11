package com.moodfm.service.history.impl;

import com.moodfm.domain.vo.HistoryItemVO;
import com.moodfm.domain.vo.HistoryPageVO;
import com.moodfm.mapper.PlayRecordMapper;
import com.moodfm.service.history.HistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class HistoryServiceImpl implements HistoryService {

    private final PlayRecordMapper playRecordMapper;

    @Override
    public void clearAll(Long userId) {
        playRecordMapper.deleteByUserId(userId);
    }

    @Override
    public HistoryPageVO getHistory(Long userId, String scene, int page, int pageSize) {
        int size = Math.min(pageSize, 50);
        int offset = (Math.max(page, 1) - 1) * size;

        List<Map<String, Object>> rows = playRecordMapper.selectHistory(userId, scene, size, offset);
        long total = playRecordMapper.countHistory(userId, scene);

        List<HistoryItemVO> items = rows.stream().map(r -> {
            Object playedAtRaw = r.get("playedAt");
            String playedAt = playedAtRaw instanceof LocalDateTime ldt ? ldt.toString() : null;

            String songIdStr = str(r.get("songId"));
            HistoryItemVO.SongBriefVO song = HistoryItemVO.SongBriefVO.builder()
                    .id(songIdStr)
                    .title(str(r.get("title")))
                    .artist(str(r.get("artist")))
                    .platform(str(r.get("platform")))
                    .platformSongId(songIdStr)
                    .duration(num(r.get("totalSeconds")))
                    .coverUrl(str(r.get("coverUrl")))
                    .build();

            return HistoryItemVO.builder()
                    .id(lng(r.get("id")))
                    .sessionId(lng(r.get("sessionId")))
                    .playedAt(playedAt)
                    .durationPlayed(num(r.get("playedSeconds")))
                    .song(song)
                    .build();
        }).toList();

        return HistoryPageVO.builder()
                .items(items)
                .total(total)
                .page(page)
                .pageSize(size)
                .build();
    }

    private String str(Object v) {
        return v == null ? null : v.toString();
    }

    private Long lng(Object v) {
        if (v == null) return null;
        if (v instanceof Long l) return l;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return null; }
    }

    private Integer num(Object v) {
        if (v == null) return null;
        if (v instanceof Integer i) return i;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return null; }
    }
}
