package com.mst.matt.marketservice.provider.stock;

import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.dto.NormalizedOhlcvBar;
import com.mst.matt.contracts.provider.ohlcv.OhlcvDataProvider;
import com.mst.matt.marketservice.client.HttpJsonClient;
import com.mst.matt.marketservice.client.api.MarketstackEodResponse;
import com.mst.matt.marketservice.config.MarketProviderProperties;
import com.mst.matt.marketservice.service.OhlcvStorageService;
import com.mst.matt.marketservice.service.SymbolNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;

/**
 * Marketstack OHLCV provider — STOCK only.
 *
 * <p>Free tier: 100 requests/month (limited). Uses {@code /v1/eod}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketstackOhlcvProvider implements OhlcvDataProvider {

    public static final String PROVIDER_NAME = "MARKETSTACK";
    private static final String BASE_URL     = "http://api.marketstack.com/v1";

    private final HttpJsonClient          http;
    private final MarketProviderProperties keys;
    private final OhlcvStorageService     storage;

    @Override public String providerName() { return PROVIDER_NAME; }

    @Override
    public List<AssetClass> supportedAssetClasses() {
        return List.of(AssetClass.STOCK);
    }

    @Override
    public List<NormalizedOhlcvBar> getHistoricalBars(String symbol,
                                                       AssetClass assetClass,
                                                       String interval,
                                                       int limit) {
        if (!keys.hasMarketstackKey()) return List.of();
        String sym = SymbolNormalizer.normalize(symbol);
        String url = BASE_URL + "/eod?access_key=" + keys.getMarketstackKey()
                + "&symbols=" + sym
                + "&limit=" + Math.min(limit, 1000);

        List<NormalizedOhlcvBar> bars = http.getJson(url)
                .flatMap(root -> MarketstackEodResponse.fromRoot(root, sym, interval, limit, PROVIDER_NAME))
                .map(MarketstackEodResponse::bars)
                .orElse(List.of());

        if (!bars.isEmpty()) writeThrough(sym, interval, bars);
        return bars;
    }

    @Override
    public List<NormalizedOhlcvBar> getHistoricalBars(String symbol,
                                                       AssetClass assetClass,
                                                       String interval,
                                                       Instant from,
                                                       Instant to) {
        if (!keys.hasMarketstackKey()) return List.of();
        String sym = SymbolNormalizer.normalize(symbol);
        String dateFrom = from.atZone(ZoneOffset.UTC).toLocalDate().toString();
        String dateTo   = to.atZone(ZoneOffset.UTC).toLocalDate().toString();
        String url = BASE_URL + "/eod?access_key=" + keys.getMarketstackKey()
                + "&symbols=" + sym
                + "&date_from=" + dateFrom
                + "&date_to="   + dateTo
                + "&limit=1000";

        List<NormalizedOhlcvBar> bars = http.getJson(url)
                .flatMap(root -> MarketstackEodResponse.fromRoot(root, sym, interval, 1000, PROVIDER_NAME))
                .map(MarketstackEodResponse::bars)
                .orElse(List.of());

        if (!bars.isEmpty()) writeThrough(sym, interval, bars);
        return bars;
    }

    @Override
    public Stream<NormalizedOhlcvBar> streamLiveBars(String symbol, AssetClass assetClass, String interval) {
        throw new UnsupportedOperationException("Marketstack does not support streaming");
    }

    @Override
    public boolean supportsStreaming(AssetClass assetClass) { return false; }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void writeThrough(String symbol, String interval, List<NormalizedOhlcvBar> bars) {
        try {
            List<com.mst.matt.marketservice.model.OhlcvBar> domainBars = bars.stream()
                    .map(b -> com.mst.matt.marketservice.model.OhlcvBar.builder()
                            .symbol(symbol).timeframe(interval)
                            .openTime(java.time.LocalDateTime.ofInstant(b.getOpenTime(), ZoneOffset.UTC))
                            .open(b.getOpen()).high(b.getHigh()).low(b.getLow()).close(b.getClose())
                            .volume(b.getVolume()).build())
                    .toList();
            storage.saveOrUpdateBars(symbol, interval, domainBars);
        } catch (Exception ex) {
            log.warn("[Marketstack] write-through failed for {}/{}: {}", symbol, interval, ex.getMessage());
        }
    }
}
