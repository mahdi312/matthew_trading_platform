package com.mst.matt.notificationservice.kafka;

import com.mst.matt.contracts.dto.AlertTriggeredEventDto;
import com.mst.matt.contracts.dto.UserPreferencesDto;
import com.mst.matt.contracts.notification.NotificationChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Kafka consumer for the {@code alerts.triggered} topic.
 *
 * <p>Published by {@code alert-service}'s {@code AlertEventPublisher} when a
 * {@code PriceAlert} condition is met. This consumer fans the event out to all
 * registered {@link NotificationChannel} implementations in this service
 * ({@code EmailNotificationChannel} and {@code TelegramNotificationChannel}).</p>
 *
 * <h3>User preferences</h3>
 * <p>The {@code AlertTriggeredEventDto} does not carry user preferences inline.
 * In a full production wiring, preferences would be fetched from
 * {@code identity-service} (Step 9). For now, {@link #resolvePreferences} builds
 * a permissive stub that enables all channels — real preference lookup is added
 * in Step 9 when {@code identity-service} grows its profile/settings API.</p>
 *
 * <h3>Resilience</h3>
 * <p>Each channel's {@code send()} implementation catches its own exceptions
 * (contract requirement from {@link NotificationChannel}'s Javadoc). The
 * consumer itself is not retried on channel errors — Kafka offset is always
 * committed. A channel failure is a delivery concern, not a topic-processing
 * failure. Add a dead-letter topic (Step 13+) if guaranteed delivery is needed.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertTriggeredEventConsumer {

    public static final String TOPIC    = "alerts.triggered";
    public static final String GROUP_ID = "notification-service";

    private final List<NotificationChannel> channels;

    @KafkaListener(
            topics  = TOPIC,
            groupId = GROUP_ID,
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onAlertTriggered(
            AlertTriggeredEventDto event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("[AlertConsumer] received from {}[{}@{}]: alertId={} userId={} symbol={}",
                TOPIC, partition, offset,
                event.getAlertId(), event.getUserId(), event.getSymbol());

        UserPreferencesDto prefs = resolvePreferences(event.getUserId());

        for (NotificationChannel channel : channels) {
            try {
                channel.send(event, prefs);
            } catch (Exception ex) {
                // Should not reach here — channels must catch internally.
                // Belt-and-suspenders guard so one broken channel never blocks the rest.
                log.error("[AlertConsumer] channel {} threw uncaught exception for alertId={}: {}",
                        channel.channelName(), event.getAlertId(), ex.getMessage(), ex);
            }
        }
    }

    /**
     * Resolves user notification preferences for the given user ID.
     *
     * <p><b>TODO (Step 9)</b>: Replace this stub with a Feign call to
     * {@code identity-service}'s {@code GET /api/profile/{userId}/preferences}
     * once that endpoint is built. Until then, all channels are enabled with
     * the destination from the event's own {@code userId} field (Telegram chat ID
     * and email must be set in the bot config / SMTP config instead of
     * per-user preferences).</p>
     *
     * @param userId the platform user ID
     * @return a preferences snapshot enabling all channels by default
     */
    private UserPreferencesDto resolvePreferences(Long userId) {
        // Stub: enable both channels; actual email/chatId routing is handled
        // inside each channel's send() based on bot config / SMTP recipient.
        // Replace with identity-service Feign call in Step 9.
        return UserPreferencesDto.builder()
                .userId(userId)
                .inAppEnabled(false)   // in-app is handled directly by alert-service/InAppChannel
                .emailEnabled(true)
                .email(null)           // EmailDispatchService reads notification.email.to from config
                .telegramEnabled(true)
                .telegramChatId("configured") // TelegramBot reads chat-ids from config
                .build();
    }
}
