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
     */
    @Scheduled(fixedRateString = "${app.alert.poll-interval-ms:10000}")
    @Transactional
    public void checkAlerts() {
        List<PriceAlert> activeAlerts = alertRepository.findByActiveTrue();
        if (activeAlerts.isEmpty()) return;

        for (PriceAlert alert : activeAlerts) {
            try {
                checkSingleAlert(alert);
            } catch (Exception e) {
                log.error("Error checking alert {}: {}", alert.getId(), e.getMessage());
            }
        }
    }

    private void checkSingleAlert(PriceAlert alert) {
        // Indicator alerts are fired by AnalysisService via triggerIndicatorAlert()
        if (alert.getAlertType() == AlertType.INDICATOR_BUY_SIGNAL
                || alert.getAlertType() == AlertType.INDICATOR_SELL_SIGNAL) {
            return;
        }

        // Skip already-triggered non-repeating alerts
        if (alert.isTriggered() && !alert.isRepeating()) return;

        Optional<PriceQuote> quoteOpt = priceRouter.getQuote(alert.getSymbol());
        if (quoteOpt.isEmpty()) return;

        PriceQuote quote = quoteOpt.get();
        boolean shouldFire = evaluateCondition(alert, quote);

        if (shouldFire) {
            fireAlert(alert, quote);

            alert.setTriggered(true);
            alert.setTriggeredAt(LocalDateTime.now());

            // Deactivate if not repeating
            if (!alert.isRepeating()) alert.setActive(false);
            alertRepository.save(alert);
        }
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

    public PriceAlert createAlert(PriceAlert alert) {
        return alertRepository.save(alert);
    }

    public void deleteAlert(Long id) {
        alertRepository.deleteById(id);
    }

    public void toggleAlert(Long id, boolean active) {
        alertRepository.findById(id).ifPresent(a -> {
            a.setActive(active);
            if (active) a.setTriggered(false); // re-arm
            alertRepository.save(a);
        });
    }

    public List<PriceAlert> getAlertsForProfile(UserProfile profile) {
        return alertRepository.findByProfileOrderByCreatedAtDesc(profile);
    }

    /**
     * Called by AnalysisService when a composite indicator signal fires.
     * Delivers notifications immediately (not via the price polling loop).
     */
    public void triggerIndicatorAlert(String symbol, boolean isBuySignal) {
        AlertType type = isBuySignal
                ? AlertType.INDICATOR_BUY_SIGNAL
                : AlertType.INDICATOR_SELL_SIGNAL;

        Optional<PriceQuote> quoteOpt = priceRouter.getQuote(symbol);
        if (quoteOpt.isEmpty()) {
            log.warn("Indicator alert skipped — no quote for {}", symbol);
            return;
        }
        PriceQuote quote = quoteOpt.get();

        alertRepository.findByActiveTrue().stream()
                .filter(a -> a.getSymbol().equalsIgnoreCase(symbol)
                        && a.getAlertType() == type
                        && (!a.isTriggered() || a.isRepeating()))
                .forEach(a -> {
                    fireAlert(a, quote);
                    a.setTriggered(true);
                    a.setTriggeredAt(LocalDateTime.now());
                    if (!a.isRepeating()) {
                        a.setActive(false);
                    }
                    alertRepository.save(a);
                });
    }
}
