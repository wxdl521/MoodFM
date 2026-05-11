package com.moodfm.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodfm.domain.entity.FeedbackEvent;
import com.moodfm.domain.entity.MoodSession;
import com.moodfm.domain.entity.PlayRecord;
import com.moodfm.domain.entity.User;
import com.moodfm.mapper.FeedbackEventMapper;
import com.moodfm.mapper.MoodSessionMapper;
import com.moodfm.mapper.PlayRecordMapper;
import com.moodfm.mapper.UserMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "数据导出", description = "导出用户全量数据")
@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class ExportController {

    private final UserMapper userMapper;
    private final PlayRecordMapper playRecordMapper;
    private final FeedbackEventMapper feedbackEventMapper;
    private final MoodSessionMapper moodSessionMapper;
    private final ObjectMapper objectMapper;

    @Operation(summary = "导出 JSON 格式全量数据")
    @GetMapping("/json")
    public void exportJson(
            HttpServletResponse response,
            @AuthenticationPrincipal UserDetails userDetails) throws Exception {

        Long userId = Long.parseLong(userDetails.getUsername());
        User user = userMapper.selectById(userId);

        int maxRecords = 100_000;

        List<PlayRecord> records = playRecordMapper.selectList(
                new LambdaQueryWrapper<PlayRecord>()
                        .eq(PlayRecord::getUserId, userId)
                        .last("LIMIT " + (maxRecords + 1)));
        boolean playRecordsTruncated = records.size() > maxRecords;
        if (playRecordsTruncated) records = records.subList(0, maxRecords);

        List<FeedbackEvent> events = feedbackEventMapper.selectList(
                new LambdaQueryWrapper<FeedbackEvent>()
                        .eq(FeedbackEvent::getUserId, userId)
                        .last("LIMIT " + (maxRecords + 1)));
        boolean eventsTruncated = events.size() > maxRecords;
        if (eventsTruncated) events = events.subList(0, maxRecords);

        List<MoodSession> sessions = moodSessionMapper.selectList(
                new LambdaQueryWrapper<MoodSession>()
                        .eq(MoodSession::getUserId, userId)
                        .last("LIMIT " + (maxRecords + 1)));
        boolean sessionsTruncated = sessions.size() > maxRecords;
        if (sessionsTruncated) sessions = sessions.subList(0, maxRecords);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user", Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "email", user.getEmail() != null ? user.getEmail() : "",
                "createdAt", user.getCreatedAt() != null ? user.getCreatedAt().toString() : ""));
        data.put("playRecords", records);
        data.put("feedbackEvents", events);
        data.put("moodSessions", sessions);
        if (playRecordsTruncated || eventsTruncated || sessionsTruncated) {
            data.put("_warning", "Some collections were truncated to " + maxRecords + " records.");
        }

        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=moodfm-export.json");

        objectMapper.findAndRegisterModules();
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(response.getOutputStream(), data);
    }

    @Operation(summary = "导出 CSV 格式播放记录")
    @GetMapping("/csv")
    public void exportCsv(
            HttpServletResponse response,
            @AuthenticationPrincipal UserDetails userDetails) throws Exception {

        Long userId = Long.parseLong(userDetails.getUsername());

        int maxCsvRecords = 100_000;
        List<Map<String, Object>> history = playRecordMapper.selectHistory(userId, null, maxCsvRecords + 1, 0);
        boolean truncated = history.size() > maxCsvRecords;
        if (truncated) history = history.subList(0, maxCsvRecords);

        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=moodfm-playrecords.csv");
        response.setCharacterEncoding("UTF-8");

        // BOM for Excel
        response.getOutputStream().write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});

        PrintWriter writer = response.getWriter();
        writer.println("songTitle,artist,platform,action,playedSeconds,totalSeconds,scene,playedAt");

        for (Map<String, Object> row : history) {
            writer.printf("\"%s\",\"%s\",\"%s\",\"%s\",%s,%s,\"%s\",\"%s\"%n",
                    escape(row.get("title")),
                    escape(row.get("artist")),
                    escape(row.get("platform")),
                    escape(row.get("action")),
                    row.get("playedSeconds"),
                    row.get("totalSeconds"),
                    escape(row.get("scene")),
                    escape(row.get("playedAt")));
        }
        if (truncated) {
            writer.printf("\"[WARNING: output truncated to %d records]\"%n", maxCsvRecords);
        }
        writer.flush();
    }

    private String escape(Object val) {
        if (val == null) return "";
        return val.toString().replace("\"", "\"\"");
    }
}
