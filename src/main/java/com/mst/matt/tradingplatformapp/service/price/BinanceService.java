package com.mst.matt.tradingplatformapp.service.price;

import com.google.gson.*;
import com.mst.matt.tradingplatformapp.model.OhlcvBar;
import com.mst.matt.tradingplatformapp.model.Trade;
import com.mst.matt.tradingplatformapp.service.price.api.BinanceTicker24hResponse;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Binance price service — REST + WebSocket.
 *
 * REST  → GET /api/v3/ticker/24hr  for quotes
 * REST  → GET /api/v3/klines       for OHLCV history
 * WS    → wss://stream.binance.com:9443/ws/<symbol>@ticker  for live ticks
 *
 * No API key required for public market data endpoints.
 */
@Service
public class BinanceService implements PriceService {

    private static final Logger log = LoggerFactory.getLogger(BinanceService.class);

    @Value("${api.binance.base-url}")
    private String baseUrl;

    @Value("${api.binance.fallback-url:https://data-api.binance.vision}")
    private String fallbackUrl;

    private static final String WS_BASE = "wss://stream.binance.com:9443/ws/";
    private static final String WS_FALLBACK = "wss://data-stream.binance.vision/ws/";

    private final OkHttpClient httpClient;
    private final OkHttpClient wsClient;
    private final Gson gson = new Gson();

    // symbol → latest cached quote
    private final Map<String, PriceQuote> quoteCache = new ConcurrentHashMap<>();

    // symbol → active WebSocket
    private final Map<String, WebSocket> activeStreams = new ConcurrentHashMap<>();

    // Listeners registered by the UI for live updates
    private final List<Consumer<PriceQuote>> liveListeners = new CopyOnWriteArrayList<>();

