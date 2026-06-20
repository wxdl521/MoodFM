package com.moodfm.service.ai.impl;

import com.moodfm.common.exception.LlmException;
import com.moodfm.service.ai.LlmClient;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Default implementation of {@link LlmClient}.
 * <p>
 * Each call is executed on a virtual-thread executor with a configurable timeout.
 * On timeout or exception the call is retried once. If the retry also fails a
 * {@link LlmException} is thrown.
 */
@Slf4j
@Service
public class LlmClientImpl implements LlmClient {

    private final ChatClient chatClient;
    private final long timeoutSeconds;
    private final ExecutorService executor;

    public LlmClientImpl(
            ChatClient chatClient,
            @Value("${llm.call.timeout-seconds:10}") long timeoutSeconds) {
        this.chatClient = chatClient;
        this.timeoutSeconds = timeoutSeconds;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @PreDestroy
    public void shutdown() {
        executor.close();
    }

    @Override
    public String complete(String system, String user) {
        // First attempt
        try {
            return callOnce(system, user);
        } catch (Exception firstEx) {
            log.warn("LLM call failed (attempt 1/2), retrying: {}", firstEx.getMessage());
        }

        // Single retry
        try {
            return callOnce(system, user);
        } catch (Exception retryEx) {
            throw new LlmException("LLM call failed after retry: " + retryEx.getMessage(), retryEx);
        }
    }

    private String callOnce(String system, String user) throws Exception {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(
                () -> invoke(system, user), executor);
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException te) {
            future.cancel(true);
            throw new LlmException("LLM call timed out after " + timeoutSeconds + "s", te);
        } catch (java.util.concurrent.ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new LlmException("LLM call execution error: " + cause.getMessage(), cause);
        }
    }

    private String invoke(String system, String user) {
        ChatClient.ChatClientRequestSpec spec = chatClient.prompt().user(user);
        if (StringUtils.hasText(system)) {
            spec = spec.system(system);
        }
        String content = spec.call().content();
        return content != null ? content : "";
    }
}
