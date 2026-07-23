package com.mst.matt.contracts.broker.market;

/**
 * Thrown by a {@link MarketDataProvider} when the requested OHLCV interval
 * (timeframe) is not supported by that provider.
 *
 * <p>Callers should catch this exception and either fall back to a supported
 * interval or propagate it as an HTTP 400 Bad Request to the client.</p>
 */
public class UnsupportedIntervalException extends RuntimeException {

    public UnsupportedIntervalException(String interval, String brokerName) {
        super(String.format(
                "Interval '%s' is not supported by broker '%s'. "
                + "Check BrokerCapabilities.supportedIntervals() for valid values.",
                interval, brokerName));
    }
}
