package com.mst.matt.tradingplatformapp.service.price.api.alphavantage;

import com.mst.matt.tradingplatformapp.config.MarketApiProperties;
import com.mst.matt.tradingplatformapp.model.OhlcvBar;
import com.mst.matt.tradingplatformapp.model.Trade.AssetType;
import com.mst.matt.tradingplatformapp.service.price.*;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Alpha Vantage price-data service — free-tier integration.
 *
 * <h3>Free-tier OHLCV endpoints supported:</h3>
 * <ul>
 *   <li>{@code GLOBAL_QUOTE}                — real-time quote (OHLCV + change)</li>
 *   <li>{@code TIME_SERIES_INTRADAY}        — intraday OHLCV (1min/5min/15min/30min/60min)</li>
 *   <li>{@code TIME_SERIES_DAILY}           — daily OHLCV (up to 100 compact bars)</li>
 *   <li>{@code TIME_SERIES_DAILY_ADJUSTED}  — daily adjusted OHLCV</li>
 *   <li>{@code TIME_SERIES_WEEKLY}          — weekly OHLCV</li>
 *   <li>{@code TIME_SERIES_WEEKLY_ADJUSTED} — weekly adjusted OHLCV</li>
 *   <li>{@code TIME_SERIES_MONTHLY}         — monthly OHLCV</li>
 *   <li>{@code TIME_SERIES_MONTHLY_ADJUSTED}— monthly adjusted OHLCV</li>
 * </ul>
 *
 * <h3>Rate limiting:</h3>
 * The shared {@code "alphavantage"} throttle bucket enforces 5 req/min (free tier).
 * All services that call Alpha Vantage must pass {@code THROTTLE_KEY} to
 * {@link HttpJsonClient#getJson(String, String, String)}.
 */
@Service
public class AlphaVantagePriceService implements PriceService {

    /** Package-private and non-final so {@link AlphaVantagePriceServiceTest} can
     *  redirect to {@link okhttp3.mockwebserver.MockWebServer} via ReflectionTestUtils. */
    String baseUrl = "https://www.alphavantage.co/query";

    private final HttpJsonClient http;
    private final MarketApiProperties keys;

    /** Throttle key passed to {@link HttpJsonClient} (T-23). */
    static final String THROTTLE_KEY = "alphavantage";

    public AlphaVantagePriceService(HttpJsonClient http, MarketApiProperties keys) {
        this.http = http;
        this.keys = keys;
    }

    @PostConstruct
    void registerThrottle() {
        // Alpha Vantage free tier: 5 requests per minute. See T-23.
        http.throttle(THROTTLE_KEY, 5, Duration.ofMinutes(1));
    }

    @Override
    public boolean isEnabled() { return keys.hasAlphavantageKey(); }

    @Override
    public Optional<PriceQuote> getQuote(String symbol) {
        String sym = SymbolNormalizer.normalize(symbol);
        String url = baseUrl + "?function=GLOBAL_QUOTE&symbol=" + sym
                + "&apikey=" + keys.getAlphavantageKey();
        return http.getJson(url, null, THROTTLE_KEY).flatMap(root ->
                AlphaVantageGlobalQuote.fromRoot(root)
                        .map(q -> q.toPriceQuote(sym, assetType(sym))));
    }

    /**
     * Fetches OHLCV bars for the given symbol and timeframe.
     *
     * <p>Timeframe routing:
     * <ul>
     *   <li>{@code 1m, 5m, 15m, 30m, 1h, 4h} → TIME_SERIES_INTRADAY with appropriate interval</li>
     *   <li>{@code 1d}                         → TIME_SERIES_DAILY</li>
     *   <li>{@code 1w}                         → TIME_SERIES_WEEKLY</li>
     *   <li>{@code 1mo}                        → TIME_SERIES_MONTHLY</li>
     * </ul>
     */
    @Override
    public List<OhlcvBar> getOhlcv(String symbol, String timeframe, int limit) {
        String sym = SymbolNormalizer.normalize(symbol);
        String tf = timeframe.toLowerCase();

        String function;
        String intervalParam = null;
        boolean intraday = false;

        switch (tf) {
            case "1m", "5m", "15m", "30m", "1h", "4h" -> {
                function = "TIME_SERIES_INTRADAY";
                intervalParam = mapInterval(tf);
                intraday = true;
            }
            case "1d" -> function = "TIME_SERIES_DAILY";
            case "1w" -> function = "TIME_SERIES_WEEKLY";
            case "1mo", "1month" -> function = "TIME_SERIES_MONTHLY";
            default -> {
                // Fallback: intraday for unknown short intervals, daily for longer
                if (tf.endsWith("m") || tf.endsWith("h")) {
                    function = "TIME_SERIES_INTRADAY";
                    intervalParam = mapInterval(tf);
                    intraday = true;
                } else {
                    function = "TIME_SERIES_DAILY";
                }
            }
        }

        StringBuilder url = new StringBuilder(baseUrl)
                .append("?function=").append(function)
                .append("&symbol=").append(sym)
                .append("&apikey=").append(keys.getAlphavantageKey())
                .append("&outputsize=compact");
        if (intervalParam != null) {
            url.append("&interval=").append(intervalParam);
        }
        final boolean isIntraday = intraday;
        return http.getJson(url.toString(), null, THROTTLE_KEY)
                .map(root -> AlphaVantageTimeSeriesParser.parse(
                        root, sym, timeframe, limit, isIntraday, assetType(sym)))
                .orElse(List.of());
    }

    @Override
    public boolean supports(String symbol) {
        return isEnabled() && !SymbolNormalizer.normalize(symbol).isEmpty();
    }

    @Override
    public String getProviderName() { return "Alpha Vantage"; }

    @Override
    public MarketDataProvider getProviderId() { return MarketDataProvider.ALPHA_VANTAGE; }

    /**
     * Maps internal timeframe codes to Alpha Vantage {@code interval} values for
     * {@code TIME_SERIES_INTRADAY}.
     */
    static String mapInterval(String tf) {
        return switch (tf.toLowerCase()) {
            case "1m"  -> "1min";
            case "5m"  -> "5min";
            case "15m" -> "15min";
            case "30m" -> "30min";
            case "1h", "4h" -> "60min";
            default -> "5min";
        };
    }

    static AssetType assetType(String s) {
        if (AssetClassDetector.isCrypto(s)) return AssetType.CRYPTO;
        if (AssetClassDetector.isForex(s)) return AssetType.FOREX;
        return AssetType.STOCK;
    }

    // Package-private for use by AlphaVantageMarketService
    String getBaseUrl()             { return baseUrl; }
    MarketApiProperties getKeys()   { return keys; }
    HttpJsonClient getHttp()        { return http; }
}
