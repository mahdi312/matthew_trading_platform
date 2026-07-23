package com.mst.matt.contracts.dto;

import com.mst.matt.contracts.enums.BrokerType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Lightweight real-time price tick published by {@code market-service} and
 * consumed by services that need the latest price (e.g., trading-service,
 * notification-service for price alerts).
 *
 * <p>Designed to be serialisable to JSON and suitable for WebSocket push,
 * Kafka messages, or SSE streams.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceTickDto {

    /**
     * Broker / data-source that produced this tick.
     */
    private BrokerType source;

    /**
     * Trading symbol (e.g., "BTCUSDT", "AAPL").
     */
    private String symbol;

    /**
     * Last traded price.
     */
    private BigDecimal lastPrice;

    /**
     * Best bid price in the order book; may be {@code null} for some sources.
     */
    private BigDecimal bidPrice;

    /**
     * Best ask price in the order book; may be {@code null} for some sources.
     */
    private BigDecimal askPrice;

    /**
     * 24-hour trading volume in base-asset units; may be {@code null}.
     */
    private BigDecimal volume24h;

    /**
     * 24-hour price change percentage; may be {@code null}.
     */
    private BigDecimal changePercent24h;

    /**
     * Server-side timestamp when this tick was produced.
     */
    private Instant timestamp;
}
