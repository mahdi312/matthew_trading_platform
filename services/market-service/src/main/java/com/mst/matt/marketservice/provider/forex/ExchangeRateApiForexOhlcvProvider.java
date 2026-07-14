package com.mst.matt.marketservice.provider.forex;

import com.mst.matt.marketservice.client.HttpJsonClient;
import com.mst.matt.marketservice.config.MarketProviderProperties;
import org.springframework.stereotype.Component;

/**
 * ExchangeRate-API — free tier 1500 req/month, no credit card required.
 *
 * <p>Endpoint: {@code https://v6.exchangerate-api.com/v6/{KEY}/latest/{FROM}}
 * Returns: {@code conversion_rates: {USD: 1.0, EUR: 0.93, ...}}.
 */
@Component
public class ExchangeRateApiForexOhlcvProvider extends AbstractForexOhlcvProvider {

    public static final String PROVIDER_NAME = "EXCHANGERATE_API";
    private static final String BASE = "https://v6.exchangerate-api.com/v6";

    private final MarketProviderProperties keys;

    public ExchangeRateApiForexOhlcvProvider(HttpJsonClient http, MarketProviderProperties keys) {
        super(http);
        this.keys = keys;
    }

    @Override public String providerName()      { return PROVIDER_NAME; }
    @Override protected boolean hasCredentials() { return keys.hasExchangerateapiKey(); }

    @Override
    protected String latestRatesUrl(String from, String to) {
        return BASE + "/" + keys.getExchangerateapiKey() + "/latest/" + from;
    }

    @Override protected String ratesNode() { return "conversion_rates"; }
}
