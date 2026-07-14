package com.mst.matt.notificationservice.config;

import com.mst.matt.contracts.dto.AlertTriggeredEventDto;
import com.mst.matt.contracts.dto.TradeEventDto;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka consumer configuration for {@code notification-service}.
 *
 * <p>A single {@link ConcurrentKafkaListenerContainerFactory} handles both
 * {@code AlertTriggeredEventDto} (from {@code alerts.triggered}) and
 * {@code TradeEventDto} (from {@code trades.executed} / {@code trades.closed})
 * because Spring Kafka's {@link JsonDeserializer} can be configured to trust
 * any class in our contracts module.</p>
 *
 * <h3>Error handling</h3>
 * <p>The consumer uses {@link ErrorHandlingDeserializer} as a wrapper so that
 * a single malformed message does not crash the listener thread — the deserialization
 * error is logged and the offset is committed, preventing infinite retry loops.</p>
 */
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        JsonDeserializer<Object> deserializer = new JsonDeserializer<>();
        // Trust all classes from the contracts module (AlertTriggeredEventDto, TradeEventDto)
        deserializer.addTrustedPackages(
                "com.mst.matt.contracts.dto",
                "com.mst.matt.contracts.enums"
        );
        deserializer.setUseTypeHeaders(true);

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());
        props.put(JsonDeserializer.TRUSTED_PACKAGES,
                "com.mst.matt.contracts.dto,com.mst.matt.contracts.enums");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        // Group IDs are set per-listener via @KafkaListener(groupId=...) so the
        // factory does not specify a default group.

        return new DefaultKafkaConsumerFactory<>(props,
                new StringDeserializer(),
                new ErrorHandlingDeserializer<>(deserializer));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(1); // single partition per topic in dev; increase for production
        return factory;
    }
}
