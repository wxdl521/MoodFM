package com.moodfm.service.ai;

/**
 * Unified LLM text-completion façade.
 * Hides timeout, retry, and executor concerns from call sites.
 */
public interface LlmClient {

    /**
     * Send a text-completion request to the underlying LLM.
     *
     * @param system the system prompt; {@code null} or blank means no system prompt
     * @param user   the user message (must not be null/blank)
     * @return the model's text response (never null, may be empty string)
     * @throws com.moodfm.common.exception.LlmException if the call fails after the retry
     *         budget is exhausted (timeout, network error, or upstream API failure)
     */
    String complete(String system, String user);
}
