package com.mst.matt.alertservice.notification;

import com.mst.matt.contracts.dto.AlertTriggeredEventDto;
import com.mst.matt.contracts.dto.UserPreferencesDto;
import com.mst.matt.contracts.notification.NotificationChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * <b>STUB</b> — no SMTP call is made yet.
 *
 * <p>Registered now so the alert pipeline compiles and runs end-to-end with
 * all three {@link NotificationChannel} beans present, per Step 4.75. Real
 * SMTP wiring (JavaMailSender, HTML templates, etc. — see the JavaFX
 * monolith's {@code NotificationService.sendEmail} for the reference
 * implementation to port) is added in {@code notification-service} in
 * Step 7, consuming the same {@code alerts.triggered} Kafka topic that
 * {@code AlertEvaluationService} publishes to.</p>
 *
 * <p>This bean stays registered in {@code alert-service} after Step 7 lands
 * only if in-process delivery is still desired; the design intent is that
 * email/Telegram fan-out permanently moves to {@code notification-service}
 * and this stub is removed at that point — see class-level note in
 * {@link NotificationChannel}.</p>
 */
@Slf4j
@Component
public class EmailNotificationChannel implements NotificationChannel {

    public static final String CHANNEL_NAME = "EMAIL";

    @Override
    public void send(AlertTriggeredEventDto event, UserPreferencesDto prefs) {
        if (prefs == null || !prefs.isEmailEnabled() || prefs.getEmail() == null
                || prefs.getEmail().isBlank()) {
            log.debug("Email channel not configured/enabled for userId={}, skipping alertId={}",
                    event.getUserId(), event.getAlertId());
            return;
        }
        // NOT IMPLEMENTED — real SMTP send lands in notification-service (Step 7).
        log.info("[STUB] Would send EMAIL for alertId={} symbol={} to={} "
                        + "(SMTP wiring not yet implemented — see Step 7)",
                event.getAlertId(), event.getSymbol(), prefs.getEmail());
    }

    @Override
    public String channelName() {
        return CHANNEL_NAME;
    }
}