    private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "binance-ws-reconnect");
        t.setDaemon(true);
        return t;
    });

    public BinanceService(@Autowired @Qualifier("priceHttpClient") OkHttpClient priceHttpClient) {
        this.httpClient = priceHttpClient;

        this.wsClient = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(0,  TimeUnit.MILLISECONDS)
                .pingInterval(20, TimeUnit.SECONDS)
                .build();
    }

    // ── PriceService Interface ──────────────────────────────

    @Override
    public Optional<PriceQuote> getQuote(String symbol) {
        String sym = SymbolNormalizer.forBinance(symbol);
        PriceQuote cached = quoteCache.get(sym);
        if (cached != null && isRecent(cached)) return Optional.of(cached);
        return fetchQuoteRest(sym);
    }

    @Override
    public List<OhlcvBar> getOhlcv(String symbol, String timeframe, int limit) {
        return fetchKlines(SymbolNormalizer.forBinance(symbol), mapTimeframe(timeframe), limit);
    }

    @Override
    public boolean supports(String symbol) {
        String n = SymbolNormalizer.normalize(symbol);
        if (n.isEmpty()) return false;
        if (n.endsWith("USDT") || n.endsWith("BUSD")) return true;
        if (n.length() > 3 && (n.endsWith("BTC") || n.endsWith("ETH") || n.endsWith("BNB"))) {
            return true;
        }
        // Bare base tickers (BTC, SOL) — not 4-letter stock symbols (AAPL, TSLA)
        if (n.length() >= 2 && n.length() <= 5 && n.chars().allMatch(Character::isLetter)) {
            return n.length() != 4;
        }
        return false;
    }

    @Override
    public String getProviderName() { return "Binance"; }

    @Override
    public MarketDataProvider getProviderId() { return MarketDataProvider.BINANCE; }

    // ── REST: Single Quote ──────────────────────────────────

    private Optional<PriceQuote> fetchQuoteRest(String symbol) {
        for (String root : List.of(baseUrl, fallbackUrl)) {
            Optional<PriceQuote> quote = fetchQuoteFromBase(root, symbol);
            if (quote.isPresent()) return quote;
        }
        return Optional.empty();
    }

    private Optional<PriceQuote> fetchQuoteFromBase(String root, String symbol) {
        String url = root + "/api/v3/ticker/24hr?symbol=" + symbol;
        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "TradingPlatform/1.0")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                log.warn("Binance HTTP {} for {} via {}", response.code(), symbol, root);
                return Optional.empty();
            }

            JsonObject json = gson.fromJson(response.body().string(), JsonObject.class);
            Optional<PriceQuote> quote = BinanceTicker24hResponse.fromJson(json)
                    .map(t -> t.toPriceQuote(symbol));
            quote.ifPresent(q -> quoteCache.put(symbol, q));
            return quote;

        } catch (IOException e) {
            log.error("Binance REST error for {} via {}: {}", symbol, root, e.getMessage());
            return Optional.empty();
        }
    }

    private List<OhlcvBar> fetchKlines(String symbol, String interval, int limit) {
        for (String root : List.of(baseUrl, fallbackUrl)) {
            List<OhlcvBar> bars = fetchKlinesFromBase(root, symbol, interval, limit);
            if (!bars.isEmpty()) return bars;
        }
        return List.of();
    }

    private List<OhlcvBar> fetchKlinesFromBase(String root, String symbol, String interval, int limit) {
        String url = String.format("%s/api/v3/klines?symbol=%s&interval=%s&limit=%d",
                root, symbol, interval, limit);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "TradingPlatform/1.0")
                .build();

        List<OhlcvBar> bars = new ArrayList<>();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return bars;

            // Binance returns array-of-arrays:
            // [openTime, open, high, low, close, volume, closeTime, ...]
            JsonArray klines = gson.fromJson(response.body().string(), JsonArray.class);

            for (JsonElement el : klines) {
                JsonArray k = el.getAsJsonArray();
                long openTimeMs = k.get(0).getAsLong();

                OhlcvBar bar = OhlcvBar.builder()
                        .symbol(symbol)
                        .timeframe(interval)
                        .openTime(LocalDateTime.ofInstant(
                                Instant.ofEpochMilli(openTimeMs), ZoneOffset.UTC))
                        .open(new BigDecimal(k.get(1).getAsString()))
                        .high(new BigDecimal(k.get(2).getAsString()))
                        .low(new BigDecimal(k.get(3).getAsString()))
                        .close(new BigDecimal(k.get(4).getAsString()))
                        .volume(new BigDecimal(k.get(5).getAsString()))
                        .assetType(Trade.AssetType.CRYPTO)
                        .build();

                bars.add(bar);
            }

        } catch (IOException e) {
            log.error("Binance klines error for {} via {}: {}", symbol, root, e.getMessage());
        }

        return bars;
    }

    // ── WebSocket: Live Ticker Stream ───────────────────────

    /**
     * Subscribe to a live price stream for a symbol.
     * Calls all registered liveListeners every time a tick arrives.
     */
    public void subscribeToTicker(String symbol) {
        String sym = SymbolNormalizer.forBinance(symbol).toLowerCase();
        if (activeStreams.containsKey(sym)) return;

        String url = WS_BASE + sym + "@ticker";
        Request request = new Request.Builder().url(url).build();

        WebSocket ws = wsClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onMessage(WebSocket webSocket, String text) {
                try {
                    JsonObject json = gson.fromJson(text, JsonObject.class);

                    PriceQuote quote = PriceQuote.builder()
                            .symbol(symbol.toUpperCase())
                            .assetType(Trade.AssetType.CRYPTO)
                            .price(new BigDecimal(json.get("c").getAsString()))
                            .open24h(new BigDecimal(json.get("o").getAsString()))
                            .high24h(new BigDecimal(json.get("h").getAsString()))
                            .low24h(new BigDecimal(json.get("l").getAsString()))
                            .change24h(new BigDecimal(json.get("p").getAsString()))
                            .changePct24h(new BigDecimal(json.get("P").getAsString()))
                            .volume24h(new BigDecimal(json.get("v").getAsString()))
                            .currency("USDT")
                            .exchange("Binance")
                            .timestamp(LocalDateTime.now())
                            .isUp(new BigDecimal(json.get("P").getAsString())
                                    .compareTo(BigDecimal.ZERO) >= 0)
                            .build();

                    quoteCache.put(symbol.toUpperCase(), quote);

                    // Notify all UI listeners
                    liveListeners.forEach(l -> l.accept(quote));

                } catch (Exception e) {
                    log.warn("Binance WS parse error: {}", e.getMessage());
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                log.error("Binance WS failure for {}: {}", symbol, t.getMessage());
                activeStreams.remove(sym);
                scheduleReconnect(symbol);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                activeStreams.remove(sym);
            }
        });

        activeStreams.put(sym, ws);
        log.info("Binance WS stream started for {}", symbol);
    }

    /**
     * Subscribe to multiple symbols (multi-stream).
     */
    public void subscribeToMultiTicker(List<String> symbols) {
        // Build combined stream URL: <s1>@ticker/<s2>@ticker/...
        String streams = String.join("/",
                symbols.stream()
                        .map(s -> s.toLowerCase() + "@ticker")
                        .toList());

        String url = "wss://stream.binance.com:9443/stream?streams=" + streams;
        Request request = new Request.Builder().url(url).build();

        wsClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onMessage(WebSocket webSocket, String text) {
                try {
                    JsonObject wrapper = gson.fromJson(text, JsonObject.class);
                    JsonObject data = wrapper.getAsJsonObject("data");
                    if (data == null) return;

                    String sym = data.get("s").getAsString();
                    PriceQuote quote = PriceQuote.builder()
                            .symbol(sym)
                            .assetType(Trade.AssetType.CRYPTO)
                            .price(new BigDecimal(data.get("c").getAsString()))
                            .change24h(new BigDecimal(data.get("p").getAsString()))
                            .changePct24h(new BigDecimal(data.get("P").getAsString()))
                            .volume24h(new BigDecimal(data.get("v").getAsString()))
                            .currency("USDT")
                            .exchange("Binance")
                            .timestamp(LocalDateTime.now())
                            .isUp(new BigDecimal(data.get("P").getAsString())
                                    .compareTo(BigDecimal.ZERO) >= 0)
                            .build();

                    quoteCache.put(sym, quote);
                    liveListeners.forEach(l -> l.accept(quote));

                } catch (Exception e) {
                    log.warn("Multi-stream parse error: {}", e.getMessage());
                }
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response r) {
                log.error("Multi-stream failure: {}", t.getMessage());
            }
        });
    }

    public void addLiveListener(Consumer<PriceQuote> listener) {
        liveListeners.add(listener);
    }

    private void scheduleReconnect(String symbol) {
        reconnectScheduler.schedule(() -> {
            try {
                subscribeToTicker(symbol);
            } catch (Exception e) {
                log.error("Binance WS reconnect failed for {}: {}", symbol, e.getMessage());
            }
        }, 5, TimeUnit.SECONDS);
    }

    public void unsubscribeAll() {
        activeStreams.values().forEach(ws -> ws.close(1000, "shutdown"));
        activeStreams.clear();
    }

    // ── Helpers ─────────────────────────────────────────────

    private boolean isRecent(PriceQuote q) {
        return q.getTimestamp() != null &&
                q.getTimestamp().isAfter(LocalDateTime.now().minusSeconds(5));
    }

    /**
     * Maps user-facing timeframe labels to Binance interval strings.
     */
    private String mapTimeframe(String tf) {
        return switch (tf.toLowerCase()) {
            case "1m"  -> "1m";
            case "3m"  -> "3m";
            case "5m"  -> "5m";
            case "15m" -> "15m";
            case "30m" -> "30m";
            case "1h"  -> "1h";
            case "2h"  -> "2h";
            case "4h"  -> "4h";
            case "6h"  -> "6h";
            case "8h"  -> "8h";
            case "12h" -> "12h";
            case "1d"  -> "1d";
            case "3d"  -> "3d";
            case "1w"  -> "1w";
            case "1mo" -> "1M";
            default    -> "1h";
        };
    }
}