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
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * CoinGecko implementation of {@link SentimentProvider}.
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code GET /global}            — market-wide sentiment proxy via market cap change</li>
 *   <li>{@code GET /search/trending}   — trending coins by social volume (proxy for bullish)</li>
 *   <li>{@code GET /coins/{id}}        — per-coin community sentiment votes</li>
 * </ul>
 *
 * <p>No API key required for free tier. Rate limited to ~10-50 req/min depending on endpoint.</p>
 */
@Component
public class CoinGeckoSentimentProvider implements SentimentProvider {

    public static final String PROVIDER_NAME = "COINGECKO";
    private static final String BASE_URL     = "https://api.coingecko.com/api/v3";
    private static final String THROTTLE     = "coingecko_ref";

    // Static symbol → CoinGecko ID map for common coins
    private static final java.util.Map<String, String> COIN_IDS = java.util.Map.ofEntries(
            java.util.Map.entry("BTC",  "bitcoin"),
            java.util.Map.entry("ETH",  "ethereum"),
            java.util.Map.entry("BNB",  "binancecoin"),
            java.util.Map.entry("SOL",  "solana"),
            java.util.Map.entry("XRP",  "ripple"),
            java.util.Map.entry("USDT", "tether"),
            java.util.Map.entry("USDC", "usd-coin"),
            java.util.Map.entry("ADA",  "cardano"),
            java.util.Map.entry("DOGE", "dogecoin"),
            java.util.Map.entry("AVAX", "avalanche-2"),
            java.util.Map.entry("DOT",  "polkadot"),
            java.util.Map.entry("MATIC","matic-network"),
            java.util.Map.entry("LINK", "chainlink"),
            java.util.Map.entry("LTC",  "litecoin")
    );

    private final RefDataProviderProperties keys;
    private final RefDataHttpClient http;

    public CoinGeckoSentimentProvider(RefDataProviderProperties keys, RefDataHttpClient http) {
        this.keys = keys;
        this.http = http;
        http.throttle(THROTTLE, 10, java.time.Duration.ofMinutes(1));
    }

    @Override public String providerName() { return PROVIDER_NAME; }

    @Override
    public List<AssetClass> supportedAssetClasses() {
        return List.of(AssetClass.CRYPTO);
    }

    // ── SentimentProvider ─────────────────────────────────────────────────────

    @Override
    public Optional<SentimentSnapshotDto> getSentiment(String symbol, AssetClass assetClass) {
        String coinId = resolveCoinId(symbol);
        if (coinId == null) return Optional.empty();

        String url = buildUrl("/coins/" + coinId)
                + "?localization=false&tickers=false&market_data=false"
                + "&community_data=true&developer_data=false";

        return http.getJson(url, null, THROTTLE).map(root -> {
            JsonObject sentiment = root.has("sentiment_votes_up_percentage")
                    ? root : null;
            Double bullishRatio = null;
            if (root.has("sentiment_votes_up_percentage")
                    && !root.get("sentiment_votes_up_percentage").isJsonNull()) {
                bullishRatio = root.get("sentiment_votes_up_percentage").getAsDouble() / 100.0;
            }
            Double bearishRatio = bullishRatio != null ? 1.0 - bullishRatio : null;

            // Derive a score from community sentiment: bullish% → [0,1] → [-1,+1]
            Double score = bullishRatio != null ? (bullishRatio * 2.0 - 1.0) : null;
            String label = score != null ? (score > 0.2 ? "GREED"
                    : score < -0.2 ? "FEAR" : "NEUTRAL") : "NEUTRAL";

            return SentimentSnapshotDto.builder()
                    .symbol(symbol.toUpperCase())
                    .assetClass(AssetClass.CRYPTO)
                    .providerName(PROVIDER_NAME)
                    .snapshotAt(Instant.now())
                    .sentimentScore(score)
                    .sentimentLabel(label)
                    .bullishRatio(bullishRatio)
                    .bearishRatio(bearishRatio)
                    .build();
        });
    }

    @Override
    public List<SentimentSnapshotDto> getSentimentBatch(List<String> symbols, AssetClass assetClass) {
        List<SentimentSnapshotDto> result = new ArrayList<>();
        for (String sym : symbols) {
            getSentiment(sym, assetClass).ifPresent(result::add);
        }
        return result;
    }

    @Override
    public SentimentSnapshotDto getMarketSentimentIndex(AssetClass assetClass) {
        // Use /global — market_cap_change_percentage_24h_usd as sentiment proxy
        Optional<JsonObject> globalOpt = http.getJson(buildUrl("/global"), null, THROTTLE);
        Double score = null;
        String label = "NEUTRAL";

        if (globalOpt.isPresent()) {
            JsonObject root = globalOpt.get();
            JsonObject data = root.has("data") ? root.getAsJsonObject("data") : root;
            if (data.has("market_cap_change_percentage_24h_usd")
                    && !data.get("market_cap_change_percentage_24h_usd").isJsonNull()) {
                double pct = data.get("market_cap_change_percentage_24h_usd").getAsDouble();
                // Clamp pct [-10, +10] → [-1, +1]
                score = Math.max(-1.0, Math.min(1.0, pct / 10.0));
                label = score > 0.3 ? "GREED" : score < -0.3 ? "FEAR" : "NEUTRAL";
            }
        }

        return SentimentSnapshotDto.builder()
                .assetClass(AssetClass.CRYPTO)
                .providerName(PROVIDER_NAME)
                .snapshotAt(Instant.now())
                .sentimentScore(score)
                .sentimentLabel(label)
                .build();
    }

    @Override
    public List<SentimentSnapshotDto> getTrendingByVolume(AssetClass assetClass, int limit) {
        String url = buildUrl("/search/trending");
        return http.getJson(url, null, THROTTLE).map(root -> {
            List<SentimentSnapshotDto> result = new ArrayList<>();
            JsonArray coins = root.has("coins") ? root.getAsJsonArray("coins") : new JsonArray();
            for (JsonElement el : coins) {
                if (!el.isJsonObject()) continue;
                JsonObject item = el.getAsJsonObject();
                JsonObject coin = item.has("item") ? item.getAsJsonObject("item") : item;
                String sym = JsonUtil.str(coin, "symbol");
                if (sym == null) continue;
                Integer rank = JsonUtil.integer(coin, "market_cap_rank");
                result.add(SentimentSnapshotDto.builder()
                        .symbol(sym.toUpperCase())
                        .assetClass(AssetClass.CRYPTO)
                        .providerName(PROVIDER_NAME)
                        .snapshotAt(Instant.now())
                        .sentimentScore(0.5) // trending = mildly bullish proxy
                        .sentimentLabel("GREED")
                        .build());
                if (result.size() >= limit) break;
            }
            return (List<SentimentSnapshotDto>) result;
        }).orElse(List.of());
    }

    @Override
    public List<SentimentSnapshotDto> getMostBullish(AssetClass assetClass, int limit) {
        return getTrendingByVolume(assetClass, limit); // trending ≈ bullish on CoinGecko
    }

    @Override
    public List<SentimentSnapshotDto> getMostBearish(AssetClass assetClass, int limit) {
        return List.of(); // Not available without paid tier
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String resolveCoinId(String symbol) {
        String upper = symbol.toUpperCase()
                .replace("USDT", "").replace("USD", "").trim();
        return COIN_IDS.get(upper);
    }

    private String buildUrl(String path) {
        if (keys.hasCoinGeckoKey()) {
            return "https://pro-api.coingecko.com/api/v3" + path
                    + "?x_cg_pro_api_key=" + keys.getCoingeckoKey();
        }
        return BASE_URL + path;
    }
}
