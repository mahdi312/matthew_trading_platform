
package com.mst.matt.tradingservice.kafka;

import com.mst.matt.tradingservice.model.OutboxEvent;
import com.mst.matt.tradingservice.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Polls the outbox table and relays unpublished events to Kafka.
 *
 * <p>At-least-once delivery: if the process crashes between {@code kafkaTemplate.send}
 * succeeding and the {@code publishedAt} update committing, the row gets re-sent on the
 * next poll — a duplicate Kafka message, not a lost one. Consumers (notification-service)
 * already tolerate this: email/Telegram sends aren't strictly idempotent today, but a
 * duplicate notification is a far smaller problem than a silently lost one. Tighten this
 * further with a dedup key on the consumer side if duplicates become a real nuisance.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxRelayService {

    private static final int MAX_ATTEMPTS = 10;

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> rawKafkaTemplate; // raw String template — payload is pre-serialized JSON

    @Scheduled(fixedDelayString = "${outbox.relay.interval-ms:2000}")
    @Transactional
    public void relayPendingEvents() {
        List<OutboxEvent> pending = outboxRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
        if (pending.isEmpty()) return;

        for (OutboxEvent event : pending) {
            try {
                rawKafkaTemplate.send(event.getTopic(), event.getKafkaKey(), event.getPayload()).get(); // synchronous within the poll loop — simplicity over throughput here
                event.setPublishedAt(LocalDateTime.now());
                outboxRepository.save(event);
                log.debug("Outbox relay: published id={} topic={}", event.getId(), event.getTopic());
            } catch (Exception ex) {
                event.setAttempts(event.getAttempts() + 1);
                outboxRepository.save(event);
                if (event.getAttempts() >= MAX_ATTEMPTS) {
                    log.error("Outbox relay: id={} topic={} FAILED {} times — giving up, needs manual attention: {}",
                            event.getId(), event.getTopic(), event.getAttempts(), ex.getMessage());
                } else {
                    log.warn("Outbox relay: id={} topic={} attempt {} failed, will retry: {}",
                            event.getId(), event.getTopic(), event.getAttempts(), ex.getMessage());
                }
            }
        }
    }
}