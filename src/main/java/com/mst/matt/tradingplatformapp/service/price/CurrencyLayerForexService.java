package com.mst.matt.tradingplatformapp.service.price;

import com.mst.matt.tradingplatformapp.config.MarketApiProperties;
import org.springframework.stereotype.Service;

/**
 * currencylayer.com — free tier 100 req/month, USD source only, keyed quotes ({@code USDEUR}).
 *
 * Endpoint: {@code http://api.currencylayer.com/live?access_key=KEY&currencies=EUR,GBP,...}
 * Returns: {@code quotes: {USDEUR: 0.93, USDGBP: 0.78, ...}}.
 *
 * Cross rates resolved via {@code (USDto) / (USDfrom)}.
 *
 * Added for T-05.
 */
@Service
public class CurrencyLayerForexService extends AbstractForexService {

    private static final String BASE = "http://api.currencylayer.com/live";
    private final MarketApiProperties keys;

    public CurrencyLayerForexService(HttpJsonClient http, MarketApiProperties keys) {
        super(http);
        this.keys = keys;
    }

    @Override protected boolean hasCredentials() { return keys.hasCurrencylayerKey(); }

    @Override protected String latestRatesUrl(String from, String to) {
        return BASE
                + "?access_key=" + keys.getCurrencylayerKey()
                + "&source=USD&currencies=" + from + "," + to;
    }

    @Override protected String ratesNode() { return "quotes"; }

    @Override protected String formatQuoteKey(String from, String to) {
        return "USD" + to;
    }

    @Override
    public java.util.Optional<PriceQuote> getQuote(String symbol) {
        if (!hasCredentials()) return java.util.Optional.empty();
        String[] pair = parsePair(symbol);
        if (pair == null) return java.util.Optional.empty();
        String from = pair[0];
        String to   = pair[1];

        return http.getJson(latestRatesUrl(from, to)).flatMap(root -> {
            if (!root.has(ratesNode())) return java.util.Optional.<PriceQuote>empty();
            var quotes = root.getAsJsonObject(ratesNode());
            java.math.BigDecimal cross = crossRateFromUsdQuotes(quotes, from, to);
            if (cross == null) return java.util.Optional.<PriceQuote>empty();
            return java.util.Optional.of(PriceQuote.builder()
                    .symbol(from + "/" + to)
                    .assetName(from + "/" + to)
                    .assetType(com.mst.matt.tradingplatformapp.model.Trade.AssetType.FOREX)
                    .price(cross)
                    .change24h(java.math.BigDecimal.ZERO)
                    .changePct24h(java.math.BigDecimal.ZERO)
                    .currency(to)
                    .exchange(getProviderName())
                    .timestamp(java.time.LocalDateTime.now())
                    .isUp(true)
                    .build());
        });
    }

    @Override public String getProviderName() { return "CurrencyLayer"; }
    @Override public MarketDataProvider getProviderId() { return MarketDataProvider.CURRENCY_LAYER; }

    /**
     * CurrencyLayer quotes are USD→X. Resolves any supported cross per report.html live-rates shape.
     */
    static java.math.BigDecimal crossRateFromUsdQuotes(
            com.google.gson.JsonObject quotes, String from, String to) {
        if ("USD".equals(from) && "USD".equals(to)) {
            return java.math.BigDecimal.ONE;
        }
        if ("USD".equals(from)) {
            String key = "USD" + to;
            if (!quotes.has(key)) return null;
            java.math.BigDecimal rate = JsonParseUtil.asBigDecimal(quotes, key);
            return rate.signum() == 0 ? null : rate;
        }
        if ("USD".equals(to)) {
            String key = "USD" + from;
            if (!quotes.has(key)) return null;
            java.math.BigDecimal usdFrom = JsonParseUtil.asBigDecimal(quotes, key);
            if (usdFrom.signum() == 0) return null;
            return java.math.BigDecimal.ONE.divide(usdFrom, 8, java.math.RoundingMode.HALF_UP);
        }
        String usdFromKey = "USD" + from;
        String usdToKey = "USD" + to;
        if (!quotes.has(usdFromKey) || !quotes.has(usdToKey)) return null;
        java.math.BigDecimal usdFrom = JsonParseUtil.asBigDecimal(quotes, usdFromKey);
        java.math.BigDecimal usdTo = JsonParseUtil.asBigDecimal(quotes, usdToKey);
        if (usdFrom.signum() == 0) return null;
        return usdTo.divide(usdFrom, 8, java.math.RoundingMode.HALF_UP);
    }
}
