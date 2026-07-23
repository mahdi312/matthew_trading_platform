package com.mst.matt.contracts.dto;

import com.mst.matt.contracts.enums.AlertCondition;
import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.enums.BrokerType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Cross-service event published by {@code alert-service} when a
 * {@code PriceAlert} condition is met.
 *
 * <h3>Publication</h3>
 * <p>Published to the Kafka topic {@code alerts.triggered} (see
 * {@code AlertEvaluationService} in {@code alert-service}) and consumed by:</p>
 * <ul>
 *   <li>{@code alert-service} itself — its {@code InAppNotificationChannel}
 *       relays this event over WebSocket/STOMP to the owning user's browser
 *       session immediately (no Kafka round-trip needed for that channel).</li>
 *   <li>{@code notification-service} (Step 7) — consumes the same topic to
 *       fan out email and Telegram notifications.</li>
 * </ul>
 *
 * <p>This DTO contains no JPA annotations — persistence of the underlying
 * {@code PriceAlert} entity is owned exclusively by {@code alert-service}.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertTriggeredEventDto {

    /** Unique identifier for this triggering occurrence (UUID). */
    private String eventId;

    /** The {@code PriceAlert.id} that fired. */
    private Long alertId;

    /** Platform user id that owns the alert. */
    private Long userId;

    /** Symbol the alert was watching (e.g., "BTCUSDT", "AAPL"). */
    private String symbol;

    /** Asset class of the watched symbol. */
    private AssetClass assetClass;

    /**
     * Broker or data-provider name the alert's price feed came from
     * (e.g., "BITUNIX", "ALPHA_VANTAGE"); informational only.
     */
    private BrokerType brokerType;

    /** The condition that was evaluated (ABOVE / BELOW / PERCENT_CHANGE). */
    private AlertCondition condition;

    /** The target value configured on the alert (price or percentage). */
    private BigDecimal targetValue;

    /** The actual live value that satisfied the condition at trigger time. */
    private BigDecimal observedValue;

    /** Optional user-supplied label/message to include in notifications. */
    private String message;

    /** When the condition was satisfied and this event was created. */
    private Instant triggeredAt;
}
