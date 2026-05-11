package com.moodfm.service.report.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodfm.domain.entity.User;
import com.moodfm.domain.entity.WeeklyReport;
import com.moodfm.domain.vo.WeeklyReportVO;
import com.moodfm.mapper.MoodSessionMapper;
import com.moodfm.mapper.PlayRecordMapper;
import com.moodfm.mapper.UserMapper;
import com.moodfm.mapper.WeeklyReportMapper;
import com.moodfm.common.exception.BizException;
import com.moodfm.service.report.WeeklyReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyReportServiceImpl implements WeeklyReportService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DISP = DateTimeFormatter.ofPattern("MM/dd");

    private final WeeklyReportMapper weeklyReportMapper;
    private final PlayRecordMapper playRecordMapper;
    private final MoodSessionMapper moodSessionMapper;
    private final UserMapper userMapper;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    @Value("classpath:prompts/weekly-report.txt")
    private Resource promptResource;

    // ─── Public API ───────────────────────────────────────────────

    @Override
    public WeeklyReportVO getReport(Long userId, Long reportId) {
        WeeklyReport row = weeklyReportMapper.selectByIdAndUserId(reportId, userId);
        if (row == null) {
            throw new BizException(404, "周报不存在");
        }
        return deserialize(row);
    }

    @Override
    public WeeklyReportVO generateOrGetCurrentWeek(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        // Only generate for completed weeks (last week)
        LocalDate lastWeekStart = weekStart.minusWeeks(1);
        LocalDate lastWeekEnd   = weekStart;

        WeeklyReport existing = weeklyReportMapper
                .selectByUserIdAndWeekStart(userId, lastWeekStart.format(ISO));
        if (existing != null) {
            return deserialize(existing);
        }

        return generateAndStore(userId, lastWeekStart, lastWeekEnd);
    }

    @Override
    public List<WeeklyReport> listReports(Long userId) {
        return weeklyReportMapper.selectListByUserId(userId);
    }

    // ─── Generation ───────────────────────────────────────────────

    public WeeklyReportVO generateAndStore(Long userId, LocalDate weekStart, LocalDate weekEnd) {
        String ws = weekStart.format(ISO);
        String we = weekEnd.format(ISO);

        // Stats
        Map<String, Object> stats = playRecordMapper.selectWeekStats(userId, ws, we);
        long tracks      = stats != null ? toLong(stats.get("tracks"))       : 0L;
        long totalSec    = stats != null ? toLong(stats.get("totalSeconds"))  : 0L;
        long likes       = stats != null ? toLong(stats.get("likes"))         : 0L;
        long skips       = stats != null ? toLong(stats.get("skips"))         : 0L;

        if (tracks == 0) return null; // nothing to report

        String listenTime = formatSec(totalSec);
        String moodMatch  = (likes + skips) > 0
                ? Math.round(100.0 * likes / (likes + skips)) + "%"
                : "—";

        // Most played song
        Map<String, Object> topSongRow = playRecordMapper.selectWeekTopSong(userId, ws, we);
        WeeklyReportVO.SongInfo mostPlayed = topSongRow == null ? null :
                WeeklyReportVO.SongInfo.builder()
                        .title(str(topSongRow.get("title")))
                        .artist(str(topSongRow.get("artist")))
                        .playCount(toInt(topSongRow.get("playCount")))
                        .build();

        // New discoveries
        List<Map<String, Object>> discRows = playRecordMapper.selectWeekNewDiscoveries(userId, ws, we);
        List<WeeklyReportVO.TrackInfo> discoveries = new ArrayList<>();
        for (Map<String, Object> r : discRows) {
            discoveries.add(WeeklyReportVO.TrackInfo.builder()
                    .title(str(r.get("title")))
                    .artist(str(r.get("artist")))
                    .genre(str(r.get("genre")))
                    .mood("dusk")
                    .build());
        }

        // Top genre (scoped to the actual week range)
        List<Map<String, Object>> genreRows = playRecordMapper.selectGenreCountsByDateRange(userId, ws, we);
        String topGenre = genreRows.isEmpty() ? "—" : capitalize(str(genreRows.get(0).get("genre")));

        // Mood from sessions (scoped to the actual week range)
        List<Map<String, Object>> sessionRows = moodSessionMapper.selectRecentMoodParamsByDateRange(userId, ws, we);
        double avgV = 0.5, avgE = 0.5;
        if (!sessionRows.isEmpty()) {
            avgV = sessionRows.stream()
                    .mapToDouble(r -> parseVE(str(r.get("moodParams")))[0]).average().orElse(0.5);
            avgE = sessionRows.stream()
                    .mapToDouble(r -> parseVE(str(r.get("moodParams")))[1]).average().orElse(0.5);
        }
        String moodCn = moodCn(avgV, avgE);

        // Week label
        int weekNum = weekStart.get(WeekFields.ISO.weekOfWeekBasedYear());
        String weekLabel = "WK" + weekNum;
        String dateRange = weekStart.format(DISP) + "—" + weekEnd.minusDays(1).format(DISP);

        // User nickname
        User user = userMapper.selectById(userId);
        String nickname = user != null ? user.getUsername() : "MoodFM User";

        // AI narrative
        AiNarrative ai = callAI(weekLabel, dateRange,
                tracks, listenTime, topGenre, moodCn,
                String.valueOf(avgV), String.valueOf(avgE),
                mostPlayed != null ? mostPlayed.getTitle()  : "—",
                mostPlayed != null ? mostPlayed.getArtist() : "—",
                mostPlayed != null ? String.valueOf(mostPlayed.getPlayCount()) : "0",
                String.valueOf(discRows.size()));

        WeeklyReportVO vo = WeeklyReportVO.builder()
                .weekLabel(weekLabel)
                .dateRange(dateRange)
                .titleEn1("A week of")
                .headlineWord(ai.headlineWord)
                .headlineWord2(ai.headlineWord2)
                .titleCn(ai.titleCn)
                .summary(ai.summary)
                .essayBody(ai.essayBody)
                .quote(ai.quote)
                .userNickname(nickname)
                .stats(WeeklyReportVO.WeekStats.builder()
                        .tracks(String.valueOf(tracks))
                        .listenTime(listenTime)
                        .newDiscovered(String.valueOf(discRows.size()))
                        .moodMatch(moodMatch)
                        .build())
                .mostPlayedSong(mostPlayed)
                .topDiscoveries(discoveries)
                .build();

        // Persist
        try {
            String json = objectMapper.writeValueAsString(vo);
            WeeklyReport row = new WeeklyReport();
            row.setUserId(userId);
            row.setWeekStart(weekStart);
            row.setWeekEnd(weekEnd);
            row.setData(json);
            row.setAiSummary(ai.summary);
            row.setGeneratedAt(java.time.LocalDateTime.now());
            weeklyReportMapper.insert(row);
            vo.setId(row.getId());
        } catch (Exception e) {
            log.warn("Failed to persist weekly report: {}", e.getMessage());
        }

        return vo;
    }

    // ─── AI Call ──────────────────────────────────────────────────

    private AiNarrative callAI(String weekLabel, String dateRange,
                                long tracks, String listenTime, String topGenre,
                                String moodCn, String valence, String energy,
                                String topSong, String topArtist, String topPlayCount,
                                String newDiscovered) {
        try {
            String prompt = new String(promptResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("{{weekLabel}}", weekLabel)
                    .replace("{{dateRange}}", dateRange)
                    .replace("{{tracks}}", String.valueOf(tracks))
                    .replace("{{listenTime}}", listenTime)
                    .replace("{{topGenre}}", topGenre)
                    .replace("{{moodCn}}", moodCn)
                    .replace("{{valence}}", valence)
                    .replace("{{energy}}", energy)
                    .replace("{{topSong}}", topSong)
                    .replace("{{topArtist}}", topArtist)
                    .replace("{{topPlayCount}}", topPlayCount)
                    .replace("{{newDiscovered}}", newDiscovered);

            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            String raw = extractJson(response);
            JsonNode node = objectMapper.readTree(raw);
            return new AiNarrative(
                    node.path("headlineWord").asText("quiet"),
                    node.path("headlineWord2").asText("light."),
                    node.path("titleCn").asText("静听一周"),
                    node.path("summary").asText(""),
                    node.path("essayBody").asText(""),
                    node.path("quote").asText("")
            );
        } catch (Exception e) {
            log.warn("Weekly report AI call failed: {}", e.getMessage());
            return new AiNarrative("quiet", "light.", "静听一周", "", "", "");
        }
    }

    private record AiNarrative(String headlineWord, String headlineWord2,
                                String titleCn, String summary,
                                String essayBody, String quote) {}

    // ─── Deserialization ──────────────────────────────────────────

    private WeeklyReportVO deserialize(WeeklyReport row) {
        try {
            WeeklyReportVO vo = objectMapper.readValue(row.getData(), WeeklyReportVO.class);
            vo.setId(row.getId());
            return vo;
        } catch (Exception e) {
            log.warn("Failed to deserialize weekly report {}: {}", row.getId(), e.getMessage());
            return WeeklyReportVO.builder()
                    .id(row.getId())
                    .weekLabel("WK—")
                    .headlineWord("quiet")
                    .headlineWord2("light.")
                    .titleCn("静听一周")
                    .build();
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────

    private String extractJson(String s) {
        if (s == null) return "{}";
        s = s.trim();
        int start = s.indexOf('{'), end = s.lastIndexOf('}');
        return (start >= 0 && end > start) ? s.substring(start, end + 1) : s;
    }

    private String formatSec(long sec) {
        if (sec <= 0) return "0m";
        long h = sec / 3600, m = (sec % 3600) / 60;
        return h > 0 ? h + "h " + m + "m" : m + "m";
    }

    private String moodCn(double v, double e) {
        if (e > 0.65)                        return "高能";
        if (v > 0.6 && e <= 0.65)            return "平静";
        if (v < 0.4)                          return "忧郁";
        if (e >= 0.4 && v >= 0.4)            return "专注";
        return "温柔";
    }

    private double[] parseVE(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode mood = root.path("mood");
            return new double[]{ clamp(mood.path("valence").asDouble(0.5)),
                                 clamp(mood.path("energy").asDouble(0.5)) };
        } catch (Exception ex) {
            return new double[]{0.5, 0.5};
        }
    }

    private double clamp(double v) { return Math.max(0, Math.min(1, v)); }

    private int toInt(Object o) { return o instanceof Number n ? n.intValue() : 0; }

    private long toLong(Object o) { return o instanceof Number n ? n.longValue() : 0L; }

    private String str(Object o) { return o == null ? "" : String.valueOf(o); }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }
}
