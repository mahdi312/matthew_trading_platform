package com.mst.matt.marketservice.bitunix.ws;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mst.matt.contracts.dto.OhlcvBarDto;
import com.mst.matt.contracts.dto.PriceTickDto;
import com.mst.matt.contracts.enums.BrokerType;
import com.mst.matt.marketservice.bitunix.BitUnixProperties;
import com.mst.matt.marketservice.bitunix.support.BitUnixIntervalSupport;
import com.mst.matt.marketservice.bitunix.support.BitUnixWsChannelSupport;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.io.Closeable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Owns the single, process-wide OkHttp WebSocket connection to BitUnix's
 * public live-market feed ({@code wss://fapi.bitunix.com/public/}) and fans
 * every incoming push out two ways (Step 5.4):
 * <ol>
 *   <li><b>STOMP relay</b> — every kline push is broadcast to
 *       {@code /topic/ohlcv/{symbol}/{interval}} and every ticker push to
 *       {@code /topic/price/{symbol}} via {@link SimpMessagingTemplate}.
 *       Spring's STOMP broker only actually delivers a message to sessions
 *       that have subscribed to that destination, so broadcasting
 *       unconditionally (rather than tracking per-topic subscriber counts)
 *       is safe and simple.</li>
 *   <li><b>In-process listeners</b> — {@link #streamTicker} registers a
 *       direct {@link Consumer} used by
 *       {@code BitUnixMarketDataProvider#streamLivePrice}, independent of
 *       any STOMP/frontend subscriber.</li>
 * </ol>
 *
 * <h3>Connection lifecycle</h3>
 * <p>Connects eagerly on startup ({@link PostConstruct}) since this is an
 * unauthenticated public feed with no per-connection cost concerns beyond
 * the documented rate limits. Reconnects with exponential backoff (capped)
 * on any failure/close, and — because BitUnix does not persist
 * subscriptions across connections — re-sends every channel currently in
 * {@link #activeChannels} immediately after reconnecting.</p>
 *
 * <h3>Keepalive</h3>
 * <p>Sends {@code {"op":"ping","ping":<unix_ms>}} every
 * {@code bitunix.ws-ping-interval-seconds} (default 25s) via a dedicated
 * single-thread scheduler, per the guide's documented "~20-30s" cadence.
 * OkHttp's own {@code pingInterval} (configured on the
 * {@code bitUnixWsHttpClient} bean) provides a second, protocol-level
 * keepalive independently of this application-level ping.</p>
 */
@Slf4j
@Component
public class BitUnixWebSocketClient implements Closeable {

    private static final long BACKOFF_BASE_MS = 2_000L;
    private static final long BACKOFF_MAX_MS = 60_000L;

    private final BitUnixProperties properties;
    private final OkHttpClient wsHttpClient;
    private final Gson gson;
    private final SimpMessagingTemplate messagingTemplate;

    /** Every BitUnix WS channel we should be subscribed to, e.g. {@code "BTCUSDT|market_kline_60min"} or {@code "BTCUSDT|ticker"}. Re-sent on every (re)connect. */
    private final Set<String> activeChannels = ConcurrentHashMap.newKeySet();

    /** In-process ticker listeners for {@code streamLivePrice}, keyed by symbol. */
    private final ConcurrentHashMap<String, List<Consumer<PriceTickDto>>> tickerListeners = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(daemonThreadFactory());

    private volatile WebSocket webSocket;
    private volatile boolean connected = false;
    private volatile boolean shouldRun = true;
    private volatile java.util.concurrent.ScheduledFuture<?> pingTask;
    private int reconnectAttempts = 0;

    public BitUnixWebSocketClient(
            BitUnixProperties properties,
            @Qualifier("bitUnixWsHttpClient") OkHttpClient wsHttpClient,
            Gson bitUnixGson,
            SimpMessagingTemplate messagingTemplate) {
        this.properties = properties;
        this.wsHttpClient = wsHttpClient;
        this.gson = bitUnixGson;
        this.messagingTemplate = messagingTemplate;
    }

    @PostConstruct
    public void init() {
        connect();
    }

    @PreDestroy
    @Override
    public void close() {
        shouldRun = false;
        cancelPing();
        scheduler.shutdownNow();
        if (webSocket != null) {
            webSocket.close(1000, "market-service shutting down");
            webSocket = null;
        }
        connected = false;
        log.info("BitUnix WebSocket client shut down");
    }

    // ── Subscription management ─────────────────────────────────────────────────

    /**
     * Subscribes to BitUnix's kline channel for {@code symbol}/{@code interval}
     * (REST-style interval, e.g. {@code "1h"} — translated internally to
     * BitUnix's WS suffix, e.g. {@code "60min"}). Idempotent.
     */
    public void subscribeKline(String symbol, String interval) {
        String channel = BitUnixWsChannelSupport.klineChannel(interval);
        String key = channelKey(symbol, channel);
        if (activeChannels.add(key)) {
            sendSubscribe(symbol, channel);
        }
    }

    /** Unsubscribes from BitUnix's kline channel for {@code symbol}/{@code interval}. */
    public void unsubscribeKline(String symbol, String interval) {
        String channel = BitUnixWsChannelSupport.klineChannel(interval);
        String key = channelKey(symbol, channel);
        if (activeChannels.remove(key)) {
            sendUnsubscribe(symbol, channel);
        }
    }

    /**
     * Switches a live kline stream from one interval to another for the
     * same symbol. Per the guide: unsubscribe the old channel first, then
     * subscribe the new one — never just re-subscribe over an existing
     * channel.
     */
    public void switchKlineInterval(String symbol, String oldInterval, String newInterval) {
        unsubscribeKline(symbol, oldInterval);
        subscribeKline(symbol, newInterval);
    }

    /** Subscribes to BitUnix's {@code ticker} channel for {@code symbol}. Idempotent. */
    public void subscribeTicker(String symbol) {
        String key = channelKey(symbol, BitUnixWsChannelSupport.TICKER_CHANNEL);
        if (activeChannels.add(key)) {
            sendSubscribe(symbol, BitUnixWsChannelSupport.TICKER_CHANNEL);
        }
    }

    /** Unsubscribes from BitUnix's {@code ticker} channel for {@code symbol}. */
    public void unsubscribeTicker(String symbol) {
        String key = channelKey(symbol, BitUnixWsChannelSupport.TICKER_CHANNEL);
        if (activeChannels.remove(key)) {
            sendUnsubscribe(symbol, BitUnixWsChannelSupport.TICKER_CHANNEL);
        }
    }

    // ── In-process live price stream (backs MarketDataProvider#streamLivePrice) ──

    /**
     * Opens a blocking {@link Stream} of {@link PriceTickDto} for
     * {@code symbol}, ensuring BitUnix's {@code ticker} channel is
     * subscribed for as long as the stream is open. Closing the returned
     * stream (it is {@link AutoCloseable} via {@link Stream}) unregisters
     * the listener and unsubscribes the channel if no other listener for
     * the same symbol remains.
     */
    public Stream<PriceTickDto> streamTicker(String symbol) {
        BlockingQueue<PriceTickDto> queue = new LinkedBlockingQueue<>();
        Consumer<PriceTickDto> listener = queue::offer;

        List<Consumer<PriceTickDto>> listeners = tickerListeners.computeIfAbsent(
                symbol, s -> new CopyOnWriteArrayList<>());
        listeners.add(listener);
        subscribeTicker(symbol);

        Iterator<PriceTickDto> iterator = new Iterator<>() {
            @Override
            public boolean hasNext() {
                return true; // unbounded live stream — caller closes when done
            }

            @Override
            public PriceTickDto next() {
                try {
                    return queue.take();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new java.util.NoSuchElementException("Stream interrupted");
                }
            }
        };

        Stream<PriceTickDto> stream = StreamSupport.stream(
                java.util.Spliterators.spliteratorUnknownSize(iterator, 0), false);

        return stream.onClose(() -> {
            listeners.remove(listener);
            if (listeners.isEmpty()) {
                tickerListeners.remove(symbol, listeners);
                unsubscribeTicker(symbol);
            }
        });
    }

    // ── Connection management ────────────────────────────────────────────────────

    public synchronized void connect() {
        if (connected) {
            return;
        }
        Request request = new Request.Builder().url(properties.getWsPublicUrl()).build();
        log.info("Connecting to BitUnix public WebSocket feed at {}", properties.getWsPublicUrl());
        webSocket = wsHttpClient.newWebSocket(request, new BitUnixWsListener());
    }

    private synchronized void scheduleReconnect() {
        if (!shouldRun) {
            return;
        }
        long delay = Math.min(BACKOFF_BASE_MS * (1L << Math.min(reconnectAttempts, 5)), BACKOFF_MAX_MS);
        reconnectAttempts++;
        log.info("BitUnix WS: reconnecting in {}ms (attempt {})", delay, reconnectAttempts);
        scheduler.schedule(this::connect, delay, TimeUnit.MILLISECONDS);
    }

    private void resubscribeAll() {
        for (String key : activeChannels) {
            int sep = key.indexOf('|');
            if (sep < 0) {
                continue;
            }
            String symbol = key.substring(0, sep);
            String channel = key.substring(sep + 1);
            sendSubscribe(symbol, channel);
        }
    }

    private void startPing() {
        cancelPing();
        int intervalSec = properties.getWsPingIntervalSeconds();
        pingTask = scheduler.scheduleAtFixedRate(this::sendPing, intervalSec, intervalSec, TimeUnit.SECONDS);
    }

    private void cancelPing() {
        if (pingTask != null) {
            pingTask.cancel(false);
            pingTask = null;
        }
    }

    private void sendPing() {
        WebSocket ws = webSocket;
        if (ws == null) {
            return;
        }
        JsonObject ping = new JsonObject();
        ping.addProperty("op", "ping");
        ping.addProperty("ping", Instant.now().toEpochMilli());
        ws.send(gson.toJson(ping));
    }

    private void sendSubscribe(String symbol, String channel) {
        sendSubscription("subscribe", symbol, channel);
    }

    private void sendUnsubscribe(String symbol, String channel) {
        sendSubscription("unsubscribe", symbol, channel);
    }

    private void sendSubscription(String op, String symbol, String channel) {
        WebSocket ws = webSocket;
        if (ws == null || !connected) {
            // Not connected right now — activeChannels already updated by the
            // caller, so resubscribeAll() will send this once we (re)connect.
            return;
        }
        JsonObject arg = new JsonObject();
        arg.addProperty("symbol", symbol);
        arg.addProperty("ch", channel);
        JsonArray args = new JsonArray();
        args.add(arg);
        JsonObject frame = new JsonObject();
        frame.addProperty("op", op);
        frame.add("args", args);
        ws.send(gson.toJson(frame));
        log.debug("BitUnix WS: {} {}::{}", op, symbol, channel);
    }

    private static String channelKey(String symbol, String channel) {
        return symbol + "|" + channel;
    }

    private static ThreadFactory daemonThreadFactory() {
        return runnable -> {
            Thread t = new Thread(runnable, "bitunix-ws-scheduler");
            t.setDaemon(true);
            return t;
        };
    }

    // ── Incoming message dispatch ───────────────────────────────────────────────

    private void dispatch(String text) {
        BitUnixWsPushMessage message;
        try {
            message = gson.fromJson(text, BitUnixWsPushMessage.class);
        } catch (Exception e) {
            log.debug("BitUnix WS: unparseable message: {}", abbreviate(text));
            return;
        }
        if (message == null) {
            return;
        }
        if ("ping".equals(message.getOp())) {
            log.trace("BitUnix WS: pong received");
            return;
        }
        if (message.getCh() == null || message.getData() == null) {
            return;
        }
        if (BitUnixWsChannelSupport.TICKER_CHANNEL.equals(message.getCh())) {
            dispatchTicker(message);
        } else {
            String interval = BitUnixWsChannelSupport.intervalFromKlineChannel(message.getCh());
            if (interval != null) {
                dispatchKline(message, interval);
            } else {
                log.trace("BitUnix WS: unrecognised channel '{}'", message.getCh());
            }
        }
    }

    private void dispatchTicker(BitUnixWsPushMessage message) {
        try {
            BitUnixWsTickerData data = gson.fromJson(message.getData(), BitUnixWsTickerData.class);
            String symbol = message.getSymbol();
            BigDecimal lastPrice = toBigDecimal(data.getLa());
            BigDecimal changeFraction = toBigDecimal(data.getR());
            BigDecimal changePercent = changeFraction != null
                    ? changeFraction.multiply(BigDecimal.valueOf(100)).setScale(4, RoundingMode.HALF_UP)
                    : null;

            PriceTickDto tick = PriceTickDto.builder()
                    .source(BrokerType.BITUNIX)
                    .symbol(symbol)
                    .lastPrice(lastPrice)
                    .bidPrice(null)
                    .askPrice(null)
                    .volume24h(toBigDecimal(data.getB()))
                    .changePercent24h(changePercent)
                    .timestamp(Instant.ofEpochMilli(message.getTs()))
                    .build();

            messagingTemplate.convertAndSend("/topic/price/" + symbol, tick);

            List<Consumer<PriceTickDto>> listeners = tickerListeners.get(symbol);
            if (listeners != null) {
                for (Consumer<PriceTickDto> listener : listeners) {
                    try {
                        listener.accept(tick);
                    } catch (Exception ex) {
                        log.warn("BitUnix WS: ticker listener error for {}: {}", symbol, ex.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.debug("BitUnix WS: ticker dispatch error: {}", e.getMessage());
        }
    }

    private void dispatchKline(BitUnixWsPushMessage message, String interval) {
        try {
            BitUnixWsKlineData data = gson.fromJson(message.getData(), BitUnixWsKlineData.class);
            String symbol = message.getSymbol();
            Instant pushTime = Instant.ofEpochMilli(message.getTs());
            Duration barDuration = BitUnixIntervalSupport.durationOf(interval);

            OhlcvBarDto bar = OhlcvBarDto.builder()
                    .openTime(pushTime.minus(barDuration))
                    .closeTime(pushTime)
                    .open(toBigDecimal(data.getO()))
                    .high(toBigDecimal(data.getH()))
                    .low(toBigDecimal(data.getL()))
                    .close(toBigDecimal(data.getC()))
                    .volume(toBigDecimal(data.getB()))
                    .tradeCount(null)
                    .build();

            messagingTemplate.convertAndSend("/topic/ohlcv/" + symbol + "/" + interval, bar);
        } catch (Exception e) {
            log.debug("BitUnix WS: kline dispatch error: {}", e.getMessage());
        }
    }

    private static BigDecimal toBigDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String abbreviate(String text) {
        return text.length() > 200 ? text.substring(0, 200) + "..." : text;
    }

    // ── OkHttp listener ──────────────────────────────────────────────────────────

    private class BitUnixWsListener extends WebSocketListener {

        @Override
        public void onOpen(WebSocket ws, Response response) {
            log.info("BitUnix WebSocket connected");
            connected = true;
            reconnectAttempts = 0;
            webSocket = ws;
            startPing();
            resubscribeAll();
        }

        @Override
        public void onMessage(WebSocket ws, String text) {
            dispatch(text);
        }

        @Override
        public void onFailure(WebSocket ws, Throwable t, Response response) {
            log.warn("BitUnix WebSocket failure: {}", t.getMessage());
            connected = false;
            cancelPing();
            webSocket = null;
            scheduleReconnect();
        }

        @Override
        public void onClosing(WebSocket ws, int code, String reason) {
            log.info("BitUnix WebSocket closing: {} {}", code, reason);
        }

        @Override
        public void onClosed(WebSocket ws, int code, String reason) {
            log.info("BitUnix WebSocket closed: {} {}", code, reason);
            connected = false;
            cancelPing();
            webSocket = null;
            if (shouldRun && code != 1000) {
                scheduleReconnect();
            }
        }
    }
}
