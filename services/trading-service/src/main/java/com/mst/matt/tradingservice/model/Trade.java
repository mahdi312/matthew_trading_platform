package com.mst.matt.tradingservice.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A single trade record, ported from the desktop monolith.
 *
 * <p>Supports both Long and Short directions across all asset types.
 * Trades can be either manually logged (journal-only, no live broker) or
 * live-broker trades whose orders are placed via {@link com.mst.matt.tradingservice.bitunix.BitUnixTradingProvider}.
 *
 * <p>The {@code brokerOrderId} field ties a manually-journalled or broker-placed
 * trade back to the live broker's order ID (null for manual-only trades).
 * The {@code source} field distinguishes manual entries from broker imports/live trades.
 */
@Entity
@Table(name = "trades")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** User/profile ID — links to identity-service; no FK across service boundaries. */
    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String symbol;                   // e.g. "BTCUSDT", "AAPL", "EURUSD"

    @Column(nullable = false)
    private String assetName;               // Full name: "Bitcoin", "Apple Inc."

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssetType assetType;            // CRYPTO, STOCK, FOREX

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TradeDirection direction;       // LONG, SHORT

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TradeStatus status;             // OPEN, CLOSED, CANCELLED

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TradeSource source = TradeSource.MANUAL; // MANUAL, BROKER_LIVE, BROKER_IMPORT

    @Column(length = 64)
    private String brokerOrderId;           // live-broker order ID (null for manual trades)

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal entryPrice;

    @Column(precision = 20, scale = 8)
    private BigDecimal exitPrice;           // null if trade is still OPEN

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal quantity;

    @Column(precision = 20, scale = 8)
    private BigDecimal stopLoss;

    @Column(precision = 20, scale = 8)
    private BigDecimal takeProfit;

    @Column(precision = 20, scale = 8)
    private BigDecimal fee;                 // Trading fees

    @Column(nullable = false)
    private LocalDateTime entryTime;

    @Column
    private LocalDateTime exitTime;

    @Column(length = 1000)
    private String notes;

    @Column
    private String exchange;               // "Binance", "Kraken", "NYSE", etc.

    @Column
    private String strategy;              // User label: "Ichimoku Breakout", etc.

    @Column(length = 512)
    private String screenshotPath;

    // ── Calculated fields (updated on save) ───────────────────────

    @Column(precision = 20, scale = 8)
    private BigDecimal pnlAmount;          // Raw P&L in quote currency

    @Column(precision = 10, scale = 4)
    private BigDecimal pnlPercent;         // P&L as percentage

    @Column(precision = 20, scale = 8)
    private BigDecimal totalInvested;      // entryPrice × quantity

    @Column
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        computePnL();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        computePnL();
    }

    /**
     * Recomputes P&L fields based on direction and prices.
     * Called automatically before every persist/update.
     */
    public void computePnL() {
        if (entryPrice == null || quantity == null) return;

        totalInvested = entryPrice.multiply(quantity);

        if (exitPrice == null) {
            // Still open: clear any previously realized P&L from an edited trade.
            pnlAmount = null;
            pnlPercent = null;
            return;
        }

        BigDecimal priceDiff = direction == TradeDirection.LONG
                ? exitPrice.subtract(entryPrice)
                : entryPrice.subtract(exitPrice);

        pnlAmount = priceDiff.multiply(quantity);
        if (fee != null) pnlAmount = pnlAmount.subtract(fee);

        if (entryPrice.compareTo(BigDecimal.ZERO) != 0) {
            pnlPercent = priceDiff
                    .divide(entryPrice, 8, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }
    }

    public enum AssetType      { CRYPTO, STOCK, FOREX, COMMODITY, INDEX }
    public enum TradeDirection { LONG, SHORT }
    public enum TradeStatus    { OPEN, CLOSED, CANCELLED }

    /**
     * Distinguishes how this trade record was created:
     * <ul>
     *   <li>{@code MANUAL} — user typed it in the trade journal (no broker API call)</li>
     *   <li>{@code BROKER_LIVE} — placed via {@link com.mst.matt.tradingservice.bitunix.BitUnixTradingProvider}</li>
     *   <li>{@code BROKER_IMPORT} — imported from a broker CSV via {@code BrokerImportService}</li>
     * </ul>
     */
    public enum TradeSource    { MANUAL, BROKER_LIVE, BROKER_IMPORT }
}
