package com.mst.matt.aiservice.service;

/**
 * Thrown when the AI news / insight pipeline fails unrecoverably.
 * Ported from desktop {@code service.AiNewsException}.
 */
public class AiNewsException extends RuntimeException {

    public AiNewsException(String message) {
        super(message);
    }

    public AiNewsException(String message, Throwable cause) {
        super(message, cause);
    }
}
