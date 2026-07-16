package com.mst.matt.marketservice.exception;

/**
 * Wraps broker-specific market-data failures (HTTP errors, malformed
 * responses, non-zero API result codes, etc.) behind a single,
 * broker-agnostic runtime exception.
 *
 * <p>Per {@link com.mst.matt.contracts.broker.market.MarketDataProvider}'s
 * documented error-handling convention: implementations must wrap
 * broker-SDK-specific exceptions in this type; callers (controllers,
 * services in other modules) should only ever catch {@code MarketDataException},
 * never a broker-specific type like an OkHttp {@code IOException}.</p>
 */
public class MarketDataException extends RuntimeException {

    public MarketDataException(String message) {
        super(message);
    }

    public MarketDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
