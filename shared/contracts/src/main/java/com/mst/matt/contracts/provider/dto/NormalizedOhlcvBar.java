package com.mst.matt.contracts.provider.dto;

import com.mst.matt.contracts.enums.AssetClass;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Normalized OHLCV (Open / High / Low / Close / Volume) candlestick bar
 * returned by {@link com.mst.matt.contracts.provider.ohlcv.OhlcvDataProvider}.
 *
 * <h3>Design contract</h3>
 * <p>No consumer (AI tab, Analysis tab, Journal tab …) should ever see a
 * provider-specific response shape. All provider implementations must map
 * their raw API responses to this DTO before returning.</p>
 *
 * <p>Extends the broker-tied {@link com.mst.matt.contracts.dto.OhlcvBarDto}
 * concept but adds cross-provider metadata (source, asset class, symbol)
 * so consumers always know where the data came from.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NormalizedOhlcvBar {

    // ── Identity ──────────────────────────────────────────────────────────────

    /**
     * Canonical symbol as understood by the platform (e.g., "BTCUSDT",
     * "AAPL", "EURUSD"). Providers must map their own symbol format to
     * the platform canonical form before populating this field.
     */
    private String symbol;

    /** Asset class this bar belongs to. */
    private AssetClass assetClass;

    /**
     * Name of the data provider that sourced this bar (e.g., "ALPHA_VANTAGE",
     * "BINANCE", "OANDA"). Matches a {@code providerName} registered in
     * {@link com.mst.matt.contracts.provider.registry.ProviderRegistry}.
     */
    private String providerName;

    // ── Time ──────────────────────────────────────────────────────────────────

    /** Bar open timestamp (start of the interval). */
    private Instant openTime;

    /** Bar close timestamp (end of the interval, exclusive). */
    private Instant closeTime;

    /**
     * Timeframe / interval label (e.g., "1m", "5m", "15m", "1h", "4h",
     * "1d", "1w"). Standardised across all providers — not the raw API
     * field value.
     */
    private String interval;

    // ── Price ─────────────────────────────────────────────────────────────────

    /** Opening price of the interval. */
    private BigDecimal open;

    /** Highest price during the interval. */
    private BigDecimal high;

    /** Lowest price during the interval. */
    private BigDecimal low;

    /** Closing (last) price of the interval. */
    private BigDecimal close;

    // ── Volume ────────────────────────────────────────────────────────────────

    /**
     * Volume traded in base-asset units during the interval
     * (e.g., BTC for BTC/USDT, shares for equities).
     */
    private BigDecimal volume;

    /**
     * Volume traded in quote-asset (currency) units during the interval.
     * May be {@code null} if not provided by the data source.
     */
    private BigDecimal quoteVolume;

    /**
     * Number of individual trades during the interval.
     * May be {@code null} if not provided by the data source.
     */
    private Long tradeCount;

    // ── Quality flags ─────────────────────────────────────────────────────────

    /**
     * {@code true} if this bar is the most recent, still-open (in-progress)
     * candle; {@code false} for closed/historical bars.
     */
    private boolean isLive;

    /**
     * {@code true} if the bar was reconstructed (e.g., aggregated from
     * tick data or resampled from a finer timeframe) rather than returned
     * directly by the provider at the requested resolution.
     */
    private boolean isSynthetic;
}
