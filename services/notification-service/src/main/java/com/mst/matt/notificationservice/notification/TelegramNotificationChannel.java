package com.mst.matt.notificationservice.notification;

import com.mst.matt.contracts.dto.AlertTriggeredEventDto;
import com.mst.matt.contracts.dto.UserPreferencesDto;
import com.mst.matt.contracts.notification.NotificationChannel;
import com.mst.matt.notificationservice.service.TradingTelegramBot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * <b>Production</b> Telegram notification channel for {@code notification-service}.
 *
 * <p>This is the real Bot API implementation of {@link NotificationChannel}
 * — the production counterpart of the stub previously in {@code alert-service}.
 * The stub has been replaced with a Kafka-only publish (option (a), per Step 8
 * of the migration guide). {@code alert-service} no longer sends Telegram messages
 * directly — it only publishes to {@code alerts.triggered}.</p>
 *
 * <h3>Flow</h3>
 * <ol>
 *   <li>Kafka consumer ({@code AlertTriggeredEventConsumer}) receives an event.</li>
 *   <li>User preferences are looked up; if Telegram is enabled and the chatId is set,
 *       this method is called.</li>
 *   <li>The message is sent to all configured/subscribed chat IDs via
 *       {@link TradingTelegramBot#sendAlertMessage(String)}.</li>
 * </ol>
 *
 * <p>The bean is registered unconditionally; it delegates to {@link TradingTelegramBot}
 * which is conditional on {@code telegram.bot.enabled=true} — so this channel
 * compiles and registers without a bot token; it just logs a warning if the bot
 * bean is absent.</p>
 */
@Slf4j
@Component
public class TelegramNotificationChannel implements NotificationChannel {

    public static final String CHANNEL_NAME = "TELEGRAM";

    @Autowired(required = false)
    private TradingTelegramBot telegramBot;

    @Async("notificationSendExecutor")
    @Override
    public void send(AlertTriggeredEventDto event, UserPreferencesDto prefs) {
        if (prefs == null || !prefs.isTelegramEnabled()
                || prefs.getTelegramChatId() == null || prefs.getTelegramChatId().isBlank()) {
            log.debug("[TelegramChannel] not enabled/configured for userId={}, skipping alertId={}",
                    event.getUserId(), event.getAlertId());
            return;
        }

        if (telegramBot == null) {
            log.warn("[TelegramChannel] TradingTelegramBot not available " +
                    "(telegram.bot.enabled=false or token not set) — skipping alertId={}",
                    event.getAlertId());
            return;
        }

        String message = buildMessage(event);
        telegramBot.sendAlertMessage(message);
    }

    @Override
    public String channelName() {
        return CHANNEL_NAME;
    }

    // ── Message builder ───────────────────────────────────────────────────────

    private static String buildMessage(AlertTriggeredEventDto event) {
        StringBuilder sb = new StringBuilder();
        sb.append("🔔 *Alert Triggered*\n\n");
        sb.append("*Symbol:* ").append(event.getSymbol()).append("\n");
        sb.append("*Condition:* ").append(event.getCondition())
          .append(" ").append(event.getTargetValue()).append("\n");
        sb.append("*Observed:* ").append(event.getObservedValue()).append("\n");
        if (event.getMessage() != null && !event.getMessage().isBlank()) {
            sb.append("*Note:* ").append(event.getMessage()).append("\n");
        }
        sb.append("_").append(event.getTriggeredAt()).append("_");
        return sb.toString();
    }
}
