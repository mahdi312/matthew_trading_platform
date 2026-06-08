package com.mst.matt.tradingplatformapp.model;

import com.mst.matt.tradingplatformapp.service.price.MarketDataProvider;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Registry of dynamically created OHLCV tables (e.g. {@code ETHUSDT_BINANCE_1h}).
 */
@Entity
@Table(name = "market_data_table_registry", indexes = {
        @Index(name = "idx_mdt_symbol_tf_provider",
                columnList = "symbol,timeframe,provider", unique = true),
        @Index(name = "idx_mdt_table_name", columnList = "tableName", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketDataTableRegistry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String tableName;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MarketDataProvider provider;

    @Column(nullable = false, length = 10)
    private String timeframe;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Trade.AssetType assetType;

    @Column
    private LocalDateTime lastSyncAt;

    @Column
    private LocalDateTime nextSyncAt;

    @Column(nullable = false)
    @Builder.Default
    private Integer barCount = 0;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
