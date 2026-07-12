package com.mst.matt.tradingplatformapp.service.marketdata;

import com.mst.matt.tradingplatformapp.config.MarketDataProperties;
import com.mst.matt.tradingplatformapp.model.IndicatorConfig;
import com.mst.matt.tradingplatformapp.model.OhlcvBar;
import com.mst.matt.tradingplatformapp.service.analysis.IchimokuResult;
import com.mst.matt.tradingplatformapp.service.analysis.IndicatorResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persists ALL computed indicator series in per-symbol/timeframe tables.
 * Table naming: {@code BTCUSDT_EMA_1H}, {@code BTCUSDT_ICHIMOKU_1H}, etc.
 *
 * Strategy:
 *  - On first load or stale: compute fresh, then upsert ALL series into DB tables.
 *  - On subsequent loads within the refresh window: read from DB tables (skips API call overhead).
 *  - Upsert semantics: only inserts new candles or updates the last few (never full replace).
 */
@Service
public class IndicatorSeriesStorageService {

    private static final Logger log = LoggerFactory.getLogger(IndicatorSeriesStorageService.class);

    private final DynamicIndicatorTableService indicatorTables;
    private final MarketDataProperties properties;
    private final Map<String, LocalDateTime> nextRefresh = new ConcurrentHashMap<>();

    public IndicatorSeriesStorageService(DynamicIndicatorTableService indicatorTables,
                                         MarketDataProperties properties) {
        this.indicatorTables = indicatorTables;
        this.properties = properties;
    }

    public boolean isEnabled() {
        return properties.getDynamicTables().isEnabled();
    }

    public boolean isFresh(String symbol, String timeframe) {
        LocalDateTime due = nextRefresh.get(key(symbol, timeframe));
        return due != null && due.isAfter(LocalDateTime.now());
    }

    /**
     * Persists ALL indicator series for a symbol+timeframe to dedicated DB tables.
     * Uses upsert so only new/changed candle rows are written.
     */
    public void save(String symbol, String timeframe, IndicatorResult r, List<OhlcvBar> bars) {
        if (!isEnabled() || r == null || bars == null || bars.isEmpty()) return;
        List<LocalDateTime> times = bars.stream().map(OhlcvBar::getOpenTime).toList();

        // EMA series
        saveSeries(symbol, timeframe, IndicatorTableNameUtil.SeriesType.EMA,  r.getEmaFastSeries(), times);

        // RSI series
        saveSeries(symbol, timeframe, IndicatorTableNameUtil.SeriesType.RSI,  r.getRsiSeries(), times);

        // MACD series (line only — signal and histogram stored separately via multi-value approach)
        saveSeries(symbol, timeframe, IndicatorTableNameUtil.SeriesType.MACD, r.getMacdLineSeries(), times);

        // Bollinger Bands middle
        saveSeries(symbol, timeframe, IndicatorTableNameUtil.SeriesType.BB,   r.getBbMiddleSeries(), times);

        // Stochastic K
        saveSeries(symbol, timeframe, IndicatorTableNameUtil.SeriesType.STOCH, r.getStochasticKSeries(), times);

        // ATR
        saveSeries(symbol, timeframe, IndicatorTableNameUtil.SeriesType.ATR,  r.getAtrSeries(), times);

        // CCI
        saveSeries(symbol, timeframe, IndicatorTableNameUtil.SeriesType.CCI,  r.getCciSeries(), times);

        // VWAP
        saveSeries(symbol, timeframe, IndicatorTableNameUtil.SeriesType.VWAP, r.getVwapSeries(), times);

        // Volume
        List<Double> volumes = bars.stream().map(b -> b.getVolume().doubleValue()).toList();
        saveSeries(symbol, timeframe, IndicatorTableNameUtil.SeriesType.VOL, volumes, times);

        // Ichimoku — save Span A (bull cloud boundary) as the primary Ichimoku series
        IchimokuResult ich = r.getIchimoku();
        if (ich != null && ich.getSpanASeries() != null && !ich.getSpanASeries().isEmpty()) {
            saveSeries(symbol, timeframe, IndicatorTableNameUtil.SeriesType.ICHIMOKU,
                    ich.getSpanASeries(), times);
        }

        nextRefresh.put(key(symbol, timeframe),
                LocalDateTime.now().plus(TimeframeInterval.forTimeframe(timeframe)));
        log.debug("Saved indicator series for {}/{} ({} bars)", symbol, timeframe, bars.size());
    }

    /**
     * Enriches a computed IndicatorResult with values from DB tables when the cache is still fresh.
     * This allows chart rendering to skip recomputation while still showing up-to-date data.
     */
    public IndicatorResult enrichFromTables(String symbol, String timeframe,
                                            IndicatorResult computed,
                                            IndicatorConfig config, int barCount) {
        if (!isEnabled() || !isFresh(symbol, timeframe) || computed == null) {
            return computed;
        }
        var b = computed.toBuilder();
        loadInto(symbol, timeframe, IndicatorTableNameUtil.SeriesType.EMA,   barCount).ifPresent(b::emaFastSeries);
        loadInto(symbol, timeframe, IndicatorTableNameUtil.SeriesType.RSI,   barCount).ifPresent(b::rsiSeries);
        loadInto(symbol, timeframe, IndicatorTableNameUtil.SeriesType.MACD,  barCount).ifPresent(b::macdLineSeries);
        loadInto(symbol, timeframe, IndicatorTableNameUtil.SeriesType.BB,    barCount).ifPresent(b::bbMiddleSeries);
        loadInto(symbol, timeframe, IndicatorTableNameUtil.SeriesType.STOCH, barCount).ifPresent(b::stochasticKSeries);
        loadInto(symbol, timeframe, IndicatorTableNameUtil.SeriesType.ATR,   barCount).ifPresent(b::atrSeries);
        loadInto(symbol, timeframe, IndicatorTableNameUtil.SeriesType.CCI,   barCount).ifPresent(b::cciSeries);
        loadInto(symbol, timeframe, IndicatorTableNameUtil.SeriesType.VWAP,  barCount).ifPresent(b::vwapSeries);
        return b.build();
    }

    private void saveSeries(String symbol, String timeframe,
                            IndicatorTableNameUtil.SeriesType type,
                            List<Double> values, List<LocalDateTime> times) {
        if (values == null || values.isEmpty()) return;
        try {
            String table = IndicatorTableNameUtil.tableName(symbol, type, timeframe);
            indicatorTables.upsertSeries(table, times, values);
        } catch (Exception e) {
            log.debug("Could not save indicator series {}/{}/{}: {}", symbol, timeframe, type, e.getMessage());
        }
    }

    private Optional<List<Double>> loadInto(String symbol, String timeframe,
                                             IndicatorTableNameUtil.SeriesType type,
                                             int limit) {
        try {
            String table = IndicatorTableNameUtil.tableName(symbol, type, timeframe);
            List<Double> loaded = indicatorTables.loadSeries(table, limit);
            return loaded.isEmpty() ? Optional.empty() : Optional.of(loaded);
        } catch (Exception e) {
            log.debug("Could not load indicator series {}/{}/{}: {}", symbol, timeframe, type, e.getMessage());
            return Optional.empty();
        }
    }

    /** Returns the table name used for a given indicator type (for external tools/diagnostics). */
    public String tableName(String symbol, IndicatorTableNameUtil.SeriesType type, String timeframe) {
        return IndicatorTableNameUtil.tableName(symbol, type, timeframe);
    }

    private static String key(String symbol, String timeframe) {
        return symbol.toUpperCase() + "_" + timeframe.toLowerCase();
    }
}
