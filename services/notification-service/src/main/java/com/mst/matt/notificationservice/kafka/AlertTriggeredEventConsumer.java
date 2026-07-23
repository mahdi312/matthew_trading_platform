package com.mst.matt.notificationservice.kafka;

import com.mst.matt.contracts.dto.AlertTriggeredEventDto;
import com.mst.matt.contracts.dto.UserPreferencesDto;
import com.mst.matt.contracts.notification.NotificationChannel;
import com.mst.matt.notificationservice.client.IdentityClient;
import com.mst.matt.notificationservice.client.IdentityClientFallback;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.mst.matt.contracts.observability.CorrelationIdFilter.MDC_KEY;

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
    private final IdentityClient identityClient;



    @KafkaListener(
            topics  = TOPIC,
            groupId = GROUP_ID,
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onAlertTriggered(
            AlertTriggeredEventDto event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = "X-Correlation-Id", required = false) String correlationId) {

        log.info("[AlertConsumer] received from {}[{}@{}]: alertId={} userId={} symbol={}",
                TOPIC, partition, offset,
                event.getAlertId(), event.getUserId(), event.getSymbol());


        if (correlationId != null) {
            MDC.put(MDC_KEY, correlationId);
        }
        UserPreferencesDto prefs = resolvePreferences(event.getUserId());

        for (NotificationChannel channel : channels) {
            try {
                channel.send(event, prefs);
            } catch (Exception ex) {
                log.error("[AlertConsumer] channel {} threw uncaught exception for alertId={}: {}",
                        channel.channelName(), event.getAlertId(), ex.getMessage(), ex);
            }
            finally {
                org.slf4j.MDC.remove(MDC_KEY);
            }
        }
    }

    /**
     * Resolves user notification preferences via a real Feign call to identity-service.
     * Falls back to no email/Telegram delivery (see {@link
     * IdentityClientFallback}) if identity-service
     * is unreachable — this method itself never throws.
     */
    private UserPreferencesDto resolvePreferences(Long userId) {
        return identityClient.getPreferences(userId);
    }
}
