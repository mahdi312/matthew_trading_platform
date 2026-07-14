package com.mst.matt.referencedataservice.provider.sentiment;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.dto.SentimentSnapshotDto;
import com.mst.matt.contracts.provider.sentiment.SentimentProvider;
import com.mst.matt.referencedataservice.client.JsonUtil;
import com.mst.matt.referencedataservice.client.RefDataHttpClient;
import com.mst.matt.referencedataservice.config.RefDataProviderProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * CoinMarketCap implementation of {@link SentimentProvider} — Crypto Fear &amp; Greed Index.
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code GET /v3/fear-and-greed/latest}     — current index value</li>
 *   <li>{@code GET /v3/fear-and-greed/historical} — last 7 days for trend</li>
 * </ul>
 *
 * <p>The CMC Fear &amp; Greed endpoints are publicly accessible at
 * {@code https://api.coinmarketcap.com/data-api/v3/fear-and-greed/latest}
 * without an API key, so this provider works even without a key.</p>
 *
 * <p>Rate limit: 30 req/min free tier — throttle key {@code "coinmarketcap"}.</p>
 */
@Component
public class CmcSentimentProvider implements SentimentProvider {

    public static final String PROVIDER_NAME  = "COINMARKETCAP";
    private static final String PUBLIC_BASE   = "https://api.coinmarketcap.com/data-api/v3";
    private static final String AUTH_BASE     = "https://pro-api.coinmarketcap.com";
    private static final String THROTTLE      = "coinmarketcap_ref";

    private final RefDataProviderProperties keys;
    private final RefDataHttpClient http;

    public CmcSentimentProvider(RefDataProviderProperties keys, RefDataHttpClient http) {
        this.keys = keys;
        this.http = http;
    }

    @PostConstruct
    void init() {
        http.throttle(THROTTLE, 30, Duration.ofMinutes(1));
    }

    @Override public String providerName() { return PROVIDER_NAME; }

    @Override
    public List<AssetClass> supportedAssetClasses() {
        return List.of(AssetClass.CRYPTO);
    }

    // ── SentimentProvider ─────────────────────────────────────────────────────

    @Override
    public Optional<SentimentSnapshotDto> getSentiment(String symbol, AssetClass assetClass) {
        // CMC F&G is market-wide, not per-symbol
        return Optional.of(getMarketSentimentIndex(AssetClass.CRYPTO));
    }

    @Override
    public List<SentimentSnapshotDto> getSentimentBatch(List<String> symbols, AssetClass assetClass) {
        SentimentSnapshotDto snap = getMarketSentimentIndex(AssetClass.CRYPTO);
        List<SentimentSnapshotDto> result = new ArrayList<>();
        for (String sym : symbols) {
            result.add(SentimentSnapshotDto.builder()
                    .symbol(sym)
                    .assetClass(AssetClass.CRYPTO)
                    .providerName(PROVIDER_NAME)
                    .snapshotAt(snap.getSnapshotAt())
                    .rawIndexValue(snap.getRawIndexValue())
                    .sentimentScore(snap.getSentimentScore())
                    .sentimentLabel(snap.getSentimentLabel())
                    .previousRawIndexValue(snap.getPreviousRawIndexValue())
                    .previousSentimentLabel(snap.getPreviousSentimentLabel())
                    .sentimentTrend7d(snap.getSentimentTrend7d())
                    .build());
        }
        return result;
    }

    @Override
    public SentimentSnapshotDto getMarketSentimentIndex(AssetClass assetClass) {
        // Fetch latest + historical for trend
        SentimentSnapshotDto.SentimentSnapshotDtoBuilder builder = SentimentSnapshotDto.builder()
                .assetClass(AssetClass.CRYPTO)
                .providerName(PROVIDER_NAME)
                .snapshotAt(Instant.now());

        // Latest
        Optional<JsonObject> latestOpt = http.getJson(
                PUBLIC_BASE + "/fear-and-greed/latest", null, THROTTLE);
        if (latestOpt.isPresent()) {
            JsonObject root = latestOpt.get();
            JsonObject data = root.has("data") ? root.getAsJsonObject("data") : root;
            int value = data.has("value") ? data.get("value").getAsInt() : 50;
            String label = JsonUtil.str(data, "value_classification");
            builder.rawIndexValue(value)
                   .sentimentScore(normaliseFearGreed(value))
                   .sentimentLabel(normaliseFearGreedLabel(label));
        }

        // Historical (last 7 days) for trend
        Optional<JsonObject> histOpt = http.getJson(
                PUBLIC_BASE + "/fear-and-greed/historical?limit=7", null, THROTTLE);
        if (histOpt.isPresent()) {
            JsonObject root = histOpt.get();
            JsonElement dataEl = root.has("data") ? root.get("data") : null;
            if (dataEl != null && dataEl.isJsonArray()) {
                JsonArray arr = dataEl.getAsJsonArray();
                List<Double> trend7d = new ArrayList<>();
                Integer prevRaw = null;
                String prevLabel = null;
                for (int i = arr.size() - 1; i >= 0; i--) {
                    JsonObject entry = arr.get(i).getAsJsonObject();
                    int v = entry.has("value") ? entry.get("value").getAsInt() : 50;
                    trend7d.add(normaliseFearGreed(v));
                    if (i == 1) { // second most recent = "previous"
                        prevRaw   = v;
                        prevLabel = normaliseFearGreedLabel(JsonUtil.str(entry, "value_classification"));
                    }
                }
                builder.sentimentTrend7d(trend7d)
                       .previousRawIndexValue(prevRaw)
                       .previousSentimentLabel(prevLabel);
            }
        }

        return builder.build();
    }

    @Override
    public List<SentimentSnapshotDto> getTrendingByVolume(AssetClass assetClass, int limit) {
        return List.of(); // CMC trending requires paid endpoint
    }

    @Override
    public List<SentimentSnapshotDto> getMostBullish(AssetClass assetClass, int limit) {
        return List.of();
    }

    @Override
    public List<SentimentSnapshotDto> getMostBearish(AssetClass assetClass, int limit) {
        return List.of();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Normalise 0-100 Fear & Greed index to [-1.0, +1.0] */
    private static double normaliseFearGreed(int value) {
        return (value - 50) / 50.0;
    }

    private static String normaliseFearGreedLabel(String raw) {
        if (raw == null) return "NEUTRAL";
        return switch (raw.toLowerCase().replace(" ", "_")) {
            case "extreme_fear"  -> "EXTREME_FEAR";
            case "fear"          -> "FEAR";
            case "greed"         -> "GREED";
            case "extreme_greed" -> "EXTREME_GREED";
            default              -> "NEUTRAL";
        };
    }
}
