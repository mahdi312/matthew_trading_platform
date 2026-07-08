package com.mst.matt.tradingplatformapp.service.analysis;

import com.mst.matt.tradingplatformapp.model.*;
import com.mst.matt.tradingplatformapp.repository.IndicatorConfigRepository;
import com.mst.matt.tradingplatformapp.service.OhlcvStorageService;
import com.mst.matt.tradingplatformapp.service.alert.AlertService;
import com.mst.matt.tradingplatformapp.service.marketdata.MarketDataSyncScheduler;
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
 *   3. Compute indicators on demand (IndicatorService for signal scoring,
 *      IndicatorComputeService for chart rendering via IndicatorDefinitions)
 *   4. Compute support / resistance levels (SupportResistanceService)
 *   5. Score composite signal (SignalScoringService)
 *   6. Notify AlertService if buy/sell signal threshold crossed
 *
 * NOTE: Indicator series are no longer stored in separate DB tables.
 *       They are computed fresh on each chart load directly from OHLCV data.
 */
@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    @Autowired private OhlcvStorageService       ohlcvStorageService;
    @Autowired private IndicatorService          indicatorService;
    @Autowired private IndicatorComputeService   indicatorComputeService;
    @Autowired private SupportResistanceService  srService;
    @Autowired private SignalScoringService      scoringService;
    @Autowired private IndicatorConfigRepository configRepo;
    @Autowired private AlertService              alertService;
    @Autowired private MarketDataSyncScheduler   marketDataSyncScheduler;

    @Value("${app.chart.default-bars:200}")
    private int defaultBars;

    private final Map<String, AnalysisResult> analysisCache = new ConcurrentHashMap<>();
    private final Set<String> activeWatchlist  = ConcurrentHashMap.newKeySet();
    private volatile UserProfile activeProfile;

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
        List<OhlcvBar> bars = OhlcvStorageService.chronological(
                ohlcvStorageService.getBars(symbol, timeframe, barCount, profile));

        if (bars.isEmpty()) {
            log.warn("No OHLCV data for {}/{}", symbol, timeframe);
            return AnalysisResult.empty(symbol, timeframe);
        }

        // 2. Build BarSeries (for legacy IndicatorService used by signal scoring)
        BarSeries series = indicatorService.toBarSeries(bars, symbol);

        // 3. Compute indicators (for signal scoring — uses IndicatorResult/IndicatorConfig)
        IndicatorResult indicators = indicatorService.compute(series, config);

        // 4. Support / Resistance
        SupportResistanceService.SRResult sr = srService.analyze(
                bars, config.getFibonacciLookback() > 0 ? config.getFibonacciLookback() : 50);

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
     */
    @Scheduled(fixedRate = 60000)
    public void backgroundRefresh() {
        if (activeProfile == null || activeWatchlist.isEmpty()) return;
        activeWatchlist.forEach(symbol -> {
            try {
                ohlcvStorageService.refreshBars(symbol, "1h", defaultBars, activeProfile);
                analyze(symbol, "1h", activeProfile);
            } catch (Exception e) {
                log.error("Background analysis failed for {}: {}", symbol, e.getMessage());
            }
        });
    }

    public void setActiveProfile(UserProfile profile) {
        this.activeProfile = profile;
        marketDataSyncScheduler.setActiveProfile(profile);
    }
    public void addToWatchlist(String symbol)    { activeWatchlist.add(symbol); }
    public void removeFromWatchlist(String symbol) { activeWatchlist.remove(symbol); }

    public Optional<AnalysisResult> getCached(String symbol, String timeframe) {
        return Optional.ofNullable(analysisCache.get(symbol + "_" + timeframe));
    }

    // ── BarSeries accessor for chart rendering ────────────────

    /**
     * Builds a ta4j BarSeries from a list of OhlcvBars.
     * Used by ChartController to compute IndicatorDefinitions on the fly.
     */
    public BarSeries buildBarSeries(List<OhlcvBar> bars, String name) {
        return indicatorComputeService.toBarSeries(bars, name);
    }

    /**
     * Runs the full analysis pipeline on a pre-loaded list of bars.
     * Used by ChartController when bars have already been fetched from a specific
     * provider (non-AUTO mode) so we don't need to re-fetch from DB.
     *
     * @param bars      already-fetched OHLCV bars (must be non-empty)
     * @param symbol    trading symbol for the BarSeries name
     * @param timeframe timeframe string for cache key
     * @param profile   active user profile for indicator config
     * @return full analysis result
     */
    public AnalysisResult analyzeFromBars(List<OhlcvBar> bars, String symbol,
                                          String timeframe, UserProfile profile) {
        if (bars == null || bars.isEmpty()) {
            return AnalysisResult.empty(symbol, timeframe);
        }

        IndicatorConfig config = configRepo.findByProfile(profile)
                .orElse(IndicatorConfig.fromProfile(
                        IndicatorConfig.IndicatorProfile.SWING_TRADING, profile));

        BarSeries series = indicatorService.toBarSeries(bars, symbol);
        IndicatorResult indicators = indicatorService.compute(series, config);
        SupportResistanceService.SRResult sr = srService.analyze(
                bars, config.getFibonacciLookback() > 0 ? config.getFibonacciLookback() : 50);
        double currentPrice = bars.get(bars.size() - 1).getClose().doubleValue();
        SignalScoringService.SignalResult signal = scoringService.score(indicators, sr, config, currentPrice);

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
        analysisCache.put(symbol + "_" + timeframe, result);
        return result;
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
