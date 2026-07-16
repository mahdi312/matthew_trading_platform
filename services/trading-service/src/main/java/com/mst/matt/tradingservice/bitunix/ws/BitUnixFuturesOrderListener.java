package com.mst.matt.tradingservice.bitunix.ws;

import com.mst.matt.contracts.enums.BrokerType;
import com.mst.matt.tradingservice.bitunix.ws.dto.FuturesWsOrderData;
import com.mst.matt.tradingservice.bitunix.ws.dto.OrderStatusUpdated;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Consumes raw BitUnix Futures WebSocket order-channel push events and
 * re-publishes them as normalised {@link OrderStatusUpdated} events to the
 * persistence layer (Step 6 — Futures WS push-channel model).
 *
 * <h3>Event flow</h3>
 * <pre>
 *   BitUnix WS server
 *     ↓  JSON push ({"ch":"order", "data":{...}})
 *   {@link BitUnixFuturesWsClient}
 *     ↓  {@link BitUnixFuturesWsClient.RawFuturesOrderPush} (Spring application event)
 *   {@link BitUnixFuturesOrderListener#onOrderPush}
 *     ↓  maps → {@link OrderStatusUpdated}
 *   persistence layer / @EventListener (future step)
 * </pre>
 *
 * <h3>Why a separate listener class?</h3>
 * <p>Keeping the listener separate from {@link BitUnixFuturesWsClient} follows
 * the single-responsibility principle: the WS client owns the connection
 * lifecycle; the listener owns the business-logic mapping.  This also makes
 * unit testing the mapping logic trivial — no OkHttp mocking needed.</p>
 *
 * <h3>userId resolution</h3>
 * <p>The BitUnix WS push does NOT include a userId — the WS session is
 * authenticated once per connection for a single API key.  The listener
 * currently sets {@code userId = null}.  The full implementation will
 * maintain a session registry ({@code Map<wsSessionId, userId>}) in
 * {@link BitUnixFuturesWsClient} to resolve the user at push time.</p>
 *
 * <h3>Order-status mapping</h3>
 * <pre>
 *   BitUnix WS orderStatus → OrderStatusUpdated.newStatus
 *   "OPEN"             → "OPEN"
 *   "PARTIALLY_FILLED" → "PARTIALLY_FILLED"
 *   "FILLED"           → "FILLED"
 *   "CANCELLED"        → "CANCELLED"
 *   "REJECTED"         → "REJECTED"
 *   (anything else)    → echoed as-is
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BitUnixFuturesOrderListener {

    private final ApplicationEventPublisher eventPublisher;

    /**
     * Handles a raw Futures WS order-channel push.
     *
     * <p>Maps the broker wire DTO ({@link FuturesWsOrderData}) to the
     * platform's normalised {@link OrderStatusUpdated} event and re-publishes
     * it on the Spring application event bus for downstream consumers
     * (persistence layer, notification service stub, etc.).</p>
     *
     * @param push raw push event emitted by {@link BitUnixFuturesWsClient}
     */
    @EventListener
    public void onOrderPush(BitUnixFuturesWsClient.RawFuturesOrderPush push) {
        FuturesWsOrderData data = push.data();

        log.info("Processing Futures WS order push: orderId={} event={} status={} symbol={}",
                data.getOrderId(), data.getEvent(), data.getOrderStatus(), data.getSymbol());

        OrderStatusUpdated event = OrderStatusUpdated.builder()
                .orderId(data.getOrderId())
                .clientId(data.getClientId())
                .brokerType(BrokerType.BITUNIX)
                .userId(null)                              // stub: userId resolved from session registry in full impl
                .symbol(data.getSymbol())
                .newStatus(normaliseStatus(data.getOrderStatus()))
                .averageFillPrice(parseBD(data.getAveragePrice()))
                .filledQuantity(parseBD(data.getDealAmount()))
                .fee(parseBD(data.getFee()))
                .eventTime(Instant.now())
                .serverTime(push.serverTs() > 0
                        ? Instant.ofEpochMilli(push.serverTs())
                        : Instant.now())
                .build();

        log.debug("Publishing OrderStatusUpdated: orderId={} newStatus={}",
                event.getOrderId(), event.getNewStatus());
        eventPublisher.publishEvent(event);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Normalises BitUnix's WS order status to the platform's canonical status
     * strings.  Unknown statuses are echoed as-is to avoid silent data loss.
     */
    private String normaliseStatus(String rawStatus) {
        if (rawStatus == null) return "UNKNOWN";
        return switch (rawStatus.toUpperCase()) {
            case "NEW"              -> "OPEN";
            case "OPEN"             -> "OPEN";
            case "PARTIALLY_FILLED" -> "PARTIALLY_FILLED";
            case "FILLED"           -> "FILLED";
            case "CANCELLED",
                 "CANCELED"         -> "CANCELLED";
            case "REJECTED"         -> "REJECTED";
            case "EXPIRED"          -> "EXPIRED";
            default -> {
                log.warn("Unrecognised BitUnix order status '{}' — echoing as-is", rawStatus);
                yield rawStatus;
            }
        };
    }

    private BigDecimal parseBD(String value) {
        if (value == null || value.isBlank() || "0".equals(value)) return BigDecimal.ZERO;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            log.warn("Could not parse BigDecimal from '{}': {}", value, e.getMessage());
            return BigDecimal.ZERO;
        }
    }
}
