package com.mst.matt.alertservice.model;

import com.mst.matt.contracts.enums.AlertCondition;
import com.mst.matt.contracts.enums.AlertStatus;
import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.enums.BrokerType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A user-configured price alert, evaluated against live price ticks by
 * {@code AlertEvaluationService}.
 *
 * <p>Owned exclusively by {@code alert-service} — no other service persists
 * or queries this table directly. Cross-service communication about a fired
 * alert happens exclusively through {@code AlertTriggeredEventDto}
 * (shared/contracts), never by sharing this entity.</p>
 *
 * <h3>Re-firing behaviour</h3>
 * <p>Controlled by {@link #repeating} + {@link #cooldownSeconds}:</p>
 * <ul>
 *   <li>{@code repeating = false} — one-shot; once triggered, the alert is
 *       moved to {@link AlertStatus#TRIGGERED} and never evaluated again
 *       (unless the user re-activates it via {@code PUT /alerts/{id}}).</li>
 *   <li>{@code repeating = true} — after firing, the alert re-arms
 *       automatically once {@link #cooldownSeconds} have elapsed since
 *       {@link #lastTriggeredAt}, so a single sustained price move cannot
 *       spam notifications tick after tick.</li>
 * </ul>
 */
@Entity
@Table(name = "price_alerts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class PriceAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /** Platform user id that owns this alert (from the Gateway's X-User-Id header). */
    @Column(nullable = false)
    private Long userId;

    /** Canonical trading symbol (e.g., "BTCUSDT", "AAPL", "EURUSD"). */
    @Column(nullable = false, length = 40)
    private String symbol;

    /** Asset class of {@link #symbol}; used to route the price lookup to the right provider. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssetClass assetClass;

    /**
     * Optional broker this alert's price feed should prefer (e.g., BITUNIX
     * for a crypto alert). {@code null} lets the evaluation loop fall back
     * to the default/registered provider for {@link #assetClass}.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private BrokerType brokerType;

    /**
     * Optional explicit data-provider name (e.g., "ALPHA_VANTAGE",
     * "COINGECKO") when the user/system wants a specific
     * {@code OhlcvDataProvider}/{@code MarketDataProvider} rather than the
     * registry's default priority-chain pick for {@link #assetClass}.
     */
    @Column(length = 40)
    private String providerName;

    /** ABOVE / BELOW / PERCENT_CHANGE. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertCondition condition;

    /**
     * Target value the condition is evaluated against: an absolute price for
     * ABOVE/BELOW, or a percentage (e.g. {@code 5} = 5%) for PERCENT_CHANGE.
     */
    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal targetValue;

    /**
     * Reference price used to compute the percentage move for
     * {@link AlertCondition#PERCENT_CHANGE} alerts. {@code null} until the
     * evaluation loop's first successful price lookup, which records it
     * without firing (see {@code AlertEvaluationService}); reset to the
     * current price whenever a repeating PERCENT_CHANGE alert re-arms so
     * each cycle measures the move from that point forward. Unused for
     * ABOVE/BELOW alerts.
     */
    @Column(precision = 20, scale = 8)
    private BigDecimal baselineValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AlertStatus status = AlertStatus.ACTIVE;

    /** Optional user note included in the fired notification. */
    @Column(length = 500)
    private String message;

    /** Re-arm after firing once {@link #cooldownSeconds} elapse; see class javadoc. */
    @Column(nullable = false)
    @Builder.Default
    private boolean repeating = false;

    /** Cooldown window in seconds before a {@link #repeating} alert re-arms. */
    @Column(nullable = false)
    @Builder.Default
    private int cooldownSeconds = 900; // 15 minutes default

    /** Timestamp of the most recent trigger; {@code null} if never fired. */
    @Column
    private Instant lastTriggeredAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Returns {@code true} if this alert is currently eligible for
     * evaluation, i.e. {@link #status} is {@link AlertStatus#ACTIVE}, or it
     * is {@link AlertStatus#TRIGGERED} + {@link #repeating} and the cooldown
     * window has elapsed.
     */
    public boolean isEligibleForEvaluation() {
        if (status == AlertStatus.ACTIVE) return true;
        if (status == AlertStatus.TRIGGERED && repeating) {
            if (lastTriggeredAt == null) return true;
            return Instant.now().isAfter(lastTriggeredAt.plusSeconds(cooldownSeconds));
        }
        return false;
    }
}
