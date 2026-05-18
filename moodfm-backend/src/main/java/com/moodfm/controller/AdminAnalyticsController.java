package com.moodfm.controller;

import com.moodfm.common.result.R;
import com.moodfm.mapper.MoodSessionMapper;
import com.moodfm.mapper.PlayRecordMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "管理员-分析", description = "全站流派 / 平台占比 / TOP 歌曲 / 心情雷达")
@RestController
@RequestMapping("/api/admin/analytics")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AdminAnalyticsController {

    private final PlayRecordMapper playRecordMapper;
    private final MoodSessionMapper moodSessionMapper;

    @Operation(summary = "全站流派分布 TOP 8")
    @GetMapping("/genre")
    public R<List<Map<String, Object>>> getGenreDistribution(
            @RequestParam(defaultValue = "30") int days) {
        int safeDays = Math.min(Math.max(days, 1), 365);
        List<Map<String, Object>> rows = playRecordMapper.selectAdminGenreCounts(safeDays);
        // Rename 'cnt' -> 'count' for a cleaner API response
        rows.forEach(r -> {
            Object cnt = r.remove("cnt");
            if (cnt != null) r.put("count", cnt);
        });
        return R.ok(rows);
    }

    @Operation(summary = "各平台播放量占比")
    @GetMapping("/platform")
    public R<List<Map<String, Object>>> getPlatformDistribution(
            @RequestParam(defaultValue = "30") int days) {
        int safeDays = Math.min(Math.max(days, 1), 365);
        List<Map<String, Object>> rows = playRecordMapper.selectAdminPlatformCounts(safeDays);
        rows.forEach(r -> {
            Object cnt = r.remove("cnt");
            if (cnt != null) r.put("count", cnt);
        });
        return R.ok(rows);
    }

    @Operation(summary = "全站 TOP 5 播放歌曲")
    @GetMapping("/top-songs")
    public R<List<Map<String, Object>>> getTopSongs(
            @RequestParam(defaultValue = "30") int days) {
        int safeDays = Math.min(Math.max(days, 1), 365);
        return R.ok(playRecordMapper.selectAdminTopSongs(safeDays));
    }

    @Operation(summary = "全站心情雷达（6 象限，当期 vs 上期对比）")
    @GetMapping("/mood")
    public R<Map<String, Object>> getMoodRadar(
            @RequestParam(defaultValue = "30") int days) {
        int safeDays = Math.min(Math.max(days, 1), 365);

        // Current period
        Map<String, Object> current = moodSessionMapper.selectAdminMoodDist(safeDays);
        // Previous period (same span before current period)
        Map<String, Object> previous = moodSessionMapper.selectAdminMoodDist(safeDays * 2);

        int[] thisMonth  = toRadarArray(current);
        int[] lastMonth  = toRadarArray(subtractArrayMap(previous, current));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("thisMonth",  boxArray(thisMonth));
        data.put("lastMonth",  boxArray(lastMonth));
        return R.ok(data);
    }

    /**
     * Converts a mood distribution map (pos_high, pos_low, neu_high, neu_low, neg_high, neg_low)
     * into an int array in that fixed order.
     */
    private int[] toRadarArray(Map<String, Object> row) {
        if (row == null) return new int[]{0, 0, 0, 0, 0, 0};
        return new int[]{
                toLong(row.get("pos_high")),
                toLong(row.get("pos_low")),
                toLong(row.get("neu_high")),
                toLong(row.get("neu_low")),
                toLong(row.get("neg_high")),
                toLong(row.get("neg_low"))
        };
    }

    /**
     * Returns a new map where each key's value = previous[key] - current[key].
     * Used to isolate the counts from the previous period only.
     */
    private Map<String, Object> subtractArrayMap(Map<String, Object> full, Map<String, Object> current) {
        String[] keys = {"pos_high", "pos_low", "neu_high", "neu_low", "neg_high", "neg_low"};
        Map<String, Object> prev = new LinkedHashMap<>();
        for (String key : keys) {
            long fullVal    = toLong(full    != null ? full.get(key)    : null);
            long currentVal = toLong(current != null ? current.get(key) : null);
            prev.put(key, Math.max(0L, fullVal - currentVal));
        }
        return prev;
    }

    private int toLong(Object v) {
        if (v == null) return 0;
        return ((Number) v).intValue();
    }

    private List<Integer> boxArray(int[] arr) {
        java.util.ArrayList<Integer> list = new java.util.ArrayList<>(arr.length);
        for (int v : arr) list.add(v);
        return list;
    }
}
