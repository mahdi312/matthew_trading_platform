package com.mst.matt.tradingplatformapp.service.analysis;

import com.mst.matt.tradingplatformapp.model.*;
import com.mst.matt.tradingplatformapp.repository.IndicatorConfigRepository;
import com.mst.matt.tradingplatformapp.service.OhlcvStorageService;
import com.mst.matt.tradingplatformapp.service.alert.AlertService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrates the complete analysis pipeline for a symbol:
 *
 *   1. Fetch or load OHLCV bars
 *   2. Build ta4j BarSeries
 *   3. Compute all indicators (IndicatorService)
 *   4. Compute support / resistance levels (SupportResistanceService)
 *   5. Score composite signal (SignalScoringService)
 *   6. Notify AlertService if buy/sell signal threshold crossed
 *
 * Also runs a background refresh every 60 seconds for the active watchlist.
 */
@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    @Autowired private OhlcvStorageService        ohlcvStorageService;
    @Autowired private IndicatorService           indicatorService;
    @Autowired private SupportResistanceService   srService;
    @Autowired private SignalScoringService        scoringService;
    @Autowired private IndicatorConfigRepository  configRepo;
    @Autowired private AlertService               alertService;

    @Value("${app.chart.default-bars:200}")
    private int defaultBars;

    // Cache: symbol+timeframe → last analysis result
    private final Map<String, AnalysisResult> analysisCache = new ConcurrentHashMap<>();

    // Watchlist for background refresh
    private final Set<String> activeWatchlist = ConcurrentHashMap.newKeySet();
    private volatile UserProfile activeProfile; // written on FX thread, read on scheduler

    /**
     * Run full analysis for a symbol with a given profile config.
     */
    public AnalysisResult analyze(String symbol, String timeframe, int barCount,
                                  UserProfile profile) {

        String cacheKey = symbol + "_" + timeframe;

        IndicatorConfig config = configRepo.findByProfile(profile)
                .orElse(IndicatorConfig.fromProfile(
                        IndicatorConfig.IndicatorProfile.SWING_TRADING, profile));

        // 1. Load bars
        List<OhlcvBar> bars = ohlcvStorageService.getBars(
                symbol, timeframe, barCount, profile);

        if (bars.isEmpty()) {
            log.warn("No OHLCV data for {}/{}", symbol, timeframe);
            return AnalysisResult.empty(symbol, timeframe);
        }

        // 2. Build BarSeries
        BarSeries series = indicatorService.toBarSeries(bars, symbol);

        // 3. Compute indicators
        IndicatorResult indicators = indicatorService.compute(series, config);

        // 4. Support / Resistance
        SupportResistanceService.SRResult sr = srService.analyze(
                bars, config.getFibonacciLookback() > 0
                        ? config.getFibonacciLookback() : 50);

        // 5. Signal scoring
        double currentPrice = bars.get(bars.size() - 1).getClose().doubleValue();
        SignalScoringService.SignalResult signal = scoringService.score(
                indicators, sr, config, currentPrice);

        // 6. Trigger indicator alerts
        if (signal.getConfidence() >= 60) {
            var rec = signal.getRecommendation();
            if (rec == SignalScoringService.Recommendation.STRONG_BUY
                    || rec == SignalScoringService.Recommendation.BUY) {
                alertService.triggerIndicatorAlert(symbol, true);
            } else if (rec == SignalScoringService.Recommendation.STRONG_SELL
                    || rec == SignalScoringService.Recommendation.SELL) {
                alertService.triggerIndicatorAlert(symbol, false);
            }
        }

        AnalysisResult result = AnalysisResult.builder()
                .symbol(symbol)
                .timeframe(timeframe)
                .bars(bars)
                .series(series)
                .indicators(indicators)
                .srResult(sr)
                .signal(signal)
                .currentPrice(currentPrice)
                .config(config)
                .build();

        analysisCache.put(cacheKey, result);
        return result;
    }

    /**
     * Convenience: analyze with default settings.
     */
    public AnalysisResult analyze(String symbol, String timeframe, UserProfile profile) {
        return analyze(symbol, timeframe, defaultBars, profile);
    }

    /**
     * Background refresh of analysis for all watchlist symbols.
     * Runs every 60 seconds to keep signal recommendations current.
     */
    @Scheduled(fixedRate = 60000)
    public void backgroundRefresh() {
        if (activeProfile == null || activeWatchlist.isEmpty()) return;
        activeWatchlist.forEach(symbol -> {
            try {
                // Refresh with default 1h timeframe
                ohlcvStorageService.refreshBars(symbol, "1h", defaultBars, activeProfile);
                analyze(symbol, "1h", activeProfile);
            } catch (Exception e) {
                log.error("Background analysis failed for {}: {}",
                        symbol, e.getMessage());
            }
        });
    }

    public void setActiveProfile(UserProfile profile) { this.activeProfile = profile; }
    public void addToWatchlist(String symbol) { activeWatchlist.add(symbol); }
    public void removeFromWatchlist(String symbol) { activeWatchlist.remove(symbol); }

    public Optional<AnalysisResult> getCached(String symbol, String timeframe) {
        return Optional.ofNullable(analysisCache.get(symbol + "_" + timeframe));
    }

    // ── Result DTO ───────────────────────────────────────────

    @lombok.Data @lombok.Builder
    @lombok.NoArgsConstructor @lombok.AllArgsConstructor
    public static class AnalysisResult {
        private String symbol;
        private String timeframe;
        private List<OhlcvBar> bars;
        private BarSeries series;
        private IndicatorResult indicators;
        private SupportResistanceService.SRResult srResult;
        private SignalScoringService.SignalResult signal;
        private double currentPrice;
        private IndicatorConfig config;

        public static AnalysisResult empty(String symbol, String tf) {
            return AnalysisResult.builder()
                    .symbol(symbol).timeframe(tf)
                    .bars(new ArrayList<>())
                    .indicators(IndicatorResult.empty())
                    .srResult(SupportResistanceService.SRResult.empty())
                    .build();
        }
    }
}