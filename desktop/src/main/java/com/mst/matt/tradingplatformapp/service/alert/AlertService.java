package com.mst.matt.tradingplatformapp.service.alert;

import com.mst.matt.tradingplatformapp.model.*;
import com.mst.matt.tradingplatformapp.model.PriceAlert.*;
import com.mst.matt.tradingplatformapp.repository.*;
import com.mst.matt.tradingplatformapp.service.price.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Core alert engine.
 *
 * Polls all active alerts every 10 seconds.
 * When triggered: fires email, Telegram, and/or desktop notification
 * based on the alert's configuration.
 *
 * <h3>Connection-pool hygiene</h3>
 * The polling loop ({@link #checkAlerts}) is deliberately <em>not</em>
 * annotated with {@code @Transactional} at the method level.  Doing so
 * would hold a JDBC connection open across every external price-API call,
 * exhausting the HikariCP pool under load.  Instead the work is split into
 * three clearly-bounded phases:
 * <ol>
 *   <li><strong>Read</strong>  – fetch active alerts inside a short
 *       read-only transaction ({@link #fetchActiveAlerts}).</li>
 *   <li><strong>Evaluate</strong> – call the price router and evaluate
 *       conditions with <em>no</em> database connection held.</li>
 *   <li><strong>Write</strong>  – persist the triggered state inside a
 *       short, focused write transaction ({@link #persistTriggeredAlert}).
 *       Uses {@code REQUIRES_NEW} so each update is committed independently
 *       and a single failure does not roll back the others.</li>
 * </ol>
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    @Autowired private PriceAlertRepository  alertRepository;
    @Autowired private PriceRouter           priceRouter;
    @Autowired private NotificationService   notificationService;

    /**
     * Main polling loop.
     * Interval is controlled by app.alert.poll-interval-ms (default 10 000 ms).
     * Use milliseconds directly — Spring @Scheduled does not support
     * arithmetic inside ${...} placeholders.
     *
     * <p><b>NOT annotated with {@code @Transactional}</b> — see class-level
     * javadoc for the reasoning.  Transactions are opened only in the helper
     * methods below, each of which is short-lived.</p>
     */
    @Scheduled(fixedRateString = "${app.alert.poll-interval-ms:10000}")
    public void checkAlerts() {
        // Phase 1: read — short read-only TX, connection released immediately after.
        List<PriceAlert> activeAlerts = fetchActiveAlerts();
        if (activeAlerts.isEmpty()) return;

        // Phase 2: evaluate — no DB connection held during external API calls.
        for (PriceAlert alert : activeAlerts) {
            try {
                evaluateAndFire(alert);
            } catch (Exception e) {
                log.error("Error checking alert {}: {}", alert.getId(), e.getMessage());
            }
        }
    }

    /**
     * Phase 1 – fetch active alerts.
     * Short read-only transaction; connection is released as soon as this
     * method returns.
     */
    @Transactional(readOnly = true)
    protected List<PriceAlert> fetchActiveAlerts() {
        return alertRepository.findByActiveTrue();
    }

    /**
     * Phase 2 – evaluate condition and fire notifications (no DB connection held).
     * If the alert should fire, delegates persistence to {@link #persistTriggeredAlert}.
     */
    private void evaluateAndFire(PriceAlert alert) {
        // Indicator alerts are fired by AnalysisService via triggerIndicatorAlert()
        if (alert.getAlertType() == AlertType.INDICATOR_BUY_SIGNAL
                || alert.getAlertType() == AlertType.INDICATOR_SELL_SIGNAL) {
            return;
        }

        // Skip already-triggered non-repeating alerts
        if (alert.isTriggered() && !alert.isRepeating()) return;

        // External API call — must NOT be inside a DB transaction.
        Optional<PriceQuote> quoteOpt = priceRouter.getQuote(alert.getSymbol());
        if (quoteOpt.isEmpty()) return;

        PriceQuote quote = quoteOpt.get();
        boolean shouldFire = evaluateCondition(alert, quote);

        if (shouldFire) {
            // Fire notifications (no DB connection required).
            fireAlert(alert, quote);

            // Phase 3: write — short, independent transaction per alert.
            persistTriggeredAlert(alert.getId(), alert.isRepeating());
        }
    }

    /**
     * Phase 3 – persist the triggered state.
     * Uses {@code REQUIRES_NEW} so each alert update is committed in its own
     * short transaction, independent of any surrounding context.  This
     * guarantees the connection is released quickly even if caller code holds
     * an outer transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void persistTriggeredAlert(Long alertId, boolean repeating) {
        alertRepository.findById(alertId).ifPresent(a -> {
            a.setTriggered(true);
            a.setTriggeredAt(LocalDateTime.now());
            if (!repeating) a.setActive(false);
            alertRepository.save(a);
        });
    }

    /**
     * Evaluates whether an alert's condition is met.
     */
    private boolean evaluateCondition(PriceAlert alert, PriceQuote quote) {
        BigDecimal currentPrice = quote.getPrice();

        return switch (alert.getAlertType()) {
            case PRICE_ABOVE ->
                    alert.getTargetPrice() != null
                    &&
                    currentPrice.compareTo(alert.getTargetPrice()) >= 0;

            case PRICE_BELOW ->
                    alert.getTargetPrice() != null
                    &&
                    currentPrice.compareTo(alert.getTargetPrice()) <= 0;

            case PCT_CHANGE_24H -> {
                if (quote.getChangePct24h() == null
                        || alert.getPercentageThreshold() == null) {
                    yield false;
                }
                yield quote.getChangePct24h().abs()
                        .compareTo(alert.getPercentageThreshold()) >= 0;
            }

            case INDICATOR_BUY_SIGNAL, INDICATOR_SELL_SIGNAL -> false;

            case FIBONACCI_LEVEL_TOUCH -> {
                // Check if price is within 0.3% of target level
                if (alert.getTargetPrice() == null) yield false;
                BigDecimal diff = currentPrice.subtract(alert.getTargetPrice()).abs();
                BigDecimal threshold = alert.getTargetPrice()
                        .multiply(new BigDecimal("0.003"));
                yield diff.compareTo(threshold) <= 0;
            }

            case VOLUME_SPIKE -> {
                if (quote.getVolume24h() == null || alert.getTargetPrice() == null)
                    yield false;
                // targetPrice field overloaded: stores volume threshold here
                yield quote.getVolume24h().compareTo(alert.getTargetPrice()) >= 0;
            }
        };
    }

    private void fireAlert(PriceAlert alert, PriceQuote quote) {
        String title   = buildTitle(alert, quote);
        String message = buildMessage(alert, quote);

        log.info("🔔 ALERT FIRED: {}", title);

        if (alert.isNotifyDesktop()) notificationService.sendDesktop(title, message);
        if (alert.isNotifyEmail())   notificationService.sendEmail(title, message);
        if (alert.isNotifyTelegram())notificationService.sendTelegram(title, message);
    }

    private String buildTitle(PriceAlert alert, PriceQuote quote) {
        return switch (alert.getAlertType()) {
            case PRICE_ABOVE  -> "📈 " + alert.getSymbol() + " crossed ABOVE $"
                    + alert.getTargetPrice();
            case PRICE_BELOW  -> "📉 " + alert.getSymbol() + " dropped BELOW $"
                    + alert.getTargetPrice();
            case PCT_CHANGE_24H -> {
                String pct = quote.getChangePct24h() != null
                        ? quote.getChangePct24h().toPlainString() : "N/A";
                yield "⚡ " + alert.getSymbol() + " moved " + pct + "% in 24h";
            }
            case INDICATOR_BUY_SIGNAL  -> "🟢 BUY SIGNAL: " + alert.getSymbol();
            case INDICATOR_SELL_SIGNAL -> "🔴 SELL SIGNAL: " + alert.getSymbol();
            case FIBONACCI_LEVEL_TOUCH -> "🔷 " + alert.getSymbol()
                    + " touched Fib level $" + alert.getTargetPrice();
            case VOLUME_SPIKE -> "🔊 Volume Spike: " + alert.getSymbol();
        };
    }

    private String buildMessage(PriceAlert alert, PriceQuote quote) {
        String custom = alert.getCustomMessage() != null
                ? "\n📝 Note: " + alert.getCustomMessage() : "";
        String changePct = quote.getChangePct24h() != null
                ? quote.getChangePct24h().toPlainString() : "N/A";
        return String.format(
                "Symbol: %s\nCurrent Price: $%s\n24h Change: %s%%\nTime: %s%s",
                alert.getSymbol(),
                quote.getPrice().toPlainString(),
                changePct,
                LocalDateTime.now(),
                custom
        );
    }

    // ── CRUD ────────────────────────────────────────────────

    @Transactional
    public PriceAlert createAlert(PriceAlert alert) {
        return alertRepository.save(alert);
    }

    @Transactional
    public void deleteAlert(Long id) {
        alertRepository.deleteById(id);
    }

    @Transactional
    public void toggleAlert(Long id, boolean active) {
        alertRepository.findById(id).ifPresent(a -> {
            a.setActive(active);
            if (active) a.setTriggered(false); // re-arm
            alertRepository.save(a);
        });
    }

    @Transactional(readOnly = true)
    public List<PriceAlert> getAlertsForProfile(UserProfile profile) {
        return alertRepository.findByProfileOrderByCreatedAtDesc(profile);
    }

    /**
     * Called by AnalysisService when a composite indicator signal fires.
     * Delivers notifications immediately (not via the price polling loop).
     *
     * <p>The price lookup is done outside any transaction; only the final
     * persistence step opens a short write transaction.</p>
     */
    public void triggerIndicatorAlert(String symbol, boolean isBuySignal) {
        AlertType type = isBuySignal
                ? AlertType.INDICATOR_BUY_SIGNAL
                : AlertType.INDICATOR_SELL_SIGNAL;

        // External call — no DB connection held.
        Optional<PriceQuote> quoteOpt = priceRouter.getQuote(symbol);
        if (quoteOpt.isEmpty()) {
            log.warn("Indicator alert skipped — no quote for {}", symbol);
            return;
        }
        PriceQuote quote = quoteOpt.get();

        // Read matching alerts in a short read-only TX.
        List<PriceAlert> candidates = fetchIndicatorAlertCandidates(symbol, type);

        // Fire notifications (no DB connection held).
        for (PriceAlert a : candidates) {
            fireAlert(a, quote);
            // Persist result in its own short TX.
            persistTriggeredAlert(a.getId(), a.isRepeating());
        }
    }

    /**
     * Fetch indicator alert candidates in a short read-only transaction.
     */
    @Transactional(readOnly = true)
    protected List<PriceAlert> fetchIndicatorAlertCandidates(String symbol, AlertType type) {
        return alertRepository.findByActiveTrue().stream()
                .filter(a -> a.getSymbol().equalsIgnoreCase(symbol)
                        && a.getAlertType() == type
                        && (!a.isTriggered() || a.isRepeating()))
                .toList();
    }
}
