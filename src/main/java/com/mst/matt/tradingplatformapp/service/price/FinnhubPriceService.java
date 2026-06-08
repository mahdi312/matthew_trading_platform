package com.mst.matt.tradingplatformapp.service.price;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mst.matt.tradingplatformapp.config.MarketApiProperties;
import com.mst.matt.tradingplatformapp.model.OhlcvBar;
import com.mst.matt.tradingplatformapp.model.Trade.AssetType;
import com.mst.matt.tradingplatformapp.service.price.api.FinnhubQuoteResponse;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class FinnhubPriceService implements PriceService {

    /** Package-private and non-final so {@link FinnhubPriceServiceTest} can
     *  redirect to {@link okhttp3.mockwebserver.MockWebServer} via ReflectionTestUtils. */
    String baseUrl = "https://finnhub.io/api/v1";

    private final HttpJsonClient http;
    private final MarketApiProperties keys;

    /** Throttle key passed to {@link HttpJsonClient} (T-23). */
    private static final String THROTTLE_KEY = "finnhub";

    public FinnhubPriceService(HttpJsonClient http, MarketApiProperties keys) {
        this.http = http;
        this.keys = keys;
    }

    @PostConstruct
    void registerThrottle() {
        // Finnhub free tier: 60 requests per minute. See T-23.
        http.throttle(THROTTLE_KEY, 60, Duration.ofMinutes(1));
    }

    @Override
    public boolean isEnabled() { return keys.hasFinnhubKey(); }

    @Override
    public Optional<PriceQuote> getQuote(String symbol) {
        String sym = SymbolNormalizer.normalize(symbol);
        String url = baseUrl + "/quote?symbol=" + sym + "&token=" + keys.getFinnhubKey();
        return http.getJson(url, null, THROTTLE_KEY).flatMap(json ->
                FinnhubQuoteResponse.fromJson(json)
                        .map(q -> q.toPriceQuote(sym, assetType(sym))));
    }

    @Override
    public List<OhlcvBar> getOhlcv(String symbol, String timeframe, int limit) {
        String sym = SymbolNormalizer.normalize(symbol);
        long to = Instant.now().getEpochSecond();
        long from = to - estimateSeconds(limit, timeframe);
        String path = AssetClassDetector.isForex(sym) ? "/forex/candle"
                : AssetClassDetector.isCrypto(sym) ? "/crypto/candle" : "/stock/candle";
        String url = baseUrl + path + "?symbol=" + sym
                + "&resolution=" + mapResolution(timeframe)
                + "&from=" + from + "&to=" + to
                + "&token=" + keys.getFinnhubKey();

        return http.getJson(url, null, THROTTLE_KEY).map(json -> {
            if (!"ok".equals(json.has("s") ? json.get("s").getAsString() : ""))
                return List.<OhlcvBar>of();
            JsonArray t = json.getAsJsonArray("t");
            JsonArray o = json.getAsJsonArray("o");
            JsonArray h = json.getAsJsonArray("h");
            JsonArray l = json.getAsJsonArray("l");
            JsonArray c = json.getAsJsonArray("c");
            JsonArray v = json.getAsJsonArray("v");
            if (t == null) return List.<OhlcvBar>of();
            List<OhlcvBar> bars = new ArrayList<>();
            int start = Math.max(0, t.size() - limit);
            for (int i = start; i < t.size(); i++) {
                LocalDateTime openTime = LocalDateTime.ofInstant(
                        Instant.ofEpochSecond(t.get(i).getAsLong()), ZoneOffset.UTC);
                bars.add(OhlcvBar.builder()
                        .symbol(sym)
                        .timeframe(timeframe)
                        .openTime(openTime)
                        .open(JsonParseUtil.asBigDecimal(o.get(i)))
                        .high(JsonParseUtil.asBigDecimal(h.get(i)))
                        .low(JsonParseUtil.asBigDecimal(l.get(i)))
                        .close(JsonParseUtil.asBigDecimal(c.get(i)))
                        .volume(v != null ? JsonParseUtil.asBigDecimal(v.get(i)) : BigDecimal.ZERO)
                        .assetType(assetType(sym))
                        .build());
            }
            return bars;
        }).orElse(List.of());
    }

    @Override
    public boolean supports(String symbol) {
        return isEnabled() && !SymbolNormalizer.normalize(symbol).isEmpty();
    }

    @Override
    public String getProviderName() { return "Finnhub"; }

    @Override
    public MarketDataProvider getProviderId() { return MarketDataProvider.FINNHUB; }

    private static String mapResolution(String tf) {
        return switch (tf.toLowerCase()) {
            case "1m" -> "1";
            case "5m" -> "5";
            case "15m" -> "15";
            case "30m" -> "30";
            case "1h", "4h" -> "60";
            case "1w" -> "W";
            default -> "D";
        };
    }

    private static long estimateSeconds(int limit, String tf) {
        long barSec = switch (tf.toLowerCase()) {
            case "1m" -> 60L;
            case "5m" -> 300L;
            case "15m" -> 900L;
            case "1h" -> 3600L;
            case "4h" -> 14400L;
            case "1w" -> 604800L;
            default -> 86400L;
        };
        return barSec * (limit + 5);
    }

    private static AssetType assetType(String s) {
        if (AssetClassDetector.isCrypto(s)) return AssetType.CRYPTO;
        if (AssetClassDetector.isForex(s)) return AssetType.FOREX;
        return AssetType.STOCK;
    }
}
