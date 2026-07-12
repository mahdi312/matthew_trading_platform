package com.mst.matt.contracts.dto;

import com.mst.matt.contracts.enums.BrokerType;
import lombok.AllArgsConstructor;
<parameter name="content">import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Full ticker snapshot for a given symbol — richer than {@link PriceTickDto}
 * as it includes 24-hour statistics.
 *
 * <p>Returned by {@link com.mst.matt.contracts.broker.market.MarketDataProvider#getTickerSnapshot}.
 * Cross-service DTO — no JPA annotations.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TickerSnapshotDto {

    /** Broker / data-source that produced this snapshot. */
    private BrokerType source;

    /** Trading symbol (e.g., "BTCUSDT"). */
    private String symbol;

    /** Last traded price. */
    private BigDecimal lastPrice;

    /** Best bid price; {@code null} if unsupported by the source. */
    private BigDecimal bidPrice;

    /** Best ask price; {@code null} if unsupported by the source. */
    private BigDecimal askPrice;

    /** 24-hour open price. */
    private BigDecimal openPrice24h;

    /** 24-hour high price. */
    private BigDecimal highPrice24h;

    /** 24-hour low price. */
    private BigDecimal lowPrice24h;

    /** 24-hour volume in base-asset units. */
    private BigDecimal volume24h;

    /** 24-hour volume in quote-asset units; {@code null} if unsupported. */
    private BigDecimal quoteVolume24h;

    /** 24-hour price change in absolute terms. */
    private BigDecimal priceChange24h;

    /** 24-hour price change as a percentage. */
    private BigDecimal priceChangePercent24h;

    /** Timestamp when this snapshot was taken. */
    private Instant snapshotTime;
}
