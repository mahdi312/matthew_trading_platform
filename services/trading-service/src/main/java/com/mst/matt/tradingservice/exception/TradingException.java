package com.mst.matt.tradingservice.exception;

/**
 * Domain-level trading exception — thrown by the {@code BitUnixTradingProvider}
 * and service layer when a business rule violation is detected before or after
 * calling the broker (e.g., unsupported order type, capability mismatch,
 * insufficient balance detected locally).
 */
public class TradingException extends RuntimeException {

    public TradingException(String message) {
        super(message);
    }

    public TradingException(String message, Throwable cause) {
        super(message, cause);
    }
}
