package com.mst.matt.tradingplatformapp.service.price;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mst.matt.tradingplatformapp.config.MarketApiProperties;
import com.mst.matt.tradingplatformapp.model.OhlcvBar;
import com.mst.matt.tradingplatformapp.model.Trade.AssetType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class AlphaVantagePriceService implements PriceService {

    private static final String BASE = "https://www.alphavantage.co/query";

    private final HttpJsonClient http;
    private final MarketApiProperties keys;

    public AlphaVantagePriceService(HttpJsonClient http, MarketApiProperties keys) {
        this.http = http;
        this.keys = keys;
    }

    @Override
    public boolean isEnabled() { return keys.hasAlphavantageKey(); }

    @Override
    public Optional<PriceQuote> getQuote(String symbol) {
        String sym = SymbolNormalizer.normalize(symbol);
        String url = BASE + "?function=GLOBAL_QUOTE&symbol=" + sym
                + "&apikey=" + keys.getAlphavantageKey();
        return http.getJson(url).flatMap(root -> {
            JsonObject q = root.getAsJsonObject("Global Quote");
            if (q == null) return Optional.empty();
            BigDecimal price = JsonParseUtil.asBigDecimal(q, "05. price");
            if (price.compareTo(BigDecimal.ZERO) == 0) return Optional.empty();
            BigDecimal change = JsonParseUtil.asBigDecimal(q, "09. change");
            BigDecimal changePct = JsonParseUtil.asBigDecimal(q, "10. change percent");
            return Optional.of(PriceQuote.builder()
                    .symbol(sym)
                    .assetName(sym)
                    .assetType(assetType(sym))
                    .price(price)
                    .change24h(change)
                    .changePct24h(changePct)
                    .volume24h(JsonParseUtil.asBigDecimal(q, "06. volume"))
                    .timestamp(LocalDateTime.now())
                    .isUp(change.compareTo(BigDecimal.ZERO) >= 0)
                    .build());
        });
    }

    @Override
    public List<OhlcvBar> getOhlcv(String symbol, String timeframe, int limit) {
        String sym = SymbolNormalizer.normalize(symbol);
        boolean intraday = !timeframe.equalsIgnoreCase("1d")
                && !timeframe.equalsIgnoreCase("1w");
        String function = intraday ? "TIME_SERIES_INTRADAY" : "TIME_SERIES_DAILY";
        StringBuilder url = new StringBuilder(BASE)
                .append("?function=").append(function)
                .append("&symbol=").append(sym)
                .append("&apikey=").append(keys.getAlphavantageKey())
                .append("&outputsize=compact");
        if (intraday) {
            url.append("&interval=").append(mapInterval(timeframe));
        }
        return http.getJson(url.toString())
                .map(root -> parseSeries(root, sym, timeframe, limit, intraday))
                .orElse(List.of());
    }

    private List<OhlcvBar> parseSeries(JsonObject root, String sym, String tf,
                                       int limit, boolean intraday) {
        String seriesKey = root.keySet().stream()
                .filter(k -> k.startsWith("Time Series"))
                .findFirst().orElse(null);
        if (seriesKey == null) return List.of();
        JsonObject series = root.getAsJsonObject(seriesKey);
        if (series == null) return List.of();

        List<Map.Entry<String, JsonElement>> entries = new ArrayList<>(series.entrySet());
        entries.sort(Comparator.comparing(Map.Entry::getKey));
        int start = Math.max(0, entries.size() - limit);
        List<OhlcvBar> bars = new ArrayList<>();
        DateTimeFormatter intraFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        DateTimeFormatter dayFmt = DateTimeFormatter.ISO_LOCAL_DATE;

        for (int i = start; i < entries.size(); i++) {
            var e = entries.get(i);
            JsonObject bar = e.getValue().getAsJsonObject();
            LocalDateTime openTime = intraday
                    ? LocalDateTime.parse(e.getKey(), intraFmt)
                    : LocalDate.parse(e.getKey(), dayFmt).atStartOfDay();
            bars.add(OhlcvBar.builder()
                    .symbol(sym)
                    .timeframe(tf)
                    .openTime(openTime)
                    .open(JsonParseUtil.asBigDecimal(bar, "1. open"))
                    .high(JsonParseUtil.asBigDecimal(bar, "2. high"))
                    .low(JsonParseUtil.asBigDecimal(bar, "3. low"))
                    .close(JsonParseUtil.asBigDecimal(bar, "4. close"))
                    .volume(JsonParseUtil.asBigDecimal(bar, "5. volume"))
                    .assetType(assetType(sym))
                    .build());
        }
        return bars;
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
