package com.mst.matt.alertservice.notification;

import com.mst.matt.contracts.dto.AlertTriggeredEventDto;
import com.mst.matt.contracts.dto.UserPreferencesDto;
import com.mst.matt.contracts.notification.NotificationChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Telegram notification channel stub in {@code alert-service}.
 *
 * <p><b>Step 8 — option (a): production Telegram delivery has permanently moved to
 * {@code notification-service}.</b></p>
 *
 * <p>{@code alert-service} publishes {@code AlertTriggeredEventDto} to the
 * {@code alerts.triggered} Kafka topic via {@code AlertEventPublisher}.
 * {@code notification-service} consumes that topic and performs the real Bot API
 * send via its own {@code TelegramNotificationChannel} + {@code TradingTelegramBot}.</p>
 *
 * <p>This bean is kept as a <em>no-op logging stub</em> so the alert pipeline
 * continues to compile and run with all three {@link NotificationChannel} beans
 * present. It does NOT send any Telegram message.</p>
 *
 * <p>Choice rationale (per migration guide Step 8):
 * Option (a) chosen — alert-service publishes Kafka events only; all
 * email/Telegram fan-out is the exclusive responsibility of notification-service.
 * This eliminates the duplicate, half-implemented sending logic and matches the
 * production architecture.</p>
 */
@Slf4j
@Component
public class TelegramNotificationChannel implements NotificationChannel {

    public static final String CHANNEL_NAME = "TELEGRAM";

    @Async("notificationSendExecutor")
    @Override
    public void send(AlertTriggeredEventDto event, UserPreferencesDto prefs) {
        // NO-OP: Telegram delivery has moved to notification-service (Step 8, option a).
        // alert-service only publishes to alerts.triggered Kafka topic; notification-service
        // consumes that topic and sends the actual Telegram message via TradingTelegramBot.
        log.debug("[TelegramChannel-stub] alert-service no longer sends Telegram directly " +
                "(alertId={} userId={}) — notification-service handles delivery via Kafka",
                event.getAlertId(), event.getUserId());
    }

    @Override
    public String channelName() {
        return CHANNEL_NAME;
    }
}
