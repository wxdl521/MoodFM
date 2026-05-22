package com.moodfm.service.insights.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodfm.domain.entity.WeeklyReport;
import com.moodfm.domain.vo.*;
import com.moodfm.mapper.FeedbackEventMapper;
import com.moodfm.mapper.MoodSessionMapper;
import com.moodfm.mapper.PlayRecordMapper;
import com.moodfm.mapper.WeeklyReportMapper;
import com.moodfm.service.insights.InsightsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InsightsServiceImpl implements InsightsService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MM/dd");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // Mood categories and their English/Chinese labels
    private static final Map<String, String> GENRE_CN = Map.of(
            "ambient",    "环境",
            "classical",  "古典",
            "folk",       "民谣",
            "indie",      "独立",
            "electronic", "电子",
            "jazz",       "爵士",
            "pop",        "流行",
            "rock",       "摇滚"
    );
    private static final List<String> GENRE_ORDER = List.of(
            "ambient", "classical", "folk", "indie", "electronic", "jazz", "pop", "rock");

    // Maps AI-returned Chinese genre tokens → radar English keys
    private static final Map<String, String> GENRE_ZH_TO_EN = Map.ofEntries(
            Map.entry("流行",     "pop"),
            Map.entry("华语流行", "pop"),
            Map.entry("国语流行", "pop"),
            Map.entry("粤语流行", "pop"),
            Map.entry("r&b",      "pop"),
            Map.entry("嘻哈",     "pop"),
            Map.entry("独立",     "indie"),
            Map.entry("indie",    "indie"),
            Map.entry("city pop", "indie"),
            Map.entry("摇滚",     "rock"),
            Map.entry("电子",     "electronic"),
            Map.entry("lo-fi",    "electronic"),
            Map.entry("爵士",     "jazz"),
            Map.entry("古典",     "classical"),
            Map.entry("轻音乐",   "classical"),
            Map.entry("民谣",     "folk"),
            Map.entry("ambient",  "ambient"),
            Map.entry("环境",     "ambient")
    );

    private static final Map<String, String> MOOD_HEADLINE = Map.of(
            "calm",   "serene",
            "dusk",   "wistful",
            "blue",   "melancholy",
            "energy", "electric",
            "focus",  "focused"
    );
    private static final Map<String, String> MOOD_ALT = Map.of(
            "calm",   "gentle",
            "dusk",   "tender",
            "blue",   "quiet",
            "energy", "alive",
            "focus",  "clear"
    );
    private static final Map<String, String> MOOD_CN_LABEL = Map.of(
            "calm",   "平静",
            "dusk",   "温柔",
            "blue",   "忧郁",
            "energy", "高能",
            "focus",  "专注"
    );

    private final PlayRecordMapper playRecordMapper;
    private final FeedbackEventMapper feedbackEventMapper;
    private final MoodSessionMapper moodSessionMapper;
    private final WeeklyReportMapper weeklyReportMapper;
    private final ObjectMapper objectMapper;

    // ─── Calendar ─────────────────────────────────────────────────

    @Override
    public CalendarMonthVO getCalendar(Long userId, int year, int month) {
        // Aggregate play stats per day
        List<Map<String, Object>> stats = playRecordMapper.selectCalendarStats(userId, year, month);
        Map<Integer, Map<String, Object>> statsByDay = new HashMap<>();
        for (Map<String, Object> row : stats) {
            int day = ((Number) row.get("day")).intValue();
            statsByDay.put(day, row);
        }

        // Aggregate mood per day from sessions
        List<Map<String, Object>> sessionRows = moodSessionMapper.selectMonthMoodParams(userId, year, month);
        Map<String, List<double[]>> moodByDate = new HashMap<>();
        for (Map<String, Object> row : sessionRows) {
            String date = String.valueOf(row.get("date"));
            String json = String.valueOf(row.get("moodParams"));
            double[] ve = parseValenceEnergy(json);
            moodByDate.computeIfAbsent(date, k -> new ArrayList<>()).add(ve);
        }

        // Build per-day mood map: date string → mood
        Map<Integer, String> moodByDay = new HashMap<>();
        for (Map.Entry<String, List<double[]>> e : moodByDate.entrySet()) {
            String dateStr = e.getKey();
            try {
                int day = LocalDate.parse(dateStr).getDayOfMonth();
                double avgV = e.getValue().stream().mapToDouble(a -> a[0]).average().orElse(0.5);
                double avgE = e.getValue().stream().mapToDouble(a -> a[1]).average().orElse(0.5);
                moodByDay.put(day, moodFromVE(avgV, avgE));
            } catch (Exception ex) {
                log.debug("mood date parse error: {}", dateStr);
            }
        }

        LocalDate now = LocalDate.now();
        int daysInMonth = LocalDate.of(year, month, 1).lengthOfMonth();
        int firstDOW = LocalDate.of(year, month, 1).getDayOfWeek().getValue() % 7;

        List<CalendarDayVO> days = new ArrayList<>();
        for (int d = 1; d <= daysInMonth; d++) {
            LocalDate date = LocalDate.of(year, month, d);
            boolean future = date.isAfter(now);
            Map<String, Object> stat = statsByDay.get(d);
            int tracks  = stat != null ? ((Number) stat.get("tracks")).intValue()  : 0;
            int minutes = stat != null ? ((Number) stat.get("minutes")).intValue() : 0;
            int sessions= stat != null ? ((Number) stat.get("sessions")).intValue(): 0;
            boolean empty = tracks == 0;
            String mood = (!future && !empty) ? moodByDay.getOrDefault(d, "dusk") : "empty";

            days.add(CalendarDayVO.builder()
                    .day(d).mood(mood).tracks(tracks).minutes(minutes)
                    .sessions(sessions).future(future).empty(empty || future)
                    .build());
        }

        return CalendarMonthVO.builder()
                .year(year).month(month)
                .firstDayOfWeek(firstDOW)
                .daysInMonth(daysInMonth)
                .days(days)
                .build();
    }

    // ─── Day Detail ───────────────────────────────────────────────

    @Override
    public DayDetailVO getDayDetail(Long userId, LocalDate date) {
        String dateStr = date.format(ISO);

        Map<String, Object> stat = playRecordMapper.selectDayStats(userId, dateStr);
        int tracks  = stat != null ? toInt(stat.get("tracks"))  : 0;
        int minutes = stat != null ? toInt(stat.get("minutes")) : 0;
        int sessions= stat != null ? toInt(stat.get("sessions")): 0;

        // Mood from sessions
        List<Map<String, Object>> sessionRows = moodSessionMapper.selectDayMoodParams(userId, dateStr);
        double avgV = 0.5, avgE = 0.5;
        if (!sessionRows.isEmpty()) {
            List<double[]> ves = sessionRows.stream()
                    .map(r -> parseValenceEnergy(String.valueOf(r.get("moodParams"))))
                    .collect(Collectors.toList());
            avgV = ves.stream().mapToDouble(a -> a[0]).average().orElse(0.5);
            avgE = ves.stream().mapToDouble(a -> a[1]).average().orElse(0.5);
        }
        String mood = tracks > 0 ? moodFromVE(avgV, avgE) : "empty";

        // Top tracks
        List<Map<String, Object>> trackRows = playRecordMapper.selectDayTopTracks(userId, dateStr);
        List<DayDetailVO.TrackVO> topTracks = trackRows.stream()
                .map(r -> DayDetailVO.TrackVO.builder()
                        .title(String.valueOf(r.get("title")))
                        .artist(String.valueOf(r.get("artist")))
                        .durationFormatted(formatSeconds(toInt(r.get("durationSeconds"))))
                        .playCount(toInt(r.get("playCount")))
                        .build())
                .collect(Collectors.toList());

        return DayDetailVO.builder()
                .day(date.getDayOfMonth())
                .mood(mood).tracks(tracks).minutes(minutes).sessions(sessions)
                .topTracks(topTracks)
                .build();
    }

    // ─── Summary ──────────────────────────────────────────────────

    @Override
    public InsightsSummaryVO getSummary(Long userId, int days) {
        // Listening time
        Map<String, Object> timeRow = playRecordMapper.selectTotalSeconds(userId, days);
        long totalSec = timeRow != null ? toLong(timeRow.get("totalSeconds")) : 0L;
        String listeningTime = formatSecondsLong(totalSec);

        // Feedback accuracy: likes from feedback_events, skips from play_records
        long likes = feedbackEventMapper.countLikes(userId, days);
        Map<String, Object> fbRow = playRecordMapper.selectFeedbackCounts(userId, days);
        long skips = fbRow != null ? toLong(fbRow.get("skips")) : 0L;
        String aiAccuracy = (likes + skips) > 0
                ? Math.round(100.0 * likes / (likes + skips)) + "%"
                : "—";

        // Top genre: prefer play_records.songs.features.genre; fall back to mood_sessions.preferred_genres
        List<Map<String, Object>> genreRows = playRecordMapper.selectGenreCounts(userId, days);
        String topGenre = "—", topGenreRatio = "";
        if (!genreRows.isEmpty()) {
            long total = genreRows.stream().mapToLong(r -> toLong(r.get("cnt"))).sum();
            Map<String, Object> top = genreRows.get(0);
            topGenre = capitalize(String.valueOf(top.get("genre")));
            if (total > 0) topGenreRatio = Math.round(100.0 * toLong(top.get("cnt")) / total) + "%";
        } else {
            // songs table has no features data; derive from AI-inferred preferred_genres
            List<Map<String, Object>> prefRows = moodSessionMapper.selectPreferredGenreCounts(userId, days);
            if (!prefRows.isEmpty()) {
                long total = prefRows.stream().mapToLong(r -> toLong(r.get("cnt"))).sum();
                Map<String, Object> top = prefRows.get(0);
                String rawGenre = String.valueOf(top.get("genre")).trim();
                String enGenre = GENRE_ZH_TO_EN.getOrDefault(rawGenre.toLowerCase(),
                        GENRE_ZH_TO_EN.getOrDefault(rawGenre, rawGenre));
                topGenre = capitalize(enGenre);
                if (total > 0) topGenreRatio = Math.round(100.0 * toLong(top.get("cnt")) / total) + "%";
            }
        }

        // Mood key: only count sessions where user actually typed a mood (exclude song-seed defaults)
        List<Map<String, Object>> sessionRows = moodSessionMapper.selectRealMoodParams(userId, days);
        Map<String, Integer> moodCount = new HashMap<>();
        for (Map<String, Object> r : sessionRows) {
            double[] ve = parseValenceEnergy(String.valueOf(r.get("moodParams")));
            String m = moodFromVE(ve[0], ve[1]);
            moodCount.merge(m, 1, Integer::sum);
        }
        String moodKey = moodCount.isEmpty() ? null
                : moodCount.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey();

        // Week label & range
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);
        int weekNum = endDate.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear());
        String weekLabel = days == 7 ? "本周 · WK" + weekNum : (days == 30 ? "本月" : "近三月");
        DateTimeFormatter disp = DateTimeFormatter.ofPattern("MM/dd");
        String weekRange = startDate.format(disp) + " — " + endDate.format(disp);

        InsightsSummaryVO.Stats stats = InsightsSummaryVO.Stats.builder()
                .totalListeningTime(listeningTime)
                .topGenre(topGenre)
                .topGenreRatio(topGenreRatio)
                .aiAccuracy(aiAccuracy)
                .moodKey(moodKey == null ? "—" : MOOD_CN_LABEL.getOrDefault(moodKey, "平静"))
                .moodSub(moodKey == null ? "" : MOOD_HEADLINE.getOrDefault(moodKey, "serene"))
                .weekLabel(weekLabel)
                .weekRange(weekRange)
                .listeningVsLast("")
                .build();

        String headline = moodKey == null ? "serene" : MOOD_HEADLINE.getOrDefault(moodKey, "serene");
        String headlineAlt = moodKey == null ? "gentle" : MOOD_ALT.getOrDefault(moodKey, "gentle");
        String headlineCn = moodKey == null ? "平静" : MOOD_CN_LABEL.getOrDefault(moodKey, "平静");

        // Latest weekly report (for weeklyReportId / essay preview)
        WeeklyReport latestReport = weeklyReportMapper.selectLatestByUserId(userId);
        Long weeklyReportId = latestReport != null ? latestReport.getId() : null;
        String essayTitle = null, essayBody = null;
        InsightsSummaryVO.AiSummary aiSummary = null;
        if (latestReport != null && latestReport.getData() != null) {
            try {
                JsonNode vo = objectMapper.readTree(latestReport.getData());
                essayTitle = vo.path("titleCn").asText(null);
                essayBody  = vo.path("essayBody").asText(null);
                aiSummary  = InsightsSummaryVO.AiSummary.builder()
                        .title(vo.path("titleCn").asText(""))
                        .body(vo.path("summary").asText(""))
                        .reportId(latestReport.getId())
                        .build();
            } catch (Exception e) {
                log.debug("Failed to parse latest weekly report JSON: {}", e.getMessage());
            }
        }

        // Previous report cards
        List<WeeklyReport> reportRows = weeklyReportMapper.selectListByUserId(userId);
        List<InsightsSummaryVO.ReportCard> prevReports = reportRows.stream().map(r -> {
            String mood = "dusk";
            if (r.getData() != null) {
                try {
                    JsonNode vo = objectMapper.readTree(r.getData());
                    String hw = vo.path("headlineWord").asText("");
                    if (hw.matches("quiet|gentle|tender")) mood = "calm";
                    else if (hw.matches("alive|electric|restless")) mood = "energy";
                    else if (hw.matches("melancholy|grey|heavy")) mood = "blue";
                } catch (Exception ignored) {}
            }
            int wkNum = r.getWeekStart().get(WeekFields.ISO.weekOfWeekBasedYear());
            return InsightsSummaryVO.ReportCard.builder()
                    .id(r.getId())
                    .week(wkNum)
                    .year(r.getWeekStart().getYear())
                    .title(r.getAiSummary() != null ? r.getAiSummary() : "WK" + wkNum)
                    .mood(mood)
                    .build();
        }).collect(Collectors.toList());

        return InsightsSummaryVO.builder()
                .headline(headline)
                .headlineAlt(headlineAlt)
                .headlineCn(headlineCn)
                .stats(stats)
                .essayTitle(essayTitle)
                .essayBody(essayBody)
                .weeklyReportId(weeklyReportId)
                .aiSummary(aiSummary)
                .previousReports(prevReports)
                .build();
    }

    // ─── Mood Trend ───────────────────────────────────────────────

    @Override
    public MoodTrendVO getMoodTrend(Long userId, int days) {
        // User mood from sessions grouped by date
        List<Map<String, Object>> sessionRows = moodSessionMapper.selectRecentMoodParams(userId, days);
        Map<String, List<double[]>> byDate = new LinkedHashMap<>();
        for (Map<String, Object> r : sessionRows) {
            String date = String.valueOf(r.get("date"));
            double[] ve = parseValenceEnergy(String.valueOf(r.get("moodParams")));
            byDate.computeIfAbsent(date, k -> new ArrayList<>()).add(ve);
        }

        // Song mood from play records
        List<Map<String, Object>> songRows = playRecordMapper.selectDailySongMood(userId, days);
        Map<String, Double> songMoodByDate = new LinkedHashMap<>();
        for (Map<String, Object> r : songRows) {
            String date = String.valueOf(r.get("date"));
            Object e = r.get("avgEnergy");
            if (e != null) songMoodByDate.put(date, ((Number) e).doubleValue());
        }

        // Build aligned date series (last N days)
        LocalDate end = LocalDate.now();
        List<String> labels = new ArrayList<>();
        List<Double> userMood = new ArrayList<>();
        List<Double> songMoodList = new ArrayList<>();
        int lowIdx = -1;
        double lowVal = Double.MAX_VALUE;

        DateTimeFormatter labelFmt = DateTimeFormatter.ofPattern("MM/dd");
        for (int i = days - 1; i >= 0; i--) {
            LocalDate d = end.minusDays(i);
            String key = d.format(ISO);
            labels.add(d.format(labelFmt));

            List<double[]> ves = byDate.get(key);
            double uv = ves != null
                    ? ves.stream().mapToDouble(a -> a[0]).average().orElse(0.5)
                    : 0.5;
            userMood.add(round2(uv));
            songMoodList.add(round2(songMoodByDate.getOrDefault(key, 0.5)));

            if (uv < lowVal && ves != null) { lowVal = uv; lowIdx = labels.size() - 1; }
        }

        return MoodTrendVO.builder()
                .labels(labels)
                .userMood(userMood)
                .songMood(songMoodList)
                .lowPointIndex(lowIdx)
                .build();
    }

    // ─── Genre Radar ──────────────────────────────────────────────

    @Override
    public List<GenreRadarItemVO> getGenreRadar(Long userId, int days) {
        List<Map<String, Object>> rows = playRecordMapper.selectGenreCounts(userId, days);

        // songs.features.genre is not populated by the current pipeline;
        // fall back to AI-inferred preferred_genres from mood sessions
        if (rows.isEmpty()) {
            rows = moodSessionMapper.selectPreferredGenreCounts(userId, days);
        }
        if (rows.isEmpty()) return buildEmptyRadar();

        Map<String, Long> counts = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            String raw = String.valueOf(r.get("genre")).toLowerCase().trim();
            // normalize Chinese AI genre tokens to English radar keys
            String g = GENRE_ZH_TO_EN.getOrDefault(raw, GENRE_ZH_TO_EN.getOrDefault(
                    String.valueOf(r.get("genre")).trim(), raw));
            counts.merge(g, toLong(r.get("cnt")), Long::sum);
        }
        long max = counts.values().stream().mapToLong(v -> v).max().orElse(1);

        return GENRE_ORDER.stream().map(g -> GenreRadarItemVO.builder()
                .genre(capitalize(g))
                .genreCn(GENRE_CN.getOrDefault(g, g))
                .value(round2(counts.getOrDefault(g, 0L) / (double) max))
                .build())
                .collect(Collectors.toList());
    }

    private List<GenreRadarItemVO> buildEmptyRadar() {
        return GENRE_ORDER.stream().map(g -> GenreRadarItemVO.builder()
                .genre(capitalize(g))
                .genreCn(GENRE_CN.getOrDefault(g, g))
                .value(0)
                .build())
                .collect(Collectors.toList());
    }

    // ─── Top Items ────────────────────────────────────────────────

    @Override
    public TopItemsVO getTopItems(Long userId, int days) {
        List<Map<String, Object>> artistRows = playRecordMapper.selectTopArtists(userId, days);
        long maxArtistSec = artistRows.isEmpty() ? 1
                : toLong(artistRows.get(0).get("totalSeconds"));

        List<TopItemsVO.ArtistItem> artists = artistRows.stream().map(r -> {
            long sec = toLong(r.get("totalSeconds"));
            return TopItemsVO.ArtistItem.builder()
                    .name(String.valueOf(r.get("artist")))
                    .playCount(toInt(r.get("playCount")))
                    .tag(toInt(r.get("playCount")) == 1 ? "1 TRACK" : toInt(r.get("playCount")) + " TRACKS")
                    .totalTime(formatSecondsLong(sec))
                    .proportion(round2((double) sec / Math.max(maxArtistSec, 1)))
                    .build();
        }).collect(Collectors.toList());

        List<Map<String, Object>> songRows = playRecordMapper.selectTopSongs(userId, days);
        int maxSongCount = songRows.isEmpty() ? 1
                : toInt(songRows.get(0).get("playCount"));

        List<TopItemsVO.SongItem> songs = songRows.stream().map(r -> {
            int cnt = toInt(r.get("playCount"));
            return TopItemsVO.SongItem.builder()
                    .title(String.valueOf(r.get("title")))
                    .artist(String.valueOf(r.get("artist")))
                    .playCount(cnt)
                    .proportion(round2((double) cnt / Math.max(maxSongCount, 1)))
                    .build();
        }).collect(Collectors.toList());

        return TopItemsVO.builder().artists(artists).songs(songs).build();
    }

    // ─── Helpers ──────────────────────────────────────────────────

    /** Parse [valence, energy] from moodParams JSON; defaults to [0.5, 0.5]. */
    private double[] parseValenceEnergy(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode mood = root.path("mood");
            double v = mood.path("valence").asDouble(0.5);
            double e = mood.path("energy").asDouble(0.5);
            return new double[]{clamp(v), clamp(e)};
        } catch (Exception ex) {
            return new double[]{0.5, 0.5};
        }
    }

    private String moodFromVE(double valence, double energy) {
        if (energy > 0.65)                          return "energy";
        if (valence > 0.6 && energy <= 0.65)        return "calm";
        if (valence < 0.4)                           return "blue";
        if (energy >= 0.4 && valence >= 0.4)         return "focus";
        return "dusk";
    }

    private String formatSeconds(int sec) {
        if (sec <= 0) return "";
        return String.format("%d:%02d", sec / 60, sec % 60);
    }

    private String formatSecondsLong(long sec) {
        if (sec <= 0) return "0m";
        long h = sec / 3600, m = (sec % 3600) / 60;
        return h > 0 ? h + "h " + m + "m" : m + "m";
    }

    private int toInt(Object o) {
        return o instanceof Number ? ((Number) o).intValue() : 0;
    }

    private long toLong(Object o) {
        return o instanceof Number ? ((Number) o).longValue() : 0L;
    }

    private double clamp(double v) { return Math.max(0, Math.min(1, v)); }

    private double round2(double v) { return Math.round(v * 100) / 100.0; }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }
}
