package com.mst.matt.tradingplatformapp.service.price;

import com.mst.matt.tradingplatformapp.model.OhlcvBar;
import com.mst.matt.tradingplatformapp.model.Trade.AssetType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Shared scaffolding for the small army of REST forex providers added in T-05
 * (Fixer, FreeCurrencyAPI, Open Exchange Rates, ExchangeRate-API, CurrencyLayer).
 *
 * <p>All five expose a simple "give me latest rates for one base currency, get a JSON map
 * of quote→rate" endpoint, so subclasses only need to plug in:
 * <ul>
 *     <li>the URL builder for the {@code latest} endpoint,</li>
 *     <li>the JSON root key holding the rate map,</li>
 *     <li>whether the service is gated by an API key.</li>
 * </ul>
 *
 * <p>OHLCV is intentionally a no-op — these are spot-rate sources only. The router falls
 * through to Twelve Data / Alpha Vantage for forex candles.
 */
public abstract class AbstractForexService implements PriceService {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected static final Set<String> SUPPORTED_CURRENCIES = Set.of(
            "USD", "EUR", "GBP", "JPY", "CHF", "CAD", "AUD", "NZD", "SEK", "NOK",
            "DKK", "PLN", "CZK", "HUF", "BGN", "RON", "TRY", "CNY", "HKD",
            "SGD", "KRW", "BRL", "MXN", "ZAR", "INR", "RUB", "IDR", "PHP", "THB"
    );

    protected final HttpJsonClient http;

    protected AbstractForexService(HttpJsonClient http) {
        this.http = http;
    }

    /** True when this provider has been configured (e.g. API key present). */
    @Override
    public boolean isEnabled() {
        return hasCredentials();
    }

    protected abstract boolean hasCredentials();

    /** Build the URL that returns latest rates for {@code from} base. */
    protected abstract String latestRatesUrl(String from, String to);

    /** Root JSON key holding the rates map ({@code rates}, {@code data}, {@code quotes}, …). */
    protected abstract String ratesNode();

    /**
     * Some providers (CurrencyLayer free plan, OpenExchangeRates) only accept USD as base
     * and use keyed quotes like {@code USDEUR}.  Hook for those.
     */
    protected String formatQuoteKey(String from, String to) {
        return to;
    }

    @Override
    public Optional<PriceQuote> getQuote(String symbol) {
        if (!hasCredentials()) return Optional.empty();
        String[] pair = parsePair(symbol);
        if (pair == null) return Optional.empty();

        String from = pair[0];
        String to = pair[1];

        return http.getJson(latestRatesUrl(from, to))
                .flatMap(root -> {
                    if (!root.has(ratesNode())) return Optional.<PriceQuote>empty();
                    var rates = root.getAsJsonObject(ratesNode());
                    String key = formatQuoteKey(from, to);
                    if (!rates.has(key)) return Optional.<PriceQuote>empty();
                    BigDecimal rate = JsonParseUtil.asBigDecimal(rates, key);
                    if (rate.compareTo(BigDecimal.ZERO) == 0) return Optional.<PriceQuote>empty();
                    return Optional.of(PriceQuote.builder()
                            .symbol(from + "/" + to)
                            .assetName(from + "/" + to)
                            .assetType(AssetType.FOREX)
                            .price(rate)
                            .change24h(BigDecimal.ZERO)
                            .changePct24h(BigDecimal.ZERO)
                            .currency(to)
                            .exchange(getProviderName())
                            .timestamp(LocalDateTime.now())
                            .isUp(true)
                            .build());
                });
    }

    @Override
    public List<OhlcvBar> getOhlcv(String symbol, String timeframe, int limit) {
        // OHLCV not provided by these spot-rate APIs — router will fall through.
        return Collections.emptyList();
    }

    @Override
    public boolean supports(String symbol) {
        if (!hasCredentials()) return false;
        String[] pair = parsePair(symbol);
        return pair != null
                && SUPPORTED_CURRENCIES.contains(pair[0])
                && SUPPORTED_CURRENCIES.contains(pair[1]);
    }

    /** Parses EURUSD, EUR/USD, EUR-USD into [from, to]; returns null on bad input. */
    protected static String[] parsePair(String symbol) {
        if (symbol == null) return null;
        String s = SymbolNormalizer.forForex(symbol);
        if (s.length() != 6 || !s.chars().allMatch(Character::isLetter)) return null;
        return new String[]{ s.substring(0, 3), s.substring(3) };
    }
}
