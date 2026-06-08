package com.mst.matt.tradingplatformapp.service.price;

import com.google.gson.JsonObject;
import com.mst.matt.tradingplatformapp.config.MarketApiProperties;
import com.mst.matt.tradingplatformapp.model.OhlcvBar;
import com.mst.matt.tradingplatformapp.model.Trade.AssetType;
import com.mst.matt.tradingplatformapp.service.price.api.AlphaVantageGlobalQuote;
import com.mst.matt.tradingplatformapp.service.price.api.AlphaVantageTimeSeriesParser;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
public class AlphaVantagePriceService implements PriceService {

    /** Package-private and non-final so {@link AlphaVantagePriceServiceTest} can
     *  redirect to {@link okhttp3.mockwebserver.MockWebServer} via ReflectionTestUtils. */
    String baseUrl = "https://www.alphavantage.co/query";

    private final HttpJsonClient http;
    private final MarketApiProperties keys;

    /** Throttle key passed to {@link HttpJsonClient} (T-23). */
    private static final String THROTTLE_KEY = "alphavantage";

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

    @Override
    public List<OhlcvBar> getOhlcv(String symbol, String timeframe, int limit) {
        String sym = SymbolNormalizer.normalize(symbol);
        boolean intraday = !timeframe.equalsIgnoreCase("1d")
                && !timeframe.equalsIgnoreCase("1w");
        String function = intraday ? "TIME_SERIES_INTRADAY" : "TIME_SERIES_DAILY";
        StringBuilder url = new StringBuilder(baseUrl)
                .append("?function=").append(function)
                .append("&symbol=").append(sym)
                .append("&apikey=").append(keys.getAlphavantageKey())
                .append("&outputsize=compact");
        if (intraday) {
            url.append("&interval=").append(mapInterval(timeframe));
        }
        return http.getJson(url.toString(), null, THROTTLE_KEY)
                .map(root -> parseSeries(root, sym, timeframe, limit, intraday))
                .orElse(List.of());
    }

    private List<OhlcvBar> parseSeries(JsonObject root, String sym, String tf,
                                       int limit, boolean intraday) {
        return AlphaVantageTimeSeriesParser.parse(
                root, sym, tf, limit, intraday, assetType(sym));
    }

    @Override
    public boolean supports(String symbol) {
        return isEnabled() && !SymbolNormalizer.normalize(symbol).isEmpty();
    }

    @Override
    public String getProviderName() { return "Alpha Vantage"; }

    @Override
    public MarketDataProvider getProviderId() { return MarketDataProvider.ALPHA_VANTAGE; }

    private static String mapInterval(String tf) {
        return switch (tf.toLowerCase()) {
            case "1m" -> "1min";
            case "5m" -> "5min";
            case "15m" -> "15min";
            case "30m", "1h", "4h" -> "60min";
            default -> "5min";
        };
    }

    private static AssetType assetType(String s) {
        if (AssetClassDetector.isCrypto(s)) return AssetType.CRYPTO;
        if (AssetClassDetector.isForex(s)) return AssetType.FOREX;
        return AssetType.STOCK;
    }
}
