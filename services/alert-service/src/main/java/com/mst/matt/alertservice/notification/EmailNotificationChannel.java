package com.mst.matt.alertservice.notification;

import com.mst.matt.contracts.dto.AlertTriggeredEventDto;
import com.mst.matt.contracts.dto.UserPreferencesDto;
import com.mst.matt.contracts.notification.NotificationChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Email notification channel stub in {@code alert-service}.
 *
 * <p><b>Step 8 — option (a): production email delivery has permanently moved to
 * {@code notification-service}.</b></p>
 *
 * <p>{@code alert-service} publishes {@code AlertTriggeredEventDto} to the
 * {@code alerts.triggered} Kafka topic via {@code AlertEventPublisher}.
 * {@code notification-service} consumes that topic and performs the real SMTP
 * send via its own {@code EmailNotificationChannel} + {@code EmailDispatchService}.</p>
 *
 * <p>This bean is kept as a <em>no-op logging stub</em> so the alert pipeline
 * continues to compile and run with all three {@link NotificationChannel} beans
 * present. It does NOT send any email.</p>
 *
 * <p>Choice rationale (per migration guide Step 8):
 * Option (a) chosen — alert-service publishes Kafka events only; all
 * email/Telegram fan-out is the exclusive responsibility of notification-service.
 * This eliminates the duplicate, half-implemented sending logic and matches the
 * production architecture.</p>
 */
@Slf4j
@Component
public class EmailNotificationChannel implements NotificationChannel {

    public static final String CHANNEL_NAME = "EMAIL";

    @Override
    public void send(AlertTriggeredEventDto event, UserPreferencesDto prefs) {
        // NO-OP: email delivery has moved to notification-service (Step 8, option a).
        // alert-service only publishes to alerts.triggered Kafka topic; notification-service
        // consumes that topic and sends the actual email via EmailDispatchService.
        log.debug("[EmailChannel-stub] alert-service no longer sends email directly " +
                "(alertId={} userId={}) — notification-service handles delivery via Kafka",
                event.getAlertId(), event.getUserId());
    }

    @Override
    public String channelName() {
        return CHANNEL_NAME;
    }
}
