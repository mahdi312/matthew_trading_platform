package com.mst.matt.notificationservice.kafka;

import com.mst.matt.contracts.dto.TradeEventDto;
import com.mst.matt.notificationservice.client.IdentityClient;
import com.mst.matt.notificationservice.service.EmailDispatchService;
import com.mst.matt.notificationservice.service.TradingTelegramBot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for {@code trades.executed} and {@code trades.closed} topics.
 *
 * <p>Published by {@code trading-service}'s {@code TradeEventPublisher}
 * (added in the same Step 8 pass — see {@code trading-service} changes).
 * Consumes both topics in a single listener using a container-level
 * {@code topics} list.</p>
 *
 * <h3>Notification behaviour</h3>
 * <ul>
 *   <li><b>trades.executed</b> — trade opened / filled: sends a brief
 *       "trade opened" notification.</li>
 *   <li><b>trades.closed</b>  — trade closed with P&L: includes P&L
 *       in the notification body.</li>
 * </ul>
 *
 * <p>Uses the same {@link EmailDispatchService} and {@link TradingTelegramBot}
 * as the alert consumer. Both are optional beans (conditional on SMTP host /
 * telegram.bot.enabled) so the service starts cleanly without credentials.</p>
 */
@Slf4j
@Component
public class TradeEventConsumer {

    public static final String TOPIC_EXECUTED = "trades.executed";
    public static final String TOPIC_CLOSED   = "trades.closed";
    public static final String GROUP_ID       = "notification-service-trades";

    private final IdentityClient identityClient;
    private final EmailDispatchService emailDispatchService;
    private final TradingTelegramBot telegramBot;

    public TradeEventConsumer(
            IdentityClient identityClient,
            @Autowired(required = false) EmailDispatchService emailDispatchService,
            @Autowired(required = false) TradingTelegramBot telegramBot) {
        this.identityClient = identityClient;
        this.emailDispatchService = emailDispatchService;
        this.telegramBot = telegramBot;
    }

    @KafkaListener(
            topics  = {TOPIC_EXECUTED, TOPIC_CLOSED},
            groupId = GROUP_ID,
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onTradeEvent(
            TradeEventDto event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("[TradeConsumer] received from {}[{}@{}]: eventId={} userId={} symbol={} status={}",
                topic, partition, offset,
                event.getEventId(), event.getUserId(), event.getSymbol(), event.getStatus());

        com.mst.matt.contracts.dto.UserPreferencesDto prefs = identityClient.getPreferences(event.getUserId());

        boolean isClosed = TOPIC_CLOSED.equals(topic);
        String subject = buildSubject(event, isClosed);
        String body    = buildBody(event, isClosed);

        if (prefs.isEmailEnabled() && prefs.getEmail() != null && !prefs.getEmail().isBlank()
                && emailDispatchService != null && emailDispatchService.isConfigured()) {
            emailDispatchService.send(subject, body);
        }

        if (prefs.isTelegramEnabled() && prefs.getTelegramChatId() != null && !prefs.getTelegramChatId().isBlank()
                && telegramBot != null) {
            telegramBot.sendAlertMessage(buildTelegramMessage(event, isClosed));
        }
    }

    // ── Message builders ──────────────────────────────────────────────────────

    private static String buildSubject(TradeEventDto event, boolean isClosed) {
        return isClosed
                ? "Trade closed: %s — P&L calculated".formatted(event.getSymbol())
                : "Trade executed: %s %s".formatted(event.getSide(), event.getSymbol());
    }

    private static String buildBody(TradeEventDto event, boolean isClosed) {
        StringBuilder sb = new StringBuilder();
        sb.append("Trade ").append(isClosed ? "Closed" : "Executed").append("\n");
        sb.append("─────────────────────\n");
        sb.append("Symbol:   ").append(event.getSymbol()).append("\n");
        sb.append("Side:     ").append(event.getSide()).append("\n");
        sb.append("Quantity: ").append(event.getQuantity()).append("\n");
        if (event.getFillPrice() != null) {
            sb.append("Fill Price: ").append(event.getFillPrice()).append("\n");
        }
        if (isClosed && event.getFee() != null) {
            sb.append("Fee: ").append(event.getFee()).append("\n");
        }
        sb.append("Status:   ").append(event.getStatus()).append("\n");
        sb.append("Time:     ").append(event.getUpdatedAt()).append("\n");
        return sb.toString();
    }

    private static String buildTelegramMessage(TradeEventDto event, boolean isClosed) {
        StringBuilder sb = new StringBuilder();
        sb.append(isClosed ? "📊 *Trade Closed*\n\n" : "⚡ *Trade Executed*\n\n");
        sb.append("*Symbol:* ").append(event.getSymbol()).append("\n");
        sb.append("*Side:* ").append(event.getSide()).append("\n");
        sb.append("*Qty:* ").append(event.getQuantity()).append("\n");
        if (event.getFillPrice() != null) {
            sb.append("*Fill:* ").append(event.getFillPrice()).append("\n");
        }
        sb.append("*Status:* ").append(event.getStatus()).append("\n");
        sb.append("_").append(event.getUpdatedAt()).append("_");
        return sb.toString();
    }
}
