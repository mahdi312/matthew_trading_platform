package com.mst.matt.alertservice.kafka;

import com.mst.matt.contracts.dto.AlertTriggeredEventDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes {@link AlertTriggeredEventDto} to the {@code alerts.triggered}
 * Kafka topic on condition match.
 *
 * <p>Consumers (Step 7's {@code notification-service}) subscribe to this
 * topic to fan out email/Telegram notifications. {@code alert-service}
 * itself does not consume this topic — its own
 * {@code InAppNotificationChannel} delivers in-app notifications directly
 * (see that class's javadoc) without going through Kafka, so in-app delivery
 * has zero dependency on the topic being consumed.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertEventPublisher {

    public static final String TOPIC = "alerts.triggered";

    private final KafkaTemplate<String, AlertTriggeredEventDto> kafkaTemplate;

    public void publish(AlertTriggeredEventDto event) {
        try {
            // Key by userId so all of a user's alert events land on the same
            // partition, preserving per-user ordering for downstream consumers.
            kafkaTemplate.send(TOPIC, String.valueOf(event.getUserId()), event);
            log.info("Published AlertTriggeredEventDto to '{}': alertId={} symbol={} userId={}",
                    TOPIC, event.getAlertId(), event.getSymbol(), event.getUserId());
        } catch (Exception ex) {
            // Publishing failure must not block in-app delivery, which already
            // happened synchronously before this call — log and move on.
            log.error("Failed to publish AlertTriggeredEventDto (alertId={}) to '{}': {}",
                    event.getAlertId(), TOPIC, ex.getMessage(), ex);
        }
    }
}
