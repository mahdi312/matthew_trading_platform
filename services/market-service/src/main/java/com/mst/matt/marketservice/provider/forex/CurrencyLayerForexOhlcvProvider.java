package com.mst.matt.marketservice.provider.forex;

import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.dto.NormalizedOhlcvBar;
import com.mst.matt.marketservice.client.HttpJsonClient;
import com.mst.matt.marketservice.config.MarketProviderProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * CurrencyLayer — free tier 100 req/month, USD base only, keyed quotes ({@code USDEUR}).
 *
 * <p>Endpoint: {@code http://api.currencylayer.com/live?access_key=KEY&currencies=EUR,GBP,...}
 * Returns: {@code quotes: {USDEUR: 0.93, USDGBP: 0.78, ...}}.
 * Cross rates resolved via {@code (USDTo) / (USDFrom)}.
 */
@Component
public class CurrencyLayerForexOhlcvProvider extends AbstractForexOhlcvProvider {

    public static final String PROVIDER_NAME = "CURRENCYLAYER";
    private static final String BASE = "http://api.currencylayer.com/live";

    private final MarketProviderProperties keys;

    public CurrencyLayerForexOhlcvProvider(HttpJsonClient http, MarketProviderProperties keys) {
        super(http);
        this.keys = keys;
    }

    @Override public String providerName()      { return PROVIDER_NAME; }
    @Override protected boolean hasCredentials() { return keys.hasCurrencylayerKey(); }

    @Override
    protected String latestRatesUrl(String from, String to) {
        return BASE
                + "?access_key=" + keys.getCurrencylayerKey()
                + "&source=USD&currencies=" + from + "," + to;
    }

    @Override protected String ratesNode() { return "quotes"; }

    /** CurrencyLayer uses keys like "USDEUR" rather than just "EUR". */
    @Override
    protected String formatQuoteKey(String from, String to) { return "USD" + to; }

    /** Override to compute cross rate via USD base. */
    @Override
    protected Optional<BigDecimal> fetchRate(String from, String to) {
        return http.getJson(latestRatesUrl(from, to)).flatMap(root -> {
            if (!root.has(ratesNode())) return Optional.empty();
            var quotes = root.getAsJsonObject(ratesNode());
            BigDecimal cross = usdBaseCrossRate(
                    convertUsdKeyedMap(quotes), from, to);
            return cross != null ? Optional.of(cross) : Optional.empty();
        });
    }

    /** Converts {"USDEUR": 0.9, "USDGBP": 0.8} → {"EUR": 0.9, "GBP": 0.8}. */
    private com.google.gson.JsonObject convertUsdKeyedMap(com.google.gson.JsonObject quotes) {
        com.google.gson.JsonObject result = new com.google.gson.JsonObject();
        for (var entry : quotes.entrySet()) {
            String key = entry.getKey();
            if (key.length() == 6 && key.startsWith("USD")) {
                result.add(key.substring(3), entry.getValue());
            }
        }
        return result;
    }

    @Override
    public List<NormalizedOhlcvBar> getHistoricalBars(String symbol, AssetClass assetClass,
                                                       String interval, int limit) {
        return List.of();
    }

    @Override
    public List<NormalizedOhlcvBar> getHistoricalBars(String symbol, AssetClass assetClass,
                                                       String interval, Instant from, Instant to) {
        return List.of();
    }
}
