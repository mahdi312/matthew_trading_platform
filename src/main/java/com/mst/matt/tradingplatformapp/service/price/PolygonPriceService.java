package com.mst.matt.tradingplatformapp.service.price;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mst.matt.tradingplatformapp.config.MarketApiProperties;
import com.mst.matt.tradingplatformapp.model.OhlcvBar;
import com.mst.matt.tradingplatformapp.model.Trade.AssetType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class PolygonPriceService implements PriceService {

    /** Package-private and non-final so {@link PolygonPriceServiceTest} can
     *  redirect to {@link okhttp3.mockwebserver.MockWebServer} via ReflectionTestUtils. */
    String baseUrl = "https://api.polygon.io";

    private final HttpJsonClient http;
    private final MarketApiProperties keys;

    public PolygonPriceService(HttpJsonClient http, MarketApiProperties keys) {
        this.http = http;
        this.keys = keys;
    }

    @Override
    public boolean isEnabled() { return keys.hasPolygonKey(); }

    @Override
    public Optional<PriceQuote> getQuote(String symbol) {
        String sym = SymbolNormalizer.normalize(symbol);
        String url = baseUrl + "/v2/snapshot/locale/us/markets/stocks/tickers/"
                + sym + "?apiKey=" + keys.getPolygonKey();
        return http.getJson(url).flatMap(root -> {
            JsonObject ticker = root.getAsJsonObject("ticker");
            if (ticker == null) return Optional.empty();
            JsonObject day = ticker.getAsJsonObject("day");
            JsonObject prev = ticker.getAsJsonObject("prevDay");
            if (day == null) return Optional.empty();
            BigDecimal price = JsonParseUtil.asBigDecimal(day, "c");
            if (price.compareTo(BigDecimal.ZERO) == 0) return Optional.empty();
            BigDecimal prevClose = prev != null ? JsonParseUtil.asBigDecimal(prev, "c") : price;
            BigDecimal change = price.subtract(prevClose);
            return Optional.of(PriceQuote.builder()
                    .symbol(sym)
                    .assetName(sym)
                    .assetType(AssetType.STOCK)
                    .price(price)
                    .open24h(JsonParseUtil.asBigDecimal(day, "o"))
                    .high24h(JsonParseUtil.asBigDecimal(day, "h"))
                    .low24h(JsonParseUtil.asBigDecimal(day, "l"))
                    .change24h(change)
                    .volume24h(JsonParseUtil.asBigDecimal(day, "v"))
                    .timestamp(LocalDateTime.now())
                    .isUp(change.compareTo(BigDecimal.ZERO) >= 0)
                    .build());
        });
    }

    @Override
    public List<OhlcvBar> getOhlcv(String symbol, String timeframe, int limit) {
        String sym = SymbolNormalizer.normalize(symbol);
        var span = mapSpan(timeframe);
        long to = Instant.now().toEpochMilli();
        long from = Instant.now().minus(estimateDays(limit, timeframe), ChronoUnit.DAYS).toEpochMilli();
        String url = String.format(
                baseUrl + "/v2/aggs/ticker/%s/range/%d/%s/%d/%d?adjusted=true&sort=asc&limit=%d&apiKey=%s",
                sym, span.multiplier(), span.timespan(), from, to, limit, keys.getPolygonKey());

        return http.getJson(url).map(root -> {
            JsonArray results = root.getAsJsonArray("results");
            if (results == null) return List.<OhlcvBar>of();
            List<OhlcvBar> bars = new ArrayList<>();
            for (var el : results) {
                JsonObject r = el.getAsJsonObject();
                long ms = r.get("t").getAsLong();
                bars.add(OhlcvBar.builder()
                        .symbol(sym)
                        .timeframe(timeframe)
                        .openTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneOffset.UTC))
                        .open(JsonParseUtil.asBigDecimal(r, "o"))
                        .high(JsonParseUtil.asBigDecimal(r, "h"))
                        .low(JsonParseUtil.asBigDecimal(r, "l"))
                        .close(JsonParseUtil.asBigDecimal(r, "c"))
                        .volume(JsonParseUtil.asBigDecimal(r, "v"))
                        .assetType(AssetType.STOCK)
                        .build());
            }
            return bars;
        }).orElse(List.of());
    }

    @Override
    public boolean supports(String symbol) {
        String s = SymbolNormalizer.normalize(symbol);
        return isEnabled() && !AssetClassDetector.isCrypto(s) && !AssetClassDetector.isForex(s);
    }

    @Override
    public String getProviderName() { return "Polygon.io"; }

    @Override
    public MarketDataProvider getProviderId() { return MarketDataProvider.POLYGON; }

    private record Span(int multiplier, String timespan) {}

    private static Span mapSpan(String tf) {
        return switch (tf.toLowerCase()) {
            case "1m" -> new Span(1, "minute");
            case "5m" -> new Span(5, "minute");
            case "15m" -> new Span(15, "minute");
            case "1h" -> new Span(1, "hour");
            case "4h" -> new Span(4, "hour");
            case "1w" -> new Span(1, "week");
            default -> new Span(1, "day");
        };
    }

    private static long estimateDays(int limit, String tf) {
        return switch (tf.toLowerCase()) {
            case "1m", "5m" -> Math.max(7, limit / 100);
            case "15m", "1h" -> Math.max(30, limit / 20);
            case "1w" -> limit * 7L;
            default -> Math.max(120, limit);
        };
    }
}
