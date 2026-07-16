package com.mst.matt.marketservice.provider.crypto;

import com.google.gson.JsonObject;
import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.dto.NormalizedOhlcvBar;
import com.mst.matt.contracts.provider.ohlcv.OhlcvDataProvider;
import com.mst.matt.marketservice.client.HttpJsonClient;
import com.mst.matt.marketservice.client.api.AlphaVantageGlobalQuote;
import com.mst.matt.marketservice.client.api.AlphaVantageTimeSeriesParser;
import com.mst.matt.marketservice.config.MarketProviderProperties;
import com.mst.matt.marketservice.service.AssetClassDetector;
import com.mst.matt.marketservice.service.OhlcvStorageService;
import com.mst.matt.marketservice.service.SymbolNormalizer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

/**
 * Alpha Vantage OHLCV provider — supports STOCK, CRYPTO, FOREX.
 *
 * <p>Free tier: 5 requests/minute (25 requests/day on the very lowest plan;
 * 500 req/day on the standard free tier). Registered throttle key: "alphavantage".
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlphaVantageOhlcvProvider implements OhlcvDataProvider {

    public static final String PROVIDER_NAME = "ALPHA_VANTAGE";
    private static final String THROTTLE_KEY = "alphavantage";
    private static final String BASE_URL     = "https://www.alphavantage.co/query";

    private final HttpJsonClient          http;
    private final MarketProviderProperties keys;
    private final OhlcvStorageService     storage;

    @PostConstruct
    void registerThrottle() {
        // Alpha Vantage free tier: 5 requests/minute
        http.throttle(THROTTLE_KEY, 5, Duration.ofMinutes(1));
    }

    @Override public String providerName() { return PROVIDER_NAME; }

    @Override
    public List<AssetClass> supportedAssetClasses() {
        return List.of(AssetClass.CRYPTO, AssetClass.STOCK, AssetClass.FOREX);
    }

    @Override
    public List<NormalizedOhlcvBar> getHistoricalBars(String symbol,
                                                       AssetClass assetClass,
                                                       String interval,
                                                       int limit) {
        if (!keys.hasAlphavantageKey()) return List.of();
        String sym = SymbolNormalizer.normalize(symbol);
        String function;
        String intervalParam = null;
        boolean intraday = false;

        switch (interval.toLowerCase()) {
            case "1m", "5m", "15m", "30m", "1h", "4h" -> {
                function = "TIME_SERIES_INTRADAY";
                intervalParam = mapInterval(interval);
                intraday = true;
            }
            case "1d" -> function = assetClass == AssetClass.CRYPTO
                    ? "DIGITAL_CURRENCY_DAILY"
                    : assetClass == AssetClass.FOREX ? "FX_DAILY" : "TIME_SERIES_DAILY";
            case "1w" -> function = assetClass == AssetClass.CRYPTO
                    ? "DIGITAL_CURRENCY_WEEKLY"
                    : assetClass == AssetClass.FOREX ? "FX_WEEKLY" : "TIME_SERIES_WEEKLY";
            case "1mo" -> function = assetClass == AssetClass.CRYPTO
                    ? "DIGITAL_CURRENCY_MONTHLY"
                    : assetClass == AssetClass.FOREX ? "FX_MONTHLY" : "TIME_SERIES_MONTHLY";
            default -> function = "TIME_SERIES_DAILY";
        }

        StringBuilder url = new StringBuilder(BASE_URL)
                .append("?function=").append(function)
                .append("&symbol=").append(sym)
                .append("&apikey=").append(keys.getAlphavantageKey())
                .append("&outputsize=compact");
        if (intervalParam != null) url.append("&interval=").append(intervalParam);

        final boolean isIntraday = intraday;
        List<NormalizedOhlcvBar> bars = http.getJson(url.toString(), null, THROTTLE_KEY)
                .map(root -> AlphaVantageTimeSeriesParser.parse(
                        root, sym, interval, limit, isIntraday, assetClass, PROVIDER_NAME))
                .orElse(List.of());

        if (!bars.isEmpty()) {
            writeThrough(sym, interval, bars);
        }
        return bars;
    }

    @Override
    public List<NormalizedOhlcvBar> getHistoricalBars(String symbol,
                                                       AssetClass assetClass,
                                                       String interval,
                                                       Instant from,
                                                       Instant to) {
        // Alpha Vantage compact mode returns the last 100 bars; no range filter in free tier
        return getHistoricalBars(symbol, assetClass, interval, 100);
    }

    @Override
    public Stream<NormalizedOhlcvBar> streamLiveBars(String symbol, AssetClass assetClass, String interval) {
        throw new UnsupportedOperationException("Alpha Vantage does not support streaming");
    }

    @Override
    public boolean supportsStreaming(AssetClass assetClass) { return false; }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void writeThrough(String symbol, String interval, List<NormalizedOhlcvBar> bars) {
        try {
            List<com.mst.matt.marketservice.model.OhlcvBar> domainBars = bars.stream()
                    .map(b -> toOhlcvBar(b, symbol, interval))
                    .toList();
            storage.saveOrUpdateBars(symbol, interval, domainBars);
        } catch (Exception ex) {
            log.warn("[AlphaVantage] write-through failed for {}/{}: {}", symbol, interval, ex.getMessage());
        }
    }

    private static com.mst.matt.marketservice.model.OhlcvBar toOhlcvBar(
            NormalizedOhlcvBar b, String symbol, String interval) {
        return com.mst.matt.marketservice.model.OhlcvBar.builder()
                .symbol(symbol)
                .timeframe(interval)
                .openTime(java.time.LocalDateTime.ofInstant(b.getOpenTime(),
                        java.time.ZoneOffset.UTC))
                .open(b.getOpen())
                .high(b.getHigh())
                .low(b.getLow())
                .close(b.getClose())
                .volume(b.getVolume())
                .build();
    }

    private static String mapInterval(String tf) {
        return switch (tf.toLowerCase()) {
            case "1m"       -> "1min";
            case "5m"       -> "5min";
            case "15m"      -> "15min";
            case "30m"      -> "30min";
            case "1h", "4h" -> "60min";
            default         -> "5min";
        };
    }
}
