package com.mst.matt.tradingplatformapp.service.price;

import com.google.gson.*;
import com.mst.matt.tradingplatformapp.model.OhlcvBar;
import com.mst.matt.tradingplatformapp.model.Trade.AssetType;
import com.mst.matt.tradingplatformapp.service.price.api.FrankfurterLatestResponse;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/**
 * Frankfurter.app — free, no API key, ECB-sourced forex rates.
 * 30+ currencies, updated daily.
 *
 * Endpoints:
 *   /latest?from=EUR&to=USD         → current rate
 *   /YYYY-MM-DD..YYYY-MM-DD?from=X  → historical rates (for OHLCV approximation)
 */
@Service
public class ForexService implements PriceService {

    private static final Logger log = LoggerFactory.getLogger(ForexService.class);

    @Value("${api.frankfurter.base-url}")
    private String baseUrl;

    private final OkHttpClient httpClient;
    private final Gson gson = new Gson();

    // Supported forex pairs
    private static final Set<String> SUPPORTED_CURRENCIES = Set.of(
            "USD","EUR","GBP","JPY","CHF","CAD","AUD","NZD","SEK","NOK",
            "DKK","PLN","CZK","HUF","BGN","RON","HRK","TRY","CNY","HKD",
            "SGD","KRW","BRL","MXN","ZAR","INR","RUB","IDR","PHP","THB"
    );

    public ForexService(@Autowired @Qualifier("priceHttpClient") OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public Optional<PriceQuote> getQuote(String symbol) {
        // Parse pair: EURUSD, EUR/USD, EUR-USD
        String[] pair = parsePair(symbol);
        if (pair == null) return Optional.empty();

        String from = pair[0];
        String to   = pair[1];
        String url  = baseUrl + "/latest?from=" + from + "&to=" + to;

        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "TradingPlatform/1.0")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return Optional.empty();

            JsonObject json = gson.fromJson(response.body().string(), JsonObject.class);
            return FrankfurterLatestResponse.fromJson(json)
                    .flatMap(r -> r.toPriceQuote(from, to));

        } catch (IOException e) {
            log.error("Frankfurter error for {}: {}", symbol, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<OhlcvBar> getOhlcv(String symbol, String timeframe, int limit) {
        String[] pair = parsePair(symbol);
        if (pair == null) return Collections.emptyList();

        String from = pair[0];
        String to   = pair[1];

        // Calculate date range from limit
        LocalDate endDate   = LocalDate.now();
        LocalDate startDate = endDate.minusDays(limit + 10); // extra buffer for weekends

        String url = String.format("%s/%s..%s?from=%s&to=%s",
                baseUrl, startDate, endDate, from, to);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "TradingPlatform/1.0")
                .build();

        List<OhlcvBar> bars = new ArrayList<>();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return bars;

            JsonObject json  = gson.fromJson(response.body().string(), JsonObject.class);
            JsonObject rates = json.getAsJsonObject("rates");
            if (rates == null) return bars;

            // Frankfurter returns daily rates — approximate OHLC from consecutive days
            List<Map.Entry<String, JsonElement>> entries =
                    new ArrayList<>(rates.entrySet());

            for (int i = 0; i < entries.size(); i++) {
                Map.Entry<String, JsonElement> entry = entries.get(i);
                BigDecimal close = JsonParseUtil.asBigDecimal(
                        entry.getValue().getAsJsonObject(), to);

                BigDecimal open = (i == 0) ? close :
                        JsonParseUtil.asBigDecimal(
                                entries.get(i - 1).getValue().getAsJsonObject(), to);

                // Approximate high/low with ±0.1% spread
                BigDecimal spread = close.multiply(new BigDecimal("0.001"));
                BigDecimal high   = close.add(spread);
                BigDecimal low    = close.subtract(spread);

                OhlcvBar bar = OhlcvBar.builder()
                        .symbol(from + "/" + to)
                        .timeframe("1d")
                        .openTime(LocalDate.parse(entry.getKey())
                                .atStartOfDay())
                        .open(open)
                        .high(high)
                        .low(low)
                        .close(close)
                        .volume(BigDecimal.ZERO)
                        .assetType(AssetType.FOREX)
                        .build();

                bars.add(bar);
            }

        } catch (IOException e) {
            log.error("Frankfurter history error: {}", e.getMessage());
        }

        if (bars.size() > limit)
            bars = bars.subList(bars.size() - limit, bars.size());

        return bars;
    }

    @Override
    public boolean supports(String symbol) {
        String[] pair = parsePair(symbol);
        return pair != null
                && SUPPORTED_CURRENCIES.contains(pair[0])
                && SUPPORTED_CURRENCIES.contains(pair[1]);
    }

    @Override
    public String getProviderName() { return "Frankfurter (ECB)"; }

    @Override
    public MarketDataProvider getProviderId() { return MarketDataProvider.FRANKFURTER; }

    // ── Helpers ─────────────────────────────────────────────

    private String[] parsePair(String symbol) {
        // Supports: EURUSD, EUR/USD, EUR-USD, EUR_USD
        String s = symbol.toUpperCase()
                .replace("/", "")
                .replace("-", "")
                .replace("_", "")
                .replace("=X", "");  // Yahoo forex suffix

        if (s.length() == 6) {
            String from = s.substring(0, 3);
            String to   = s.substring(3, 6);
            if (SUPPORTED_CURRENCIES.contains(from)
                    && SUPPORTED_CURRENCIES.contains(to))
                return new String[]{from, to};
        }
        return null;
    }
}