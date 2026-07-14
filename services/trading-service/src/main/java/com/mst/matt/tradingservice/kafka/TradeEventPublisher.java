package com.mst.matt.tradingservice.kafka;

import com.mst.matt.contracts.dto.TradeEventDto;
import com.mst.matt.contracts.enums.BrokerType;
import com.mst.matt.contracts.enums.InstrumentType;
import com.mst.matt.contracts.enums.OrderSide;
import com.mst.matt.contracts.enums.OrderType;
import com.mst.matt.tradingservice.model.Trade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Publishes {@link TradeEventDto} events to Kafka topics on trade lifecycle changes.
 *
 * <h3>Topics</h3>
 * <ul>
 *   <li>{@value #TOPIC_EXECUTED} — trade opened / order filled (status transitions to OPEN).</li>
 *   <li>{@value #TOPIC_CLOSED}   — trade closed with realised P&L (status transitions to CLOSED).</li>
 * </ul>
 *
 * <p>Consumed by {@code notification-service}'s {@code TradeEventConsumer} to
 * fan out email and Telegram trade-event notifications (Step 8 of the
 * migration guide).</p>
 *
 * <h3>Key strategy</h3>
 * <p>Events are keyed by {@code userId} (as String) so all of a user's trade
 * events land on the same partition, preserving per-user ordering — same
 * strategy as {@code AlertEventPublisher} in {@code alert-service}.</p>
 *
 * <h3>Resilience</h3>
 * <p>Publishing failures are caught and logged; they must not block the
 * trade-persistence transaction that already completed before this call.
 * A failed publish is a notification-delivery miss, not a trade-data loss.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradeEventPublisher {

    public static final String TOPIC_EXECUTED = "trades.executed";
    public static final String TOPIC_CLOSED   = "trades.closed";

    private final KafkaTemplate<String, TradeEventDto> kafkaTemplate;

    /**
     * Publishes a "trade executed/opened" event.
     * Call after {@code TradeService.saveTrade()} when a new trade is persisted.
     *
     * @param trade the newly persisted trade
     */
    public void publishExecuted(Trade trade) {
        publish(TOPIC_EXECUTED, toDto(trade, "OPEN"));
    }

    /**
     * Publishes a "trade closed" event.
     * Call after {@code TradeService.closeTrade()} once the trade is persisted as CLOSED.
     *
     * @param trade the now-closed trade (with exitPrice and pnlAmount set)
     */
    public void publishClosed(Trade trade) {
        publish(TOPIC_CLOSED, toDto(trade, "CLOSED"));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void publish(String topic, TradeEventDto event) {
        try {
            kafkaTemplate.send(topic, String.valueOf(event.getUserId()), event);
            log.info("Published TradeEventDto to '{}': eventId={} userId={} symbol={} status={}",
                    topic, event.getEventId(), event.getUserId(),
                    event.getSymbol(), event.getStatus());
        } catch (Exception ex) {
            // Publishing failure must not surface to the caller — the trade is already
            // persisted; a missed Kafka publish is a notification miss, not data loss.
            log.error("Failed to publish TradeEventDto to '{}' (symbol={} userId={}): {}",
                    topic, event.getSymbol(), event.getUserId(), ex.getMessage(), ex);
        }
    }

    private static TradeEventDto toDto(Trade trade, String status) {
        return TradeEventDto.builder()
                .eventId(UUID.randomUUID().toString())
                .userId(trade.getUserId())
                .brokerType(BrokerType.BITUNIX)
                .symbol(trade.getSymbol())
                .instrumentType(resolveInstrumentType(trade))
                .side(resolveSide(trade))
                .orderType(OrderType.MARKET)
                .quantity(trade.getQuantity())
                .requestedPrice(trade.getEntryPrice())
                .fillPrice(trade.getEntryPrice())
                .fee(trade.getFee())
                .status(status)
                .submittedAt(trade.getEntryTime() != null
                        ? trade.getEntryTime().atZone(java.time.ZoneId.systemDefault()).toInstant()
                        : Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private static InstrumentType resolveInstrumentType(Trade trade) {
        if (trade.getAssetType() == null) return InstrumentType.CRYPTO_SPOT;
        // Trade.AssetType: CRYPTO, STOCK, FOREX, COMMODITY, INDEX
        // Map to contracts InstrumentType
        return switch (trade.getAssetType()) {
            case STOCK     -> InstrumentType.EQUITY;
            case FOREX     -> InstrumentType.FOREX;
            case COMMODITY -> InstrumentType.COMMODITY;
            case INDEX     -> InstrumentType.ETF_INDEX;
            default        -> InstrumentType.CRYPTO_SPOT;
        };
    }

    private static OrderSide resolveSide(Trade trade) {
        // TradeDirection enum: LONG, SHORT
        if (trade.getDirection() == null) return OrderSide.BUY;
        return switch (trade.getDirection()) {
            case SHORT -> OrderSide.SELL;
            default    -> OrderSide.BUY;
        };
    }
}
