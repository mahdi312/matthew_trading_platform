package com.mst.matt.marketservice.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One OHLCV candlestick bar.
 *
 * <p>Ported from the desktop monolith's {@code OhlcvBar}.
 * Stored per symbol + timeframe for indicator calculation and chart rendering.
 *
 * <h3>Table strategy decision</h3>
 * <p>The desktop monolith used a dynamic-table-per-symbol pattern (one table per
 * {@code SYMBOL_PROVIDER_TF} combination) to keep bar counts manageable.
 * In this microservice context we consolidate into a <strong>single partitioned
 * table</strong> ({@code ohlcv_bars}) with a composite index on
 * {@code (symbol, timeframe, open_time)} for the following reasons:
 * <ul>
 *   <li>market-service uses a dedicated database per the microservice pattern,
 *       so table proliferation is contained to one schema.</li>
 *   <li>PostgreSQL's native partitioning (RANGE on open_time) scales to hundreds
 *       of millions of rows without requiring application-level DDL.</li>
 *   <li>Simplifies the repository layer — no DDL generation in application code.</li>
 * </ul>
 * The dynamic-table services ({@link com.mst.matt.marketservice.service.DynamicOhlcvTableService},
 * {@link com.mst.matt.marketservice.service.MarketDataTableNameUtil}) are still
 * ported for compatibility, but the single-table path is the default and
 * recommended approach going forward.
 */
@Entity
@Table(name = "ohlcv_bars",
        indexes = {
                @Index(name = "idx_ohlcv_symbol_tf_time",
                        columnList = "symbol,timeframe,openTime")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OhlcvBar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false, length = 10)
    private String timeframe;               // "1m","5m","15m","1h","4h","1d","1w"

    @Column(nullable = false)
    private LocalDateTime openTime;

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal open;

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal high;

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal low;

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal close;

    @Column(nullable = false, precision = 30, scale = 8)
    private BigDecimal volume;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssetType assetType;

    /** Optional: identifies which provider delivered this bar (e.g., "BINANCE"). */
    @Column(length = 32)
    private String provider;
}
