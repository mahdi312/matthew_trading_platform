package com.mst.matt.tradingservice.bitunix.ws.dto;

import com.mst.matt.contracts.enums.BrokerType;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Internal application event published whenever a BitUnix WebSocket push
 * carries a definitive order-state change (Step 6 — Futures WS listener).
 *
 * <h3>Source</h3>
 * <ul>
 *   <li><b>Futures</b>: emitted by
 *       {@link com.mst.matt.tradingservice.bitunix.ws.BitUnixFuturesOrderListener}
 *       on receipt of {@code order} channel pushes from
 *       {@link com.mst.matt.tradingservice.bitunix.ws.BitUnixFuturesWsClient}.</li>
 *   <li><b>Spot</b>: emitted by the Spot WS RPC reply handler in
 *       {@link com.mst.matt.tradingservice.bitunix.ws.BitUnixSpotWsClient}
 *       when an {@code order.place_order} or {@code order.cancel} reply arrives.</li>
 * </ul>
 *
 * <h3>Consumer</h3>
 * <p>The persistence layer (a future {@code OrderRepository} / service bean)
 * listens for this event via Spring's {@code @EventListener} / application
 * event bus, or via an in-process queue/disruptor, and updates the local order
 * record.  No external Kafka publish happens here — that is Step 7
 * (Notification Service Kafka Fan-Out).</p>
 *
 * <h3>Immutability</h3>
 * <p>Lombok {@code @Value} ensures all fields are final; {@code @Builder}
 * provides a convenient construction path from the WS listener.</p>
 */
@Value
@Builder
public class OrderStatusUpdated {

    // ── Order identity ────────────────────────────────────────────────────────

    /** Broker-assigned order identifier. */
    String orderId;

    /**
     * Client-assigned order identifier (as sent in the place-order request).
     * May be {@code null} if not provided originally.
     */
    String clientId;

    /** Broker that generated this event. */
    BrokerType brokerType;

    /** Platform user id that owns the order. */
    Long userId;

    // ── Order details ─────────────────────────────────────────────────────────

    /** Trading symbol (e.g., {@code "BTCUSDT"}). */
    String symbol;

    /**
     * New order status as reported by the broker's WS channel.
     * Typical values: {@code "OPEN"}, {@code "PARTIALLY_FILLED"},
     * {@code "FILLED"}, {@code "CANCELLED"}, {@code "REJECTED"}.
     */
    String newStatus;

    /**
     * Volume-weighted average fill price; {@code null} if not yet filled.
     */
    BigDecimal averageFillPrice;

    /**
     * Cumulative filled quantity (base asset units); {@code null} if unfilled.
     */
    BigDecimal filledQuantity;

    /**
     * Total fee charged so far (quote asset units).
     */
    BigDecimal fee;

    // ── Provenance ────────────────────────────────────────────────────────────

    /**
     * When this event was generated (i.e., when the WS message was received),
     * in UTC.
     */
    Instant eventTime;

    /**
     * Server-side timestamp from the WS push, in UTC.
     * May differ from {@link #eventTime} by network latency.
     */
    Instant serverTime;
}
