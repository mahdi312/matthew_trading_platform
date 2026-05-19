package com.mst.matt.tradingplatformapp.service.price;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mst.matt.tradingplatformapp.config.MarketApiProperties;
import com.mst.matt.tradingplatformapp.model.OhlcvBar;
import com.mst.matt.tradingplatformapp.model.Trade.AssetType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class TwelveDataPriceService implements PriceService {

    private static final String BASE = "https://api.twelvedata.com";
    private static final DateTimeFormatter DT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final HttpJsonClient http;
    private final MarketApiProperties keys;

    public TwelveDataPriceService(HttpJsonClient http, MarketApiProperties keys) {
        this.http = http;
        this.keys = keys;
    }

    @Override
    public boolean isEnabled() { return keys.hasTwelvedataKey(); }

    @Override
    public Optional<PriceQuote> getQuote(String symbol) {
        String sym = formatSymbol(symbol);
        String url = BASE + "/quote?symbol=" + sym + "&apikey=" + keys.getTwelvedataKey();
        return http.getJson(url).flatMap(json -> {
            BigDecimal price = JsonParseUtil.asBigDecimal(json, "close");
            if (price.compareTo(BigDecimal.ZERO) == 0)
                price = JsonParseUtil.asBigDecimal(json, "price");
            if (price.compareTo(BigDecimal.ZERO) == 0) return Optional.empty();
            BigDecimal change = JsonParseUtil.asBigDecimal(json, "change");
            BigDecimal pct = JsonParseUtil.asBigDecimal(json, "percent_change");
            return Optional.of(PriceQuote.builder()
                    .symbol(SymbolNormalizer.normalize(symbol))
                    .assetName(json.has("name") ? json.get("name").getAsString() : sym)
                    .assetType(assetType(symbol))
                    .price(price)
                    .change24h(change)
                    .changePct24h(pct)
                    .timestamp(LocalDateTime.now())
                    .isUp(change.compareTo(BigDecimal.ZERO) >= 0)
                    .build());
        });
    }

    @Override
    public List<OhlcvBar> getOhlcv(String symbol, String timeframe, int limit) {
        String sym = formatSymbol(symbol);
        String interval = mapInterval(timeframe);
        String url = BASE + "/time_series?symbol=" + sym
                + "&interval=" + interval + "&outputsize=" + Math.min(limit, 5000)
                + "&apikey=" + keys.getTwelvedataKey();

        return http.getJson(url).map(json -> {
            JsonArray values = json.getAsJsonArray("values");
            if (values == null) return List.<OhlcvBar>of();
            List<OhlcvBar> bars = new ArrayList<>();
            String norm = SymbolNormalizer.normalize(symbol);
            for (int i = values.size() - 1; i >= 0 && bars.size() < limit; i--) {
                JsonObject v = values.get(i).getAsJsonObject();
                LocalDateTime openTime = LocalDateTime.parse(v.get("datetime").getAsString(), DT);
                bars.add(0, OhlcvBar.builder()
                        .symbol(norm)
                        .timeframe(timeframe)
                        .openTime(openTime)
                        .open(JsonParseUtil.asBigDecimal(v, "open"))
                        .high(JsonParseUtil.asBigDecimal(v, "high"))
                        .low(JsonParseUtil.asBigDecimal(v, "low"))
                        .close(JsonParseUtil.asBigDecimal(v, "close"))
                        .volume(JsonParseUtil.asBigDecimal(v, "volume"))
                        .assetType(assetType(symbol))
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
    public String getProviderName() { return "Twelve Data"; }

    @Override
    public MarketDataProvider getProviderId() { return MarketDataProvider.TWELVE_DATA; }

    private static String formatSymbol(String symbol) {
        String s = SymbolNormalizer.normalize(symbol);
        if (AssetClassDetector.isForex(s) && s.length() == 6)
            return s.substring(0, 3) + "/" + s.substring(3);
        if (AssetClassDetector.isCrypto(s) && s.endsWith("USDT"))
            return s.substring(0, s.length() - 4) + "/USDT";
        return s;
    }

    private static String mapInterval(String tf) {
        return switch (tf.toLowerCase()) {
            case "1m" -> "1min";
            case "5m" -> "5min";
            case "15m" -> "15min";
            case "1h" -> "1h";
            case "4h" -> "4h";
            case "1w" -> "1week";
            default -> "1day";
        };
    }

    private static AssetType assetType(String symbol) {
        String s = SymbolNormalizer.normalize(symbol);
        if (AssetClassDetector.isCrypto(s)) return AssetType.CRYPTO;
        if (AssetClassDetector.isForex(s)) return AssetType.FOREX;
        return AssetType.STOCK;
    }
}
