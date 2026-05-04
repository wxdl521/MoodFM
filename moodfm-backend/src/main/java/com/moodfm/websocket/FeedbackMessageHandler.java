package com.moodfm.websocket;

import com.moodfm.domain.dto.feedback.PlaybackFeedbackDto;
import com.moodfm.service.feedback.FeedbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class FeedbackMessageHandler {

    private final FeedbackService feedbackService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 接收前端播放反馈事件
     * 客户端发送到: /app/feedback
     */
    @MessageMapping("/feedback")
    public void handleFeedback(@Payload PlaybackFeedbackDto dto,
                                SimpMessageHeaderAccessor headerAccessor,
                                Principal principal) {
        if (principal == null) {
            log.warn("Unauthenticated WebSocket feedback, ignored");
            return;
        }

        Long userId = Long.parseLong(principal.getName());
        log.debug("Feedback from user {}: {} on song {}", userId, dto.getEventType(), dto.getSongId());

        feedbackService.record(userId, dto);

        // 通知客户端反馈已收到（可选）
        messagingTemplate.convertAndSendToUser(
                principal.getName(),
                "/queue/feedback-ack",
                Map.of("songId", dto.getSongId(), "eventType", dto.getEventType(), "status", "ok")
        );
    }
}
