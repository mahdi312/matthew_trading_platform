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
 * Open Exchange Rates — free tier 1000 req/month, USD base only.
 *
 * <p>Endpoint: {@code https://openexchangerates.org/api/latest.json?app_id=KEY}
 * Returns: {@code rates: {EUR: 0.93, GBP: 0.78, ...}} (always USD-based).
 * Cross rates resolved via {@code (USD/to) / (USD/from)}.
 */
@Component
public class OpenExchangeRatesForexOhlcvProvider extends AbstractForexOhlcvProvider {

    public static final String PROVIDER_NAME = "OPEN_EXCHANGE_RATES";
    private static final String BASE = "https://openexchangerates.org/api/latest.json";

    private final MarketProviderProperties keys;

    public OpenExchangeRatesForexOhlcvProvider(HttpJsonClient http, MarketProviderProperties keys) {
        super(http);
        this.keys = keys;
    }

    @Override public String providerName()      { return PROVIDER_NAME; }
    @Override protected boolean hasCredentials() { return keys.hasOpenexchangeratesKey(); }

    @Override
    protected String latestRatesUrl(String from, String to) {
        // Free plan is USD-base; fetch the whole map and compute cross client-side
        return BASE + "?app_id=" + keys.getOpenexchangeratesKey();
    }

    @Override protected String ratesNode() { return "rates"; }

    /** Override to compute USD-base cross-rate. */
    @Override
    protected Optional<BigDecimal> fetchRate(String from, String to) {
        return http.getJson(latestRatesUrl(from, to)).flatMap(root -> {
            if (!root.has(ratesNode())) return Optional.empty();
            var rates = root.getAsJsonObject(ratesNode());
            BigDecimal cross = usdBaseCrossRate(rates, from, to);
            return cross != null ? Optional.of(cross) : Optional.empty();
        });
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
