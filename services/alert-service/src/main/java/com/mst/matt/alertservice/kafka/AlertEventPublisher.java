package com.mst.matt.alertservice.kafka;

import com.mst.matt.contracts.dto.AlertTriggeredEventDto;
import com.mst.matt.contracts.observability.CorrelationIdFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlertEventPublisher {

    public static final String TOPIC = "alerts.triggered";

    private final KafkaTemplate<String, AlertTriggeredEventDto> kafkaTemplate;

    public void publish(AlertTriggeredEventDto event) {
        try {
            ProducerRecord<String, AlertTriggeredEventDto> record =
                    new ProducerRecord<>(TOPIC, String.valueOf(event.getUserId()), event);

            String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
            if (correlationId != null) {
                record.headers().add(
                        CorrelationIdFilter.HEADER,
                        correlationId.getBytes(StandardCharsets.UTF_8));
            }

            kafkaTemplate.send(record);
            log.info("Published AlertTriggeredEventDto to '{}': userId={}",
                    TOPIC, event.getUserId());
        } catch (Exception ex) {
            log.error("Failed to publish AlertTriggeredEventDto to '{}': userId={}",
                    TOPIC, event.getUserId(), ex);
        }
    }
}