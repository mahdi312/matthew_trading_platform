package com.mst.matt.marketservice.provider.forex;

import com.mst.matt.contracts.provider.dto.NormalizedOhlcvBar;
import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.marketservice.client.HttpJsonClient;
import com.mst.matt.marketservice.config.MarketProviderProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Fixer.io forex provider — free tier 100 req/month, EUR base only.
 *
 * <p>Free plans are locked to EUR base; cross-rates are computed client-side:
 * {@code from/to = (EUR/to) / (EUR/from)}.
 */
@Component
public class FixerForexOhlcvProvider extends AbstractForexOhlcvProvider {

    public static final String PROVIDER_NAME = "FIXER";
    private static final String BASE = "http://data.fixer.io/api/latest";

    private final MarketProviderProperties keys;

    public FixerForexOhlcvProvider(HttpJsonClient http, MarketProviderProperties keys) {
        super(http);
        this.keys = keys;
    }

    @Override public String providerName() { return PROVIDER_NAME; }
    @Override protected boolean hasCredentials() { return keys.hasFixerioKey(); }

    @Override
    protected String latestRatesUrl(String from, String to) {
        return BASE + "?access_key=" + keys.getFixerioKey()
                + "&base=EUR&symbols=" + from + "," + to;
    }

    @Override protected String ratesNode() { return "rates"; }

    /**
     * Overrides the base to handle EUR-fixed base cross-rate computation.
     */
    @Override
    public List<NormalizedOhlcvBar> getHistoricalBars(String symbol,
                                                       AssetClass assetClass,
                                                       String interval,
                                                       int limit) {
        // Fixer free tier does not have historical data — spot rates only
        return List.of();
    }

    @Override
    public List<NormalizedOhlcvBar> getHistoricalBars(String symbol,
                                                       AssetClass assetClass,
                                                       String interval,
                                                       Instant from,
                                                       Instant to) {
        return List.of();
    }

    /** Computes EUR-base cross-rate; overrides default single-key lookup. */
    @Override
    protected Optional<BigDecimal> fetchRate(String from, String to) {
        return http.getJson(latestRatesUrl(from, to)).flatMap(root -> {
            if (!root.has(ratesNode())) return Optional.empty();
            var rates = root.getAsJsonObject(ratesNode());
            BigDecimal cross = eurBaseCrossRate(rates, from, to);
            return cross != null ? Optional.of(cross) : Optional.empty();
        });
    }
}
