package com.mst.matt.tradingplatformapp.service.marketdata;

import com.mst.matt.tradingplatformapp.config.MarketDataProperties;
import com.mst.matt.tradingplatformapp.model.IndicatorConfig;
import com.mst.matt.tradingplatformapp.model.OhlcvBar;
import com.mst.matt.tradingplatformapp.service.analysis.IndicatorResult;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persists computed indicator series in symbol/timeframe tables (not API-provider tables).
 */
@Service
public class IndicatorSeriesStorageService {

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

    public void save(String symbol, String timeframe, IndicatorResult r, List<OhlcvBar> bars) {
        if (!isEnabled() || r == null || bars == null || bars.isEmpty()) return;
        List<LocalDateTime> times = bars.stream().map(OhlcvBar::getOpenTime).toList();
        saveSeries(symbol, timeframe, IndicatorTableNameUtil.SeriesType.EMA, r.getEmaFastSeries(), times);
        saveSeries(symbol, timeframe, IndicatorTableNameUtil.SeriesType.RSI, r.getRsiSeries(), times);
        saveSeries(symbol, timeframe, IndicatorTableNameUtil.SeriesType.MACD, r.getMacdLineSeries(), times);
        saveSeries(symbol, timeframe, IndicatorTableNameUtil.SeriesType.BB, r.getBbMiddleSeries(), times);
        saveSeries(symbol, timeframe, IndicatorTableNameUtil.SeriesType.VOL,
                bars.stream().map(b -> b.getVolume().doubleValue()).toList(), times);
        nextRefresh.put(key(symbol, timeframe),
                LocalDateTime.now().plus(TimeframeInterval.forTimeframe(timeframe)));
    }

    public IndicatorResult enrichFromTables(String symbol, String timeframe,
                                            IndicatorResult computed,
                                            IndicatorConfig config, int barCount) {
        if (!isEnabled() || !isFresh(symbol, timeframe) || computed == null) {
            return computed;
        }
        var b = computed.toBuilder();
        loadInto(symbol, timeframe, IndicatorTableNameUtil.SeriesType.EMA, barCount)
                .ifPresent(b::emaFastSeries);
        loadInto(symbol, timeframe, IndicatorTableNameUtil.SeriesType.RSI, barCount)
                .ifPresent(b::rsiSeries);
        loadInto(symbol, timeframe, IndicatorTableNameUtil.SeriesType.MACD, barCount)
                .ifPresent(b::macdLineSeries);
        loadInto(symbol, timeframe, IndicatorTableNameUtil.SeriesType.BB, barCount)
                .ifPresent(b::bbMiddleSeries);
        return b.build();
    }

    private void saveSeries(String symbol, String timeframe, IndicatorTableNameUtil.SeriesType type,
                            List<Double> values, List<LocalDateTime> times) {
        if (values == null || values.isEmpty()) return;
        String table = IndicatorTableNameUtil.tableName(symbol, type, timeframe);
        indicatorTables.upsertSeries(table, times, values);
    }

    private java.util.Optional<List<Double>> loadInto(String symbol, String timeframe,
                                                    IndicatorTableNameUtil.SeriesType type,
                                                    int limit) {
        String table = IndicatorTableNameUtil.tableName(symbol, type, timeframe);
        List<Double> loaded = indicatorTables.loadSeries(table, limit);
        return loaded.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(loaded);
    }

    private static String key(String symbol, String timeframe) {
        return symbol.toUpperCase() + "_" + timeframe.toLowerCase();
    }
}
