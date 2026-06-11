package com.mst.matt.tradingplatformapp.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Universal symbol catalogue — one entry per tradable symbol.
 * Populated by {@link com.mst.matt.tradingplatformapp.service.symbol.SymbolSyncService}
 * from external APIs (Binance, CoinGecko, Finnhub, etc.).
 */
@Entity
@Table(name = "symbol_entries",
        indexes = {
                @Index(name = "idx_symbol_entries_type",   columnList = "assetType"),
                @Index(name = "idx_symbol_entries_search", columnList = "symbol, name")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_symbol_entries_type_symbol",
                columnNames = {"assetType", "symbol"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SymbolEntry {

    public enum AssetType { CRYPTO, STOCK, FOREX }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Ticker / trading pair (upper-case), e.g. "BTCUSDT", "AAPL", "EURUSD". */
    @Column(nullable = false, length = 40)
    private String symbol;

    /** Full name, e.g. "Bitcoin", "Apple Inc.", "Euro / US Dollar". */
    @Column(length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AssetType assetType;

    /** Exchange or source, e.g. "Binance", "NASDAQ", "Forex". */
    @Column(length = 60)
    private String exchange;

    /** Populated by which external source fetched this entry. */
    @Column(length = 40)
    private String source;
}
