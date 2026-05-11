package com.moodfm.service.report.impl;

import com.moodfm.domain.vo.AnnualReportVO;
import com.moodfm.mapper.MoodSessionMapper;
import com.moodfm.mapper.PlayRecordMapper;
import com.moodfm.service.report.AnnualReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AnnualReportServiceImpl implements AnnualReportService {

    private final PlayRecordMapper playRecordMapper;

    @Override
    public AnnualReportVO getAnnualReport(Long userId, int year) {
        // Aggregate monthly stats
        List<AnnualReportVO.MonthlyStat> monthlyStats = new ArrayList<>();
        int totalTracks = 0;
        int totalMinutes = 0;

        for (int m = 1; m <= 12; m++) {
            String monthStart = String.format("%d-%02d-01", year, m);
            String monthEnd = m < 12
                    ? String.format("%d-%02d-01", year, m + 1)
                    : String.format("%d-01-01", year + 1);

            Map<String, Object> stats = playRecordMapper.selectWeekStats(userId, monthStart, monthEnd);
            int tracks = toInt(stats.get("tracks"));
            int seconds = toInt(stats.get("totalSeconds"));
            int mins = seconds / 60;

            monthlyStats.add(AnnualReportVO.MonthlyStat.builder()
                    .month(m).tracks(tracks).minutes(mins).build());
            totalTracks += tracks;
            totalMinutes += mins;
        }

        // Top artists for the full year
        String yearStart = year + "-01-01";
        String yearEnd = (year + 1) + "-01-01";
        // Use a large day range
        List<Map<String, Object>> artistRows = playRecordMapper.selectTopArtists(userId, 365);
        List<AnnualReportVO.TopArtist> topArtists = artistRows.stream()
                .map(r -> AnnualReportVO.TopArtist.builder()
                        .name(str(r.get("artist")))
                        .totalTime(formatMinutes(toInt(r.get("totalSeconds")) / 60))
                        .playCount(toInt(r.get("playCount")))
                        .build())
                .toList();

        List<Map<String, Object>> songRows = playRecordMapper.selectTopSongs(userId, 365);
        List<AnnualReportVO.TopSong> topSongs = songRows.stream()
                .map(r -> AnnualReportVO.TopSong.builder()
                        .title(str(r.get("title")))
                        .artist(str(r.get("artist")))
                        .playCount(toInt(r.get("playCount")))
                        .build())
                .toList();

        List<Map<String, Object>> genreRows = playRecordMapper.selectGenreCounts(userId, 365);
        String topGenre = genreRows.isEmpty() ? "—" : str(genreRows.get(0).get("genre"));

        return AnnualReportVO.builder()
                .year(year)
                .totalListeningTime(formatMinutes(totalMinutes))
                .totalTracks(totalTracks)
                .totalSessions(0) // sessions not easily aggregated from existing queries
                .topGenre(topGenre)
                .topMood("—")
                .topArtists(topArtists)
                .topSongs(topSongs)
                .monthlyStats(monthlyStats)
                .build();
    }

    private String formatMinutes(int mins) {
        int h = mins / 60;
        int m = mins % 60;
        return h > 0 ? h + "h " + m + "m" : m + "m";
    }

    private String str(Object v) {
        return v == null ? "—" : v.toString();
    }

    private int toInt(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return 0; }
    }
}
