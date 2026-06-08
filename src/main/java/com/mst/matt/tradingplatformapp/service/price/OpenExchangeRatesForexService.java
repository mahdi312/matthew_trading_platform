package com.mst.matt.tradingplatformapp.service.price;

import com.mst.matt.tradingplatformapp.config.MarketApiProperties;
import org.springframework.stereotype.Service;

/**
 * openexchangerates.org — free tier 1000 req/month, USD base only.
 *
 * Endpoint: {@code https://openexchangerates.org/api/latest.json?app_id=KEY}
 * Returns: {@code rates: {EUR: 0.93, GBP: 0.78, ...}} (always USD-based).
 *
 * Cross rates resolved via {@code (USD/to) / (USD/from)}.
 *
 * Added for T-05.
 */
@Service
public class OpenExchangeRatesForexService extends AbstractForexService {

    private static final String BASE = "https://openexchangerates.org/api/latest.json";
    private final MarketApiProperties keys;

    public OpenExchangeRatesForexService(HttpJsonClient http, MarketApiProperties keys) {
        super(http);
        this.keys = keys;
    }

    @Override protected boolean hasCredentials() { return keys.hasOpenexchangeratesKey(); }

    @Override protected String latestRatesUrl(String from, String to) {
        // Free plan is USD-base; we request the whole map and compute the cross client-side.
        return BASE + "?app_id=" + keys.getOpenexchangeratesKey();
    }

    @Override protected String ratesNode() { return "rates"; }

    @Override
    public java.util.Optional<PriceQuote> getQuote(String symbol) {
        if (!hasCredentials()) return java.util.Optional.empty();
        String[] pair = parsePair(symbol);
        if (pair == null) return java.util.Optional.empty();
        String from = pair[0];
        String to   = pair[1];

        return http.getJson(latestRatesUrl(from, to)).flatMap(root -> {
            if (!root.has(ratesNode())) return java.util.Optional.<PriceQuote>empty();
            var rates = root.getAsJsonObject(ratesNode());
            java.math.BigDecimal cross = usdBaseCrossRate(rates, from, to);
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

    @Override public String getProviderName() { return "Open Exchange Rates"; }
    @Override public MarketDataProvider getProviderId() { return MarketDataProvider.OPEN_EXCHANGE_RATES; }

    /** Open Exchange Rates map is always USD → foreign (report.html latest.json). */
    static java.math.BigDecimal usdBaseCrossRate(
            com.google.gson.JsonObject rates, String from, String to) {
        if ("USD".equals(from) && "USD".equals(to)) {
            return java.math.BigDecimal.ONE;
        }
        if ("USD".equals(from)) {
            if (!rates.has(to)) return null;
            java.math.BigDecimal rate = JsonParseUtil.asBigDecimal(rates, to);
            return rate.signum() == 0 ? null : rate;
        }
        if ("USD".equals(to)) {
            if (!rates.has(from)) return null;
            java.math.BigDecimal usdFrom = JsonParseUtil.asBigDecimal(rates, from);
            if (usdFrom.signum() == 0) return null;
            return java.math.BigDecimal.ONE.divide(usdFrom, 8, java.math.RoundingMode.HALF_UP);
        }
        if (!rates.has(from) || !rates.has(to)) return null;
        java.math.BigDecimal usdFrom = JsonParseUtil.asBigDecimal(rates, from);
        java.math.BigDecimal usdTo = JsonParseUtil.asBigDecimal(rates, to);
        if (usdFrom.signum() == 0) return null;
        return usdTo.divide(usdFrom, 8, java.math.RoundingMode.HALF_UP);
    }
}
