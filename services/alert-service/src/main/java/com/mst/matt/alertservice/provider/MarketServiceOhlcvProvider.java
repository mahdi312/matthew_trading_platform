package com.mst.matt.alertservice.provider;

import com.mst.matt.alertservice.client.MarketDataClient;
import com.mst.matt.alertservice.client.MarketDataClient.OhlcvBarResponse;
import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.dto.NormalizedOhlcvBar;
import com.mst.matt.contracts.provider.ohlcv.OhlcvDataProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * {@link OhlcvDataProvider} adapter that delegates to {@code market-service}
 * via Feign — registered ahead of {@code NoOpOhlcvDataProvider} so alert
 * evaluation uses real latest closes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketServiceOhlcvProvider implements OhlcvDataProvider {

    static final String PROVIDER_NAME = "MARKET_SERVICE";

    private final MarketDataClient marketDataClient;

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    @Override
    public List<AssetClass> supportedAssetClasses() {
        return Arrays.asList(AssetClass.values());
    }

    @Override
    public List<NormalizedOhlcvBar> getHistoricalBars(String symbol,
                                                      AssetClass assetClass,
                                                      String interval,
                                                      int limit) {
        try {
            List<OhlcvBarResponse> raw = marketDataClient.getOhlcv(symbol, interval, limit);
            if (raw == null || raw.isEmpty()) {
                return List.of();
            }
            return raw.stream()
                    .map(b -> toNormalized(b, assetClass, interval))
                    .toList();
        } catch (Exception e) {
            log.warn("MarketServiceOhlcvProvider: failed to fetch {}/{} — {}",
                    symbol, interval, e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<NormalizedOhlcvBar> getHistoricalBars(String symbol,
                                                      AssetClass assetClass,
                                                      String interval,
                                                      Instant from,
                                                      Instant to) {
        return getHistoricalBars(symbol, assetClass, interval, 1000);
    }

    @Override
    public Stream<NormalizedOhlcvBar> streamLiveBars(String symbol,
                                                     AssetClass assetClass,
                                                     String interval) {
        throw new UnsupportedOperationException(
                "MarketServiceOhlcvProvider does not support live streaming");
    }

    @Override
    public boolean supportsStreaming(AssetClass assetClass) {
        return false;
    }

    private static NormalizedOhlcvBar toNormalized(OhlcvBarResponse b,
                                                   AssetClass assetClass,
                                                   String interval) {
        Instant openInstant = b.openTime() != null
                ? b.openTime().toInstant(ZoneOffset.UTC)
                : null;

        return NormalizedOhlcvBar.builder()
                .symbol(b.symbol())
                .assetClass(assetClass)
                .providerName(b.provider() != null ? b.provider() : PROVIDER_NAME)
                .openTime(openInstant)
                .closeTime(null)
                .interval(interval)
                .open(b.open())
                .high(b.high())
                .low(b.low())
                .close(b.close())
                .volume(b.volume())
                .quoteVolume(null)
                .tradeCount(null)
                .isLive(false)
                .isSynthetic(false)
                .build();
    }
}
