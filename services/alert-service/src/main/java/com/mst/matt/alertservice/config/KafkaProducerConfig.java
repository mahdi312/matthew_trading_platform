package com.mst.matt.alertservice.config;

import com.mst.matt.contracts.dto.AlertTriggeredEventDto;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka producer configuration for publishing {@link AlertTriggeredEventDto}
 * to the {@code alerts.triggered} topic (see {@code AlertEventPublisher}).
 *
 * <p>JSON value serialization keeps the wire format human-readable and
 * consumer-agnostic — {@code notification-service} (Step 7) deserializes the
 * same DTO from {@code shared/contracts} without needing Avro/schema-registry
 * infrastructure for this first cross-service event type.</p>
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, AlertTriggeredEventDto> alertProducerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, AlertTriggeredEventDto> kafkaTemplate(
            ProducerFactory<String, AlertTriggeredEventDto> alertProducerFactory) {
        return new KafkaTemplate<>(alertProducerFactory);
    }
}
