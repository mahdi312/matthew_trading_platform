package com.mst.matt.tradingplatformapp.service.price;

import com.mst.matt.tradingplatformapp.config.MarketApiProperties;
import org.springframework.stereotype.Service;

/**
 * exchangerate-api.com — free tier 1500 req/month, no card.
 *
 * Endpoint: {@code https://v6.exchangerate-api.com/v6/{KEY}/latest/{FROM}}
 * Returns: {@code conversion_rates: {USD: 1.0, EUR: 0.93, ...}}.
 *
 * Added for T-05.
 */
@Service
public class ExchangeRateApiForexService extends AbstractForexService {

    private static final String BASE = "https://v6.exchangerate-api.com/v6";
    private final MarketApiProperties keys;

    public ExchangeRateApiForexService(HttpJsonClient http, MarketApiProperties keys) {
        super(http);
        this.keys = keys;
    }

    @Override protected boolean hasCredentials() { return keys.hasExchangerateapiKey(); }

    @Override protected String latestRatesUrl(String from, String to) {
        return BASE + "/" + keys.getExchangerateapiKey() + "/latest/" + from;
    }

    @Override protected String ratesNode() { return "conversion_rates"; }

    @Override public String getProviderName() { return "ExchangeRate-API"; }
    @Override public MarketDataProvider getProviderId() { return MarketDataProvider.EXCHANGE_RATE_API; }
}
