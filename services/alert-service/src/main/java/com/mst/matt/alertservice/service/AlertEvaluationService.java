package com.mst.matt.alertservice.service;

import com.mst.matt.alertservice.kafka.AlertEventPublisher;
import com.mst.matt.alertservice.model.PriceAlert;
import com.mst.matt.alertservice.notification.UserPreferencesResolver;
import com.mst.matt.alertservice.repository.PriceAlertRepository;
import com.mst.matt.contracts.dto.AlertTriggeredEventDto;
import com.mst.matt.contracts.dto.UserPreferencesDto;
import com.mst.matt.contracts.enums.AlertCondition;
import com.mst.matt.contracts.enums.AlertStatus;
import com.mst.matt.contracts.notification.NotificationChannel;
import com.mst.matt.contracts.provider.dto.NormalizedOhlcvBar;
import com.mst.matt.contracts.provider.ohlcv.OhlcvDataProvider;
import com.mst.matt.contracts.provider.registry.ProviderRegistry;
import com.mst.matt.contracts.provider.registry.ProviderUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Core alert engine — polls active alerts on a fixed schedule, evaluates
 * each against a live price read through the
 * {@link ProviderRegistry}&lt;{@link OhlcvDataProvider}&gt; (never a broker
 * or provider SDK directly, per Step 4/4.5's abstraction rules), and on a
 * condition match publishes an {@link AlertTriggeredEventDto} to Kafka while
 * delivering the in-app notification synchronously.
 *
 * <h3>Connection-pool hygiene (mirrors the JavaFX monolith's {@code AlertService})</h3>
 * <p>The polling loop ({@link #evaluateAlerts}) is deliberately <em>not</em>
 * {@code @Transactional} at the method level — that would hold a DB
 * connection open across every external price-provider call. Work is split
 * into three phases:</p>
 * <ol>
 *   <li><b>Read</b> — fetch evaluation candidates in a short read-only
 *       transaction ({@link #fetchCandidates}).</li>
 *   <li><b>Evaluate</b> — call the provider registry and evaluate the
 *       condition with no DB connection held.</li>
 *   <li><b>Write</b> — persist the triggered/baseline state in its own
 *       short, independent transaction ({@link #persistTriggered} /
 *       {@link #persistBaseline}) using {@code REQUIRES_NEW} so one alert's
 *       write never rolls back another's.</li>
 * </ol>
 *
 * <h3>One-shot vs. repeating</h3>
 * <p>Non-repeating alerts move to {@link AlertStatus#TRIGGERED} and are
 * excluded from future candidate queries. Repeating alerts also move to
 * {@link AlertStatus#TRIGGERED} but re-enter the candidate set (and are
 * re-evaluated) once {@link PriceAlert#getCooldownSeconds()} have elapsed —
 * see {@link PriceAlert#isEligibleForEvaluation()}.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertEvaluationService {

    /** Standardised short interval used purely to read the latest close price. */
    private static final String LATEST_PRICE_INTERVAL = "1m";

    private final PriceAlertRepository alertRepository;
    private final ProviderRegistry<OhlcvDataProvider> ohlcvRegistry;
    private final List<NotificationChannel> notificationChannels;
    private final UserPreferencesResolver preferencesResolver;
    private final AlertEventPublisher eventPublisher;

    @Scheduled(fixedRateString = "${app.alert.poll-interval-ms:10000}")
    public void evaluateAlerts() {
        // Phase 1: read — short read-only TX, connection released immediately after.
        List<PriceAlert> candidates = fetchCandidates();
        if (candidates.isEmpty()) return;

        // Phase 2: evaluate — no DB connection held during provider calls.
        for (PriceAlert alert : candidates) {
            if (!alert.isEligibleForEvaluation()) continue;
            try {
                evaluateAndFire(alert);
            } catch (Exception ex) {
                log.error("Error evaluating alertId={}: {}", alert.getId(), ex.getMessage(), ex);
            }
        }
    }

    @Transactional(readOnly = true)
    protected List<PriceAlert> fetchCandidates() {
        return alertRepository.findByStatusInOrderBySymbolAsc(
                List.of(AlertStatus.ACTIVE, AlertStatus.TRIGGERED));
    }

    private void evaluateAndFire(PriceAlert alert) {
        Optional<BigDecimal> priceOpt = fetchLatestPrice(alert);
        if (priceOpt.isEmpty()) {
            log.debug("No price available for alertId={} symbol={}, skipping this cycle",
                    alert.getId(), alert.getSymbol());
            return;
        }
        BigDecimal currentPrice = priceOpt.get();

        // PERCENT_CHANGE alerts need a reference point; record it on first
        // sight (or after a repeating alert re-arms) without firing.
        if (alert.getCondition() == AlertCondition.PERCENT_CHANGE && alert.getBaselineValue() == null) {
            persistBaseline(alert.getId(), currentPrice);
            return;
        }

        boolean shouldFire = evaluateCondition(alert, currentPrice);
        if (!shouldFire) return;

        AlertTriggeredEventDto event = buildEvent(alert, currentPrice);

        // Fire notifications first (no DB connection required) — in-app is
        // synchronous/real now; email/Telegram are no-op stubs until Step 7.
        for (NotificationChannel channel : notificationChannels) {
            UserPreferencesDto prefs = preferencesResolver.resolve(alert.getUserId());
            channel.send(event, prefs);
        }

        // Publish to Kafka for Step 7's notification-service consumers.
        eventPublisher.publish(event);

        // Phase 3: write — short, independent transaction per alert.
        persistTriggered(alert.getId(), alert.isRepeating(), currentPrice);
    }

    /**
     * Reads the latest price for the alert's symbol through the
     * asset-class-aware registry's fallback chain — never a provider SDK
     * directly.
     */
    private Optional<BigDecimal> fetchLatestPrice(PriceAlert alert) {
        try {
            List<NormalizedOhlcvBar> bars = ohlcvRegistry.executeWithFallback(
                    alert.getAssetClass(),
                    provider -> provider.getHistoricalBars(
                            alert.getSymbol(), alert.getAssetClass(), LATEST_PRICE_INTERVAL, 1));
            if (bars == null || bars.isEmpty()) return Optional.empty();
            return Optional.ofNullable(bars.get(bars.size() - 1).getClose());
        } catch (ProviderUnavailableException ex) {
            log.warn("All OHLCV providers unavailable for assetClass={} symbol={}: {}",
                    alert.getAssetClass(), alert.getSymbol(), ex.getMessage());
            return Optional.empty();
        }
    }

    private boolean evaluateCondition(PriceAlert alert, BigDecimal currentPrice) {
        return switch (alert.getCondition()) {
            case ABOVE -> currentPrice.compareTo(alert.getTargetValue()) >= 0;
            case BELOW -> currentPrice.compareTo(alert.getTargetValue()) <= 0;
            case PERCENT_CHANGE -> {
                BigDecimal baseline = alert.getBaselineValue();
                if (baseline == null || baseline.signum() == 0) yield false;
                BigDecimal changePct = currentPrice.subtract(baseline)
                        .divide(baseline, 8, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                yield changePct.abs().compareTo(alert.getTargetValue()) >= 0;
            }
        };
    }

    private AlertTriggeredEventDto buildEvent(PriceAlert alert, BigDecimal currentPrice) {
        return AlertTriggeredEventDto.builder()
                .eventId(UUID.randomUUID().toString())
                .alertId(alert.getId())
                .userId(alert.getUserId())
                .symbol(alert.getSymbol())
                .assetClass(alert.getAssetClass())
                .brokerType(alert.getBrokerType())
                .condition(alert.getCondition())
                .targetValue(alert.getTargetValue())
                .observedValue(currentPrice)
                .message(alert.getMessage())
                .triggeredAt(Instant.now())
                .build();
    }

    /**
     * Phase 3 (baseline) — record the reference price for a PERCENT_CHANGE
     * alert without firing. {@code REQUIRES_NEW} keeps this write short and
     * independent of any surrounding context.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void persistBaseline(Long alertId, BigDecimal baseline) {
        alertRepository.findById(alertId).ifPresent(a -> {
            a.setBaselineValue(baseline);
            alertRepository.save(a);
        });
    }

    /**
     * Phase 3 (trigger) — persist the triggered state. {@code REQUIRES_NEW}
     * so each alert update commits independently; a single failure does not
     * roll back the others in the same evaluation cycle.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void persistTriggered(Long alertId, boolean repeating, BigDecimal firedAtPrice) {
        alertRepository.findById(alertId).ifPresent(a -> {
            a.setStatus(AlertStatus.TRIGGERED);
            a.setLastTriggeredAt(Instant.now());
            // Repeating PERCENT_CHANGE alerts re-baseline from the firing price
            // so the next cooldown cycle measures the move from here forward.
            if (repeating && a.getCondition() == AlertCondition.PERCENT_CHANGE) {
                a.setBaselineValue(firedAtPrice);
            }
            alertRepository.save(a);
        });
    }
}
