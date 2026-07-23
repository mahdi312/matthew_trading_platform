package com.mst.matt.marketservice.provider.forex;

import com.mst.matt.marketservice.client.HttpJsonClient;
import com.mst.matt.marketservice.config.MarketProviderProperties;
import org.springframework.stereotype.Component;

/**
 * FreeCurrencyAPI.com — free tier 5000 req/month.
 *
 * <p>Endpoint: {@code https://api.freecurrencyapi.com/v1/latest?apikey=KEY&base_currency=FROM&currencies=TO}
 * Returns: {@code data: {EUR: 0.93, GBP: 0.78, ...}}.
 */
@Component
public class FreeCurrencyApiForexOhlcvProvider extends AbstractForexOhlcvProvider {

    public static final String PROVIDER_NAME = "FREECURRENCYAPI";
    private static final String BASE = "https://api.freecurrencyapi.com/v1/latest";

    private final MarketProviderProperties keys;

    public FreeCurrencyApiForexOhlcvProvider(HttpJsonClient http, MarketProviderProperties keys) {
        super(http);
        this.keys = keys;
    }

    @Override public String providerName()      { return PROVIDER_NAME; }
    @Override protected boolean hasCredentials() { return keys.hasFreecurrencyapiKey(); }

    @Override
    protected String latestRatesUrl(String from, String to) {
        return BASE
                + "?apikey=" + keys.getFreecurrencyapiKey()
                + "&base_currency=" + from
                + "&currencies=" + to;
    }

    @Override protected String ratesNode() { return "data"; }
}
