package com.mst.matt.tradingplatformapp.service.price;

import com.mst.matt.tradingplatformapp.config.MarketApiProperties;
import org.springframework.stereotype.Service;

/**
 * fixer.io — free tier 100 req/month, EUR base only.
 *
 * Endpoint: {@code https://data.fixer.io/api/latest?access_key=KEY&base=EUR&symbols=USD,GBP,...}
 * Returns: {@code rates: {USD: 1.07, GBP: 0.85, ...}}.
 *
 * Note: free plans are locked to EUR base — we resolve cross-rates by dividing two
 * EUR-quoted rates ({@code from/to = (EUR/to) / (EUR/from)}).
 *
 * Added for T-05.
 */
@Service
public class FixerForexService extends AbstractForexService {

    private static final String BASE = "http://data.fixer.io/api/latest";
    private final MarketApiProperties keys;

    public FixerForexService(HttpJsonClient http, MarketApiProperties keys) {
        super(http);
        this.keys = keys;
    }

    @Override protected boolean hasCredentials() { return keys.hasFixerioKey(); }

    @Override protected String latestRatesUrl(String from, String to) {
        // Free tier forces EUR base; request both legs and let getQuote compute the cross.
        return BASE
                + "?access_key=" + keys.getFixerioKey()
                + "&base=EUR&symbols=" + from + "," + to;
    }

    @Override protected String ratesNode() { return "rates"; }

    @Override
    public java.util.Optional<PriceQuote> getQuote(String symbol) {
        if (!hasCredentials()) return java.util.Optional.empty();
        String[] pair = parsePair(symbol);
        if (pair == null) return java.util.Optional.empty();

        String from = pair[0];
        String to = pair[1];

        return http.getJson(latestRatesUrl(from, to)).flatMap(root -> {
            if (!root.has(ratesNode())) return java.util.Optional.<PriceQuote>empty();
            var rates = root.getAsJsonObject(ratesNode());
            if (!rates.has(from) || !rates.has(to)) return java.util.Optional.<PriceQuote>empty();
            java.math.BigDecimal eurFrom = JsonParseUtil.asBigDecimal(rates, from);
            java.math.BigDecimal eurTo = JsonParseUtil.asBigDecimal(rates, to);
            if (eurFrom.signum() == 0) return java.util.Optional.<PriceQuote>empty();
            java.math.BigDecimal cross =
                    eurTo.divide(eurFrom, 8, java.math.RoundingMode.HALF_UP);
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

    @Override public String getProviderName() { return "Fixer.io"; }
    @Override public MarketDataProvider getProviderId() { return MarketDataProvider.FIXER; }
}
