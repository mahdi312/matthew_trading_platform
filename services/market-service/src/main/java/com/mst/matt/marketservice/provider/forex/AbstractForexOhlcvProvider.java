package com.mst.matt.marketservice.provider.forex;

import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.dto.NormalizedOhlcvBar;
import com.mst.matt.contracts.provider.ohlcv.OhlcvDataProvider;
import com.mst.matt.marketservice.client.HttpJsonClient;
import com.mst.matt.marketservice.client.JsonParseUtil;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Shared scaffolding for all REST forex-rate providers.
 *
 * <p>All forex subclasses (Fixer, FreeCurrencyAPI, OpenExchangeRates, ExchangeRate-API,
 * CurrencyLayer) expose a simple "give me latest rates" endpoint.
 * Subclasses only need to plug in:
 * <ul>
 *   <li>the URL builder for the {@code latest} endpoint,</li>
 *   <li>the JSON root key holding the rate map,</li>
 *   <li>whether the service requires an API key.</li>
 * </ul>
 *
 * <p>OHLCV is intentionally empty — these are spot-rate sources only.
 * The {@link com.mst.matt.marketservice.registry.MarketOhlcvProviderRegistry} falls
 * through to TwelveData / Alpha Vantage for forex candles.
 */
@Slf4j
public abstract class AbstractForexOhlcvProvider implements OhlcvDataProvider {

    protected static final Set<String> SUPPORTED_CURRENCIES = Set.of(
            "USD", "EUR", "GBP", "JPY", "CHF", "CAD", "AUD", "NZD", "SEK", "NOK",
            "DKK", "PLN", "CZK", "HUF", "BGN", "RON", "TRY", "CNY", "HKD",
            "SGD", "KRW", "BRL", "MXN", "ZAR", "INR", "RUB", "IDR", "PHP", "THB"
    );

    protected final HttpJsonClient http;

    protected AbstractForexOhlcvProvider(HttpJsonClient http) {
        this.http = http;
    }

    /** True when this provider has been configured (e.g. API key present). */
    protected abstract boolean hasCredentials();

    /** Build the URL that returns latest rates for {@code from} base currency. */
    protected abstract String latestRatesUrl(String from, String to);

    /** Root JSON key holding the rates map (e.g. "rates", "data", "quotes"). */
    protected abstract String ratesNode();

    /**
     * Some providers (CurrencyLayer free plan, OpenExchangeRates) only accept USD as base
     * and use keyed quotes like {@code USDEUR}. Override to handle those.
     */
    protected String formatQuoteKey(String from, String to) {
        return to;
    }

    @Override
    public List<AssetClass> supportedAssetClasses() {
        return List.of(AssetClass.FOREX);
    }

    /**
     * Returns an empty list — these are spot-rate providers only.
     * The registry falls through to TwelveData/AlphaVantage for actual OHLCV candles.
     */
    @Override
    public List<NormalizedOhlcvBar> getHistoricalBars(String symbol,
                                                       AssetClass assetClass,
                                                       String interval,
                                                       int limit) {
        return Collections.emptyList();
    }

    @Override
    public List<NormalizedOhlcvBar> getHistoricalBars(String symbol,
                                                       AssetClass assetClass,
                                                       String interval,
                                                       Instant from,
                                                       Instant to) {
        return Collections.emptyList();
    }

    @Override
    public Stream<NormalizedOhlcvBar> streamLiveBars(String symbol, AssetClass assetClass, String interval) {
        throw new UnsupportedOperationException(providerName() + " does not support streaming");
    }

    @Override
    public boolean supportsStreaming(AssetClass assetClass) { return false; }

    // ── Currency pair parsing ─────────────────────────────────────────────────

    /**
     * Parses a forex symbol into a [from, to] currency pair.
     *
     * <p>Accepts: "EURUSD", "EUR/USD", "EUR-USD".
     *
     * @return two-element array [from, to], or null if invalid/unsupported
     */
    protected String[] parsePair(String symbol) {
        if (symbol == null) return null;
        String s = symbol.toUpperCase().replace("/", "").replace("-", "").replace("_", "");
        if (s.length() != 6) return null;
        String from = s.substring(0, 3);
        String to   = s.substring(3, 6);
        if (!SUPPORTED_CURRENCIES.contains(from) || !SUPPORTED_CURRENCIES.contains(to)) return null;
        return new String[]{from, to};
    }

    /**
     * Helper for USD-based providers (OpenExchangeRates, CurrencyLayer):
     * computes the cross rate (from/to) via: (USD/to) / (USD/from).
     */
    protected BigDecimal usdBaseCrossRate(com.google.gson.JsonObject rates, String from, String to) {
        if (!rates.has(from) || !rates.has(to)) return null;
        BigDecimal usdFrom = JsonParseUtil.asBigDecimal(rates, from);
        BigDecimal usdTo   = JsonParseUtil.asBigDecimal(rates, to);
        if (usdFrom.signum() == 0) return null;
        return usdTo.divide(usdFrom, 8, RoundingMode.HALF_UP);
    }

    /**
     * Helper for EUR-based providers (Fixer.io free tier):
     * computes the cross rate (from/to) via: (EUR/to) / (EUR/from).
     */
    protected BigDecimal eurBaseCrossRate(com.google.gson.JsonObject rates, String from, String to) {
        if (!rates.has(from) || !rates.has(to)) return null;
        BigDecimal eurFrom = JsonParseUtil.asBigDecimal(rates, from);
        BigDecimal eurTo   = JsonParseUtil.asBigDecimal(rates, to);
        if (eurFrom.signum() == 0) return null;
        return eurTo.divide(eurFrom, 8, RoundingMode.HALF_UP);
    }

    /**
     * Convenience: fetch a single rate from the provider's rates node.
     */
    protected Optional<BigDecimal> fetchRate(String from, String to) {
        return http.getJson(latestRatesUrl(from, to)).flatMap(root -> {
            if (!root.has(ratesNode())) return Optional.empty();
            var rates = root.getAsJsonObject(ratesNode());
            String key = formatQuoteKey(from, to);
            if (!rates.has(key)) return Optional.empty();
            BigDecimal rate = JsonParseUtil.asBigDecimal(rates, key);
            return rate.signum() == 0 ? Optional.empty() : Optional.of(rate);
        });
    }
}
