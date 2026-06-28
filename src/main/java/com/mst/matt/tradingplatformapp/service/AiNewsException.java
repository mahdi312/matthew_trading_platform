package com.mst.matt.tradingplatformapp.service;

/**
 * Thrown when AI news / insight fetching fails (missing API key, network error, etc.).
 */
public class AiNewsException extends RuntimeException {

    public AiNewsException(String message) {
        super(message);
    }

    public AiNewsException(String message, Throwable cause) {
        super(message, cause);
    }
}
