package com.mst.matt.tradingplatformapp.service.price;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mst.matt.tradingplatformapp.config.MarketApiProperties;
import jakarta.annotation.PreDestroy;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Finnhub WebSocket streaming service — real-time trades and news.
 *
 * <p>WebSocket endpoint: {@code wss://ws.finnhub.io?token=YOUR_KEY}
 *
 * <h3>Free-tier capabilities:</h3>
 * <ul>
 *   <li>Real-time trade ticks (last price, volume, timestamp) for subscribed symbols</li>
 *   <li>Real-time news stream for all topics</li>
 *   <li>Subscribe/unsubscribe to individual symbols at any time</li>
 *   <li>Automatic ping/keep-alive handling</li>
 *   <li>Auto-reconnect on disconnect with exponential backoff</li>
 * </ul>
 *
 * <h3>Usage:</h3>
 * <pre>{@code
 * finnhubWebSocketService.subscribe("AAPL", trade -> {
 *     System.out.println("AAPL last price: " + trade.price());
 * });
 * }</pre>
 */
@Service
public class FinnhubWebSocketService {

    private static final Logger log = LoggerFactory.getLogger(FinnhubWebSocketService.class);

    /** Finnhub WebSocket endpoint. */
    private static final String WS_URL = "wss://ws.finnhub.io";

    /** Maximum reconnect attempts before giving up. */
    private static final int MAX_RECONNECT_ATTEMPTS = 10;

    /** Initial backoff delay in ms (doubles on each retry). */
    private static final long BACKOFF_BASE_MS = 2_000L;

    /** Maximum backoff cap. */
    private static final long BACKOFF_MAX_MS = 60_000L;

    private final MarketApiProperties keys;
    private final OkHttpClient wsClient;
    private final Gson gson = new Gson();

    /** Active WebSocket connection (nullable). */
    private volatile WebSocket webSocket;

    /** Currently subscribed symbols. */
    private final Set<String> subscribedSymbols = ConcurrentHashMap.newKeySet();

    /** Per-symbol trade listeners. */
    private final Map<String, List<Consumer<TradeTick>>> tradeListeners = new ConcurrentHashMap<>();

    /** Global news listeners. */
    private final List<Consumer<NewsItem>> newsListeners = new CopyOnWriteArrayList<>();

    /** Connection state. */
    private volatile boolean connected = false;
    private volatile boolean shouldReconnect = true;
    private int reconnectAttempts = 0;

    /** Latest price cache: symbol → last TradeTick. */
    private final Map<String, TradeTick> lastTradeCache = new ConcurrentHashMap<>();

    @Autowired
    public FinnhubWebSocketService(MarketApiProperties keys,
                                   @Qualifier("wsHttpClient") OkHttpClient wsClient) {
        this.keys = keys;
        this.wsClient = wsClient;
    }

    // ─── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns true if Finnhub WebSocket is enabled (API key present).
     */
    public boolean isEnabled() {
        return keys.hasFinnhubKey();
    }

    /**
     * Returns true if the WebSocket connection is currently active.
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Subscribe to real-time trades for a symbol.
     * Automatically connects if not already connected.
     *
     * @param symbol   stock/forex/crypto ticker (e.g. "AAPL", "OANDA:EUR_USD", "BINANCE:BTCUSDT")
     * @param listener callback invoked on each trade tick
     */
    public void subscribe(String symbol, Consumer<TradeTick> listener) {
        if (!isEnabled()) {
            log.warn("Finnhub WebSocket not enabled: API key missing");
            return;
        }
        String sym = symbol.toUpperCase();
        tradeListeners.computeIfAbsent(sym, k -> new CopyOnWriteArrayList<>()).add(listener);

        if (!connected) {
            connect();
        }
        if (connected && subscribedSymbols.add(sym)) {
            sendSubscribe(sym);
        }
    }

    /**
     * Unsubscribe from a symbol and remove all listeners for it.
     *
     * @param symbol ticker to unsubscribe
     */
    public void unsubscribe(String symbol) {
        String sym = symbol.toUpperCase();
        tradeListeners.remove(sym);
        if (subscribedSymbols.remove(sym) && connected && webSocket != null) {
            sendUnsubscribe(sym);
        }
    }

    /**
     * Register a global news listener (receives all streaming news items).
     *
     * @param listener callback invoked on each news item
     */
    public void subscribeToNews(Consumer<NewsItem> listener) {
        if (!isEnabled()) return;
        newsListeners.add(listener);
        if (!connected) connect();
        if (connected && webSocket != null) {
            webSocket.send("{\"type\":\"subscribe\",\"symbol\":\"news\"}");
        }
    }

    /**
     * Get the most recently received trade tick for a symbol (from in-memory cache).
     *
     * @param symbol ticker
     * @return last trade tick, or empty if none received yet
     */
    public Optional<TradeTick> getLastTrade(String symbol) {
        return Optional.ofNullable(lastTradeCache.get(symbol.toUpperCase()));
    }

    /**
     * Get all symbols with active subscriptions.
     */
    public Set<String> getSubscribedSymbols() {
        return Collections.unmodifiableSet(subscribedSymbols);
    }

    // ─── Connection Management ─────────────────────────────────────────────────

    /**
     * Initiate a WebSocket connection to Finnhub.
     * Idempotent — safe to call multiple times.
     */
    public synchronized void connect() {
        if (!isEnabled() || connected) return;

        String url = WS_URL + "?token=" + keys.getFinnhubKey();
        Request request = new Request.Builder().url(url).build();
        log.info("Connecting to Finnhub WebSocket...");

        webSocket = wsClient.newWebSocket(request, new FinnhubWebSocketListener());
    }

    /**
     * Gracefully close the WebSocket connection.
     */
    public synchronized void disconnect() {
        shouldReconnect = false;
        connected = false;
        if (webSocket != null) {
            webSocket.close(1000, "Client disconnect");
            webSocket = null;
        }
        subscribedSymbols.clear();
        log.info("Finnhub WebSocket disconnected");
    }

    @PreDestroy
    public void onDestroy() {
        disconnect();
    }

    // ─── Private helpers ───────────────────────────────────────────────────────

    private void sendSubscribe(String symbol) {
        if (webSocket == null) return;
        String msg = String.format("{\"type\":\"subscribe\",\"symbol\":\"%s\"}", symbol);
        webSocket.send(msg);
        log.debug("Finnhub WS: subscribed to {}", symbol);
    }

    private void sendUnsubscribe(String symbol) {
        if (webSocket == null) return;
        String msg = String.format("{\"type\":\"unsubscribe\",\"symbol\":\"%s\"}", symbol);
        webSocket.send(msg);
        log.debug("Finnhub WS: unsubscribed from {}", symbol);
    }

    private void resubscribeAll() {
        for (String sym : subscribedSymbols) {
            sendSubscribe(sym);
        }
        if (!newsListeners.isEmpty()) {
            webSocket.send("{\"type\":\"subscribe\",\"symbol\":\"news\"}");
        }
    }

    private void scheduleReconnect() {
        if (!shouldReconnect || reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            log.warn("Finnhub WS: max reconnect attempts ({}) reached. Stopping.", MAX_RECONNECT_ATTEMPTS);
            return;
        }
        long delay = Math.min(BACKOFF_BASE_MS * (1L << reconnectAttempts), BACKOFF_MAX_MS);
        reconnectAttempts++;
        log.info("Finnhub WS: reconnecting in {}ms (attempt {}/{})", delay,
                reconnectAttempts, MAX_RECONNECT_ATTEMPTS);
        Thread reconnectThread = new Thread(() -> {
            try {
                Thread.sleep(delay);
                connect();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "finnhub-ws-reconnect");
        reconnectThread.setDaemon(true);
        reconnectThread.start();
    }

    private void dispatchTrade(JsonObject tradeData) {
        try {
            String sym = tradeData.has("s") ? tradeData.get("s").getAsString() : null;
            if (sym == null) return;
            BigDecimal price  = tradeData.has("p") ? tradeData.get("p").getAsBigDecimal() : BigDecimal.ZERO;
            long volume       = tradeData.has("v") ? tradeData.get("v").getAsLong() : 0L;
            long tUnix        = tradeData.has("t") ? tradeData.get("t").getAsLong() : 0L;
            // Finnhub sends ms timestamps for trades
            long epochSec     = tUnix > 1_000_000_000_000L ? tUnix / 1000 : tUnix;
            LocalDateTime ts  = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(epochSec), ZoneOffset.UTC);

            TradeTick tick = new TradeTick(sym, price, volume, ts);
            lastTradeCache.put(sym, tick);

            List<Consumer<TradeTick>> listeners = tradeListeners.get(sym);
            if (listeners != null) {
                listeners.forEach(l -> {
                    try { l.accept(tick); }
                    catch (Exception ex) { log.warn("Trade listener error for {}: {}", sym, ex.getMessage()); }
                });
            }
        } catch (Exception e) {
            log.debug("Finnhub WS: trade dispatch error: {}", e.getMessage());
        }
    }

    private void dispatchNews(JsonObject newsData) {
        try {
            String headline = newsData.has("headline") ? newsData.get("headline").getAsString() : "";
            String summary  = newsData.has("summary")  ? newsData.get("summary").getAsString()  : "";
            String url      = newsData.has("url")       ? newsData.get("url").getAsString()      : "";
            Long datetime   = newsData.has("datetime")  ? newsData.get("datetime").getAsLong()   : null;
            LocalDateTime ts = datetime != null
                    ? LocalDateTime.ofInstant(Instant.ofEpochSecond(datetime), ZoneOffset.UTC)
                    : LocalDateTime.now();

            NewsItem item = new NewsItem(headline, summary, url, ts);
            newsListeners.forEach(l -> {
                try { l.accept(item); }
                catch (Exception ex) { log.warn("News listener error: {}", ex.getMessage()); }
            });
        } catch (Exception e) {
            log.debug("Finnhub WS: news dispatch error: {}", e.getMessage());
        }
    }

    // ─── WebSocket Listener ────────────────────────────────────────────────────

    private class FinnhubWebSocketListener extends WebSocketListener {

        @Override
        public void onOpen(WebSocket ws, Response response) {
            log.info("Finnhub WebSocket connected");
            connected = true;
            reconnectAttempts = 0;
            webSocket = ws;
            resubscribeAll();
        }

        @Override
        public void onMessage(WebSocket ws, String text) {
            try {
                JsonObject msg = gson.fromJson(text, JsonObject.class);
                if (msg == null) return;
                String type = msg.has("type") ? msg.get("type").getAsString() : "";

                switch (type) {
                    case "trade" -> {
                        if (msg.has("data") && msg.get("data").isJsonArray()) {
                            JsonArray data = msg.getAsJsonArray("data");
                            data.forEach(el -> {
                                if (el.isJsonObject()) dispatchTrade(el.getAsJsonObject());
                            });
                        }
                    }
                    case "news" -> {
                        if (msg.has("data") && msg.get("data").isJsonObject()) {
                            dispatchNews(msg.getAsJsonObject("data"));
                        }
                    }
                    case "ping" -> log.trace("Finnhub WS ping received");
                    case "error" -> {
                        String err = msg.has("msg") ? msg.get("msg").getAsString() : text;
                        log.warn("Finnhub WS error message: {}", err);
                    }
                    default -> log.trace("Finnhub WS unknown message type: {}", type);
                }
            } catch (Exception e) {
                log.debug("Finnhub WS message parse error: {}", e.getMessage());
            }
        }

        @Override
        public void onFailure(WebSocket ws, Throwable t, Response response) {
            log.warn("Finnhub WebSocket failure: {}", t.getMessage());
            connected = false;
            webSocket = null;
            if (shouldReconnect) {
                scheduleReconnect();
            }
        }

        @Override
        public void onClosing(WebSocket ws, int code, String reason) {
            log.info("Finnhub WebSocket closing: {} {}", code, reason);
        }

        @Override
        public void onClosed(WebSocket ws, int code, String reason) {
            log.info("Finnhub WebSocket closed: {} {}", code, reason);
            connected = false;
            webSocket = null;
            if (shouldReconnect && code != 1000) {
                scheduleReconnect();
            }
        }
    }

    // ─── Data Records ──────────────────────────────────────────────────────────

    /**
     * Real-time trade tick received from Finnhub WebSocket.
     */
    public record TradeTick(
            String        symbol,
            BigDecimal    price,
            long          volume,
            LocalDateTime timestamp
    ) {}

    /**
     * Real-time news item received from Finnhub WebSocket news stream.
     */
    public record NewsItem(
            String        headline,
            String        summary,
            String        url,
            LocalDateTime timestamp
    ) {}
}
