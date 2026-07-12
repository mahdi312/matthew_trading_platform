package com.mst.matt.tradingplatformapp.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One OHLCV candlestick bar.
 * Stored per symbol + timeframe for indicator calculation.
 */
@Entity
@Table(name = "ohlcv_bars",
        indexes = {
                @Index(name = "idx_symbol_tf_time",
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
    private Trade.AssetType assetType;
}