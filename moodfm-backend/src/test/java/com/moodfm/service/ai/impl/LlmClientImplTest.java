package com.moodfm.service.ai.impl;

import com.moodfm.common.exception.LlmException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for LlmClientImpl: verifies timeout, retry, and LlmException propagation.
 * Uses a very short timeout (1s) to keep tests fast.
 */
@ExtendWith(MockitoExtension.class)
class LlmClientImplTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    private LlmClientImpl client;

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.shutdown();
        }
    }

    // -----------------------------------------------------------------------
    // Happy path
    // -----------------------------------------------------------------------

    @Test
    void complete_successOnFirstAttempt_returnsContent() {
        AtomicInteger callCount = new AtomicInteger(0);
        when(chatClient.prompt().user(anyString()).system(anyString()).call().content())
                .thenAnswer(inv -> {
                    callCount.incrementAndGet();
                    return "hello from LLM";
                });

        client = new LlmClientImpl(chatClient, 5L);
        String result = client.complete("sys", "user msg");

        assertEquals("hello from LLM", result);
        assertEquals(1, callCount.get(), "expected exactly 1 invocation (no retry needed)");
    }

    @Test
    void complete_nullSystem_skipsSystemPrompt_returnsContent() {
        // When system is null the impl calls .user(msg).call().content() (no .system())
        when(chatClient.prompt().user(anyString()).call().content())
                .thenReturn("no-system reply");

        client = new LlmClientImpl(chatClient, 5L);
        String result = client.complete(null, "user msg");

        assertEquals("no-system reply", result);
    }

    @Test
    void complete_blankSystem_skipsSystemPrompt_returnsContent() {
        when(chatClient.prompt().user(anyString()).call().content())
                .thenReturn("blank-system reply");

        client = new LlmClientImpl(chatClient, 5L);
        String result = client.complete("   ", "user msg");

        assertEquals("blank-system reply", result);
    }

    // -----------------------------------------------------------------------
    // Retry path: first call fails, second succeeds
    // -----------------------------------------------------------------------

    @Test
    void complete_failsOnFirstAttempt_retriesAndSucceeds() {
        AtomicInteger callCount = new AtomicInteger(0);
        when(chatClient.prompt().user(anyString()).call().content()).thenAnswer(inv -> {
            if (callCount.incrementAndGet() == 1) {
                throw new RuntimeException("transient failure");
            }
            return "retry succeeded";
        });

        client = new LlmClientImpl(chatClient, 5L);
        String result = client.complete(null, "user msg");

        assertEquals("retry succeeded", result);
        assertEquals(2, callCount.get(), "expected exactly 2 invocations (1 fail + 1 success)");
    }

    // -----------------------------------------------------------------------
    // Exhausted retry: both attempts fail → LlmException
    // -----------------------------------------------------------------------

    @Test
    void complete_bothAttemptsFail_throwsLlmException() {
        when(chatClient.prompt().user(anyString()).call().content())
                .thenThrow(new RuntimeException("permanent error"));

        client = new LlmClientImpl(chatClient, 5L);

        LlmException ex = assertThrows(LlmException.class,
                () -> client.complete(null, "user msg"));
        assertEquals(true, ex.getMessage().contains("permanent error"),
                "LlmException message should contain root cause; was: " + ex.getMessage());
    }

    // -----------------------------------------------------------------------
    // Timeout path: supplier blocks longer than timeout → LlmException
    // -----------------------------------------------------------------------

    @Test
    void complete_callExceedsTimeout_throwsLlmException() {
        when(chatClient.prompt().user(anyString()).call().content()).thenAnswer(inv -> {
            Thread.sleep(3_000); // longer than the 1s timeout
            return "should not reach";
        });

        // 1-second timeout so the test finishes quickly
        client = new LlmClientImpl(chatClient, 1L);

        LlmException ex = assertThrows(LlmException.class,
                () -> client.complete(null, "slow call"));
        // Should mention timeout
        assertEquals(true, ex.getMessage().toLowerCase().contains("timed out")
                        || ex.getMessage().toLowerCase().contains("timeout")
                        || ex.getMessage().toLowerCase().contains("failed"),
                "LlmException should mention timeout; was: " + ex.getMessage());
    }
}
