package com.mst.matt.tradingplatformapp.service.price;

import com.mst.matt.tradingplatformapp.config.MarketApiProperties;
import org.springframework.stereotype.Service;

/**
 * freecurrencyapi.com — free tier 5000 req/month.
 *
 * Endpoint: {@code https://api.freecurrencyapi.com/v1/latest?apikey=KEY&base_currency=FROM&currencies=TO}
 * Returns: {@code data: {EUR: 0.93, GBP: 0.78, ...}}.
 *
 * Added for T-05.
 */
@Service
public class FreeCurrencyApiForexService extends AbstractForexService {

    private static final String BASE = "https://api.freecurrencyapi.com/v1/latest";
    private final MarketApiProperties keys;

    public FreeCurrencyApiForexService(HttpJsonClient http, MarketApiProperties keys) {
        super(http);
        this.keys = keys;
    }

    @Override protected boolean hasCredentials() { return keys.hasFreecurrencyapiKey(); }

    @Override protected String latestRatesUrl(String from, String to) {
        return BASE
                + "?apikey=" + keys.getFreecurrencyapiKey()
                + "&base_currency=" + from
                + "&currencies=" + to;
    }

    @Override protected String ratesNode() { return "data"; }

    @Override public String getProviderName() { return "FreeCurrencyAPI"; }
    @Override public MarketDataProvider getProviderId() { return MarketDataProvider.FREE_CURRENCY_API; }
}
