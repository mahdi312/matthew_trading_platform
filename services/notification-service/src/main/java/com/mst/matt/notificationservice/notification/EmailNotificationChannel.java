package com.mst.matt.notificationservice.notification;

import com.mst.matt.contracts.dto.AlertTriggeredEventDto;
import com.mst.matt.contracts.dto.UserPreferencesDto;
import com.mst.matt.contracts.notification.NotificationChannel;
import com.mst.matt.notificationservice.service.EmailDispatchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * <b>Production</b> email notification channel for {@code notification-service}.
 *
 * <p>This is the real SMTP-sending implementation of
 * {@link NotificationChannel} — the production counterpart of the stub that
 * previously lived in {@code alert-service}. The stub in {@code alert-service}
 * has been replaced with a Kafka-only publish (option (a), per the migration
 * guide Step 8) — {@code alert-service} no longer sends email directly.</p>
 *
 * <h3>Flow</h3>
 * <ol>
 *   <li>Kafka consumer ({@code AlertTriggeredEventConsumer}) receives an
 *       {@code AlertTriggeredEventDto} from topic {@code alerts.triggered}.</li>
 *   <li>It looks up the user's preferences and calls
 *       {@link #send(AlertTriggeredEventDto, UserPreferencesDto)}.</li>
 *   <li>This method checks the user's email preference and delegates to
 *       {@link EmailDispatchService} for the actual SMTP call.</li>
 * </ol>
 *
 * <p>The bean is registered unconditionally; it delegates to
 * {@link EmailDispatchService} which is conditional on
 * {@code spring.mail.host} — so the pipeline works end-to-end
 * even without SMTP configured (log warnings only).</p>
 */
@Slf4j
@Component
public class EmailNotificationChannel implements NotificationChannel {

    public static final String CHANNEL_NAME = "EMAIL";

    @Autowired(required = false)
    private EmailDispatchService emailDispatchService;

    @Async("notificationSendExecutor")
    @Override
    public void send(AlertTriggeredEventDto event, UserPreferencesDto prefs) {
        if (prefs == null || !prefs.isEmailEnabled()
                || prefs.getEmail() == null || prefs.getEmail().isBlank()) {
            log.debug("[EmailChannel] not enabled/configured for userId={}, skipping alertId={}",
                    event.getUserId(), event.getAlertId());
            return;
        }

        if (emailDispatchService == null) {
            log.warn("[EmailChannel] EmailDispatchService not available (spring.mail.host not set)" +
                    " — skipping email for alertId={}", event.getAlertId());
            return;
        }

        String subject = buildSubject(event);
        String body    = buildBody(event);
        emailDispatchService.send(subject, body);
    }

    @Override
    public String channelName() {
        return CHANNEL_NAME;
    }

    // ── Message builders ──────────────────────────────────────────────────────

    private static String buildSubject(AlertTriggeredEventDto event) {
        return "Alert triggered: %s %s %s".formatted(
                event.getSymbol(),
                event.getCondition(),
                event.getTargetValue());
    }

    private static String buildBody(AlertTriggeredEventDto event) {
        return """
                Alert Details
                ─────────────
                Symbol:       %s
                Condition:    %s %s
                Observed:     %s
                Alert ID:     %s
                Triggered at: %s
                %s
                """.formatted(
                event.getSymbol(),
                event.getCondition(),
                event.getTargetValue(),
                event.getObservedValue(),
                event.getAlertId(),
                event.getTriggeredAt(),
                event.getMessage() != null && !event.getMessage().isBlank()
                        ? "\nNote: " + event.getMessage() : "");
    }
}
