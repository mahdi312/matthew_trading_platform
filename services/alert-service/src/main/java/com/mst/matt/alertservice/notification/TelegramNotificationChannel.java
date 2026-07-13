package com.mst.matt.alertservice.notification;

import com.mst.matt.contracts.dto.AlertTriggeredEventDto;
import com.mst.matt.contracts.dto.UserPreferencesDto;
import com.mst.matt.contracts.notification.NotificationChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * <b>STUB</b> — no Telegram Bot API call is made yet.
 *
 * <p>Registered now so the alert pipeline compiles and runs end-to-end with
 * all three {@link NotificationChannel} beans present, per Step 4.75. Real
 * Bot API wiring (see the JavaFX monolith's {@code TradingTelegramBot} for
 * the reference implementation to port — rubenlagus TelegramBots library)
 * is added in {@code notification-service} in Step 7, consuming the same
 * {@code alerts.triggered} Kafka topic that {@code AlertEvaluationService}
 * publishes to.</p>
 */
@Slf4j
@Component
public class TelegramNotificationChannel implements NotificationChannel {

    public static final String CHANNEL_NAME = "TELEGRAM";

    @Override
    public void send(AlertTriggeredEventDto event, UserPreferencesDto prefs) {
        if (prefs == null || !prefs.isTelegramEnabled() || prefs.getTelegramChatId() == null
                || prefs.getTelegramChatId().isBlank()) {
            log.debug("Telegram channel not configured/enabled for userId={}, skipping alertId={}",
                    event.getUserId(), event.getAlertId());
            return;
        }
        // NOT IMPLEMENTED — real Bot API send lands in notification-service (Step 7).
        log.info("[STUB] Would send TELEGRAM for alertId={} symbol={} to chatId={} "
                        + "(Bot API wiring not yet implemented — see Step 7)",
                event.getAlertId(), event.getSymbol(), prefs.getTelegramChatId());
    }

    @Override
    public String channelName() {
        return CHANNEL_NAME;
    }
}
