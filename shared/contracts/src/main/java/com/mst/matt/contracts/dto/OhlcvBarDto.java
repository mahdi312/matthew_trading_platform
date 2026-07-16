package com.mst.matt.contracts.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One OHLCV (Open / High / Low / Close / Volume) candlestick bar.
 *
 * <p>Returned by {@link com.mst.matt.contracts.broker.market.MarketDataProvider#getOhlcv}.
 * Cross-service DTO — no JPA annotations.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OhlcvBarDto {

    /** Bar open timestamp (start of the interval). */
    private Instant openTime;

    /** Bar close timestamp (end of the interval). */
    private Instant closeTime;

    /** Opening price. */
    private BigDecimal open;

    /** Highest price during the interval. */
    private BigDecimal high;

    /** Lowest price during the interval. */
    private BigDecimal low;

    /** Closing price. */
    private BigDecimal close;

    /** Volume traded during the interval in base-asset units. */
    private BigDecimal volume;

    /** Number of trades during the interval; may be {@code null} if unsupported. */
    private Long tradeCount;
}
