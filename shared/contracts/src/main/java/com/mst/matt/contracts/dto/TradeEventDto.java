package com.mst.matt.contracts.dto;

import com.mst.matt.contracts.enums.BrokerType;
import com.mst.matt.contracts.enums.InstrumentType;
import com.mst.matt.contracts.enums.OrderSide;
import com.mst.matt.contracts.enums.OrderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immutable cross-service DTO representing a completed or in-flight trade event.
 *
 * <p>Published by {@code trading-service} and consumed by services such as
 * {@code notification-service} and any future analytics service.
 * This class contains NO JPA annotations — persistence is the responsibility
 * of the service that stores the event.</p>
 *
 * <p>Fields are nullable where the data may not be known at event-creation time
 * (e.g., {@code fillPrice} for a pending order).</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradeEventDto {

    // ── Identity ─────────────────────────────────────────────────────────────

    /**
     * Unique identifier of this event (UUID or broker-assigned order id).
     */
    private String eventId;

    /**
     * Platform user id that owns this trade.
     */
    private Long userId;

    /**
     * Broker through which the trade was/will be executed.
     */
    private BrokerType brokerType;

    // ── Instrument ───────────────────────────────────────────────────────────

    /** Trading symbol as understood by the broker (e.g., "BTCUSDT"). */
    private String symbol;

    /** Broad classification of the traded instrument. */
    private InstrumentType instrumentType;

    // ── Order details ────────────────────────────────────────────────────────

    /** Buy or sell direction. */
    private OrderSide side;

    /** Execution type requested. */
    private OrderType orderType;

    /** Requested quantity in base-asset units. */
    private BigDecimal quantity;

    /**
     * Limit or stop price specified by the user; {@code null} for market orders.
     */
    private BigDecimal requestedPrice;

    /**
     * Actual fill price reported by the broker; {@code null} until the order
     * is filled.
     */
    private BigDecimal fillPrice;

    /**
     * Total fee charged by the broker in quote-asset units; {@code null} until
     * the order is filled.
     */
    private BigDecimal fee;

    // ── Status & timing ──────────────────────────────────────────────────────

    /**
     * Current lifecycle status of the order
     * (e.g., "PENDING", "OPEN", "FILLED", "CANCELLED", "REJECTED").
     */
    private String status;

    /** When the order was submitted to the broker. */
    private Instant submittedAt;

    /** When the order was last updated (filled, cancelled, etc.). */
    private Instant updatedAt;
}
