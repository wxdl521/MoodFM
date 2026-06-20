package com.moodfm.common.exception;

/**
 * Thrown by LlmClient when an LLM call fails after the retry budget is exhausted
 * (timeout, network error, or upstream API failure).
 * This is an unchecked exception so call sites can remain clean.
 */
public class LlmException extends RuntimeException {

    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
