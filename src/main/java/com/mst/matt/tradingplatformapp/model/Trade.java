package com.mst.matt.tradingplatformapp.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A single trade record.
 * Supports both Long and Short directions across all asset types.
 *
 * <p>Uses id-only {@code equals}/{@code hashCode} so JavaFX table updates never
 * touch the lazy {@code profile} proxy after the Hibernate session closes.
 */
@Entity
@Table(name = "trades")
@Getter
@Setter
@ToString(exclude = "profile")
@NoArgsConstructor
@AllArgsConstructor
@Builder
// Prevent Lombok-generated equals/hashCode from touching the lazy UserProfile proxy.
// A closed Hibernate session causes LazyInitializationException when JavaFX
// TableView calls equals() during rendering (Fix for Issue #2).
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include   // only the PK is used for equality — no lazy-proxy access
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    @ToString.Exclude            // prevent toString() from touching the lazy proxy
    private UserProfile profile;

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
    private TradeStatus status;             // OPEN, CLOSED

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

    /**
     * Leverage multiplier used for this trade (default 1 = no leverage).
     * When set to values > 1 (e.g. 10x, 20x), P&L is scaled by this factor.
     */
    @Column(precision = 10, scale = 2)
    private BigDecimal leverage;            // e.g. 1, 5, 10, 20, 100

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

    /**
     * Optional path to a chart screenshot captured at the time this trade was created.
     * Stored as an absolute file path, e.g. ~/.trading-platform/screenshots/trade_123.png
     */
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
     * Recomputes P&L fields based on direction, prices and leverage.
     * Called automatically before every persist/update.
     *
     * <p>With leverage > 1, the P&L amount is multiplied by the leverage factor:
     * <pre>
     *   pnlAmount  = (priceDiff × qty × leverage) − fee
     *   pnlPercent = (priceDiff / entry) × leverage × 100
     * </pre>
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

        BigDecimal lev = (leverage != null && leverage.compareTo(BigDecimal.ONE) > 0)
                ? leverage : BigDecimal.ONE;

        BigDecimal priceDiff = direction == TradeDirection.LONG
                ? exitPrice.subtract(entryPrice)
                : entryPrice.subtract(exitPrice);

        pnlAmount = priceDiff.multiply(quantity).multiply(lev);
        if (fee != null) pnlAmount = pnlAmount.subtract(fee);

        if (entryPrice.compareTo(BigDecimal.ZERO) != 0) {
            pnlPercent = priceDiff
                    .divide(entryPrice, 8, java.math.RoundingMode.HALF_UP)
                    .multiply(lev)
                    .multiply(BigDecimal.valueOf(100));
        }
    }

    public enum AssetType   { CRYPTO, STOCK, FOREX, COMMODITY, INDEX }
    public enum TradeDirection { LONG, SHORT }
    public enum TradeStatus  { OPEN, CLOSED, CANCELLED }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Trade other)) return false;
        if (id == null || other.id == null) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return id != null ? Objects.hash(id) : System.identityHashCode(this);
    }
}
