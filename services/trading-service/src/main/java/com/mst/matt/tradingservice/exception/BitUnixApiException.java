package com.mst.matt.tradingservice.exception;

/**
 * Thrown when a BitUnix REST or WebSocket API call fails.
 *
 * <p>Covers both transport-level failures (network errors, HTTP 4xx/5xx)
 * and application-level failures (BitUnix returns a non-zero {@code code}
 * field in its response envelope).</p>
 *
 * <p>Callers that need to distinguish retriable errors from fatal ones
 * should inspect the error code; a future refinement can add a
 * {@code bitUnixErrorCode} field here.</p>
 */
public class BitUnixApiException extends RuntimeException {

    public BitUnixApiException(String message) {
        super(message);
    }

    public BitUnixApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
