package com.mst.matt.tradingservice.bitunix.ws;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mst.matt.tradingservice.bitunix.auth.BitUnixCredential;
import com.mst.matt.tradingservice.bitunix.auth.BitUnixSignatureService;
import com.mst.matt.tradingservice.bitunix.ws.dto.FuturesWsBalanceData;
import com.mst.matt.tradingservice.bitunix.ws.dto.FuturesWsOrderData;
import com.mst.matt.tradingservice.bitunix.ws.dto.FuturesWsPositionData;
import com.mst.matt.tradingservice.config.TradingProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Long-lived WebSocket client for BitUnix's Futures <em>private</em> push channels
 * (Step 6 — Futures WebSocket push-channel model).
 *
 * <h3>Protocol overview</h3>
 * <ol>
 *   <li><b>Connect</b> to {@code wss://openapi.bitunix.com:443/ws-api/v1}</li>
 *   <li><b>Login</b> — send a signed {@code {"op":"login","args":[...]}} frame.
 *       The server responds with {@code {"op":"login","code":0}}.</li>
 *   <li><b>Subscribe</b> to private channels after login ack:
 *       {@code balance}, {@code order}, {@code position}.</li>
 *   <li><b>Receive push messages</b> — dispatched by channel name to typed
 *       deserialisation handlers, which then publish
 *       {@link com.mst.matt.tradingservice.bitunix.ws.dto.OrderStatusUpdated}
 *       / {@link com.mst.matt.tradingservice.bitunix.ws.dto.FuturesWsBalanceData}
 *       events via Spring's {@link ApplicationEventPublisher}.</li>
 *   <li><b>Heartbeat</b> — send {@code {"op":"ping","ping":<unix_ms>}} every
 *       {@link TradingProperties#getWsPingIntervalSeconds()} seconds
 *       (BitUnix requires a ping within every 30 s window).</li>
 *   <li><b>Reconnect</b> — on disconnect, back off exponentially (1s → 2s →
 *       4s … capped at 60 s) then re-connect and re-login and re-subscribe.</li>
 * </ol>
 *
 * <h3>Channel list</h3>
 * <ul>
 *   <li>{@code balance}  — fired on any account balance change</li>
 *   <li>{@code order}    — fired on order lifecycle events (new/partial/filled/cancelled)</li>
 *   <li>{@code position} — fired on position open/update/close events</li>
 * </ul>
 *
 * <h3>Rate limits</h3>
 * <ul>
 *   <li>Max 5 outbound messages / second / connection</li>
 *   <li>Max 300 subscriptions / connection</li>
 * </ul>
 *
 * <h3>Credential note</h3>
 * <p>This stub uses the global credentials from {@link TradingProperties}.
 * The production implementation will accept per-user credentials and maintain
 * one WS connection per active user session (or one shared connection with
 * per-user channel filtering).</p>
 */
@Slf4j
@Component
public class BitUnixFuturesWsClient {

    // ── Private channel names ─────────────────────────────────────────────────

    private static final String CH_BALANCE  = "balance";
    private static final String CH_ORDER    = "order";
    private static final String CH_POSITION = "position";

    // ── Reconnect back-off limits ─────────────────────────────────────────────

    private static final long RECONNECT_MIN_DELAY_MS = 1_000L;
    private static final long RECONNECT_MAX_DELAY_MS = 60_000L;

    // ── Dependencies ──────────────────────────────────────────────────────────

    private final OkHttpClient               wsHttpClient;
    private final Gson                       gson;
    private final BitUnixSignatureService    signer;
    private final TradingProperties          props;
    private final ApplicationEventPublisher  eventPublisher;

    // ── State ─────────────────────────────────────────────────────────────────

    private final AtomicReference<WebSocket>  currentWs       = new AtomicReference<>();
    private final AtomicBoolean               loggedIn        = new AtomicBoolean(false);
    private final AtomicBoolean               running         = new AtomicBoolean(false);
    private final AtomicInteger               reconnectDelaySec = new AtomicInteger(1);

    private final ScheduledExecutorService    scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "bitunix-futures-ws-scheduler");
                t.setDaemon(true);
                return t;
            });

    private volatile ScheduledFuture<?>       pingTask;

    // ── Constructor ───────────────────────────────────────────────────────────

    public BitUnixFuturesWsClient(
            @Qualifier("tradingBitUnixWsHttpClient") OkHttpClient wsHttpClient,
            @Qualifier("tradingGson") Gson gson,
            BitUnixSignatureService signer,
            TradingProperties props,
            ApplicationEventPublisher eventPublisher) {
        this.wsHttpClient   = wsHttpClient;
        this.gson           = gson;
        this.signer         = signer;
        this.props          = props;
        this.eventPublisher = eventPublisher;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Starts the WebSocket connection after Spring context is fully initialised.
     *
     * <p>Skipped if no credentials are configured (prevents startup failures in
     * environments where BitUnix keys are not yet set).</p>
     */
    @PostConstruct
    public void start() {
        if (props.getBitunixApiKey() == null || props.getBitunixApiKey().isBlank()) {
            log.warn("BitUnix API key not configured — Futures WS client will NOT start. "
                    + "Set bitunix.bitunix-api-key in application.yml or via env var.");
            return;
        }
        running.set(true);
        connect();
    }

    /** Shuts down cleanly on Spring context close. */
    @PreDestroy
    public void stop() {
        log.info("Shutting down BitUnix Futures WS client");
        running.set(false);
        cancelPingTask();
        WebSocket ws = currentWs.getAndSet(null);
        if (ws != null) ws.close(1000, "service shutdown");
        scheduler.shutdownNow();
    }

    // ── Connect ───────────────────────────────────────────────────────────────

    private void connect() {
        if (!running.get()) return;

        String url = props.getWsPrivateUrl();
        log.info("Connecting to BitUnix Futures WS: {}", url);

        Request request = new Request.Builder().url(url).build();
        wsHttpClient.newWebSocket(request, new FuturesWsListener());
    }

    // ── Ping ──────────────────────────────────────────────────────────────────

    private void schedulePing(WebSocket ws) {
        cancelPingTask();
        int intervalSec = props.getWsPingIntervalSeconds();
        pingTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                long now = System.currentTimeMillis();
                String pingFrame = "{\"op\":\"ping\",\"ping\":" + now + "}";
                log.trace("Sending WS ping: {}", pingFrame);
                ws.send(pingFrame);
            } catch (Exception e) {
                log.warn("Ping send failed: {}", e.getMessage());
            }
        }, intervalSec, intervalSec, TimeUnit.SECONDS);
    }

    private void cancelPingTask() {
        if (pingTask != null && !pingTask.isCancelled()) {
            pingTask.cancel(false);
        }
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    private void sendLogin(WebSocket ws) {
        BitUnixCredential credential = new BitUnixCredential(
                props.getBitunixApiKey(), props.getBitunixSecretKey());
        String nonce     = signer.generateNonce();
        String timestamp = signer.generateTimestamp();
        String sign      = signer.signWsLogin(nonce, timestamp, credential);

        JsonObject args = new JsonObject();
        args.addProperty("apiKey",    credential.apiKey());
        args.addProperty("timestamp", timestamp);
        args.addProperty("nonce",     nonce);
        args.addProperty("sign",      sign);

        JsonObject loginFrame = new JsonObject();
        loginFrame.addProperty("op", "login");
        loginFrame.add("args", gson.toJsonTree(new JsonObject[]{args}));

        String loginStr = gson.toJson(loginFrame);
        log.info("Sending WS login frame (nonce={}, timestamp={})", nonce, timestamp);
        ws.send(loginStr);
    }

    // ── Subscribe ─────────────────────────────────────────────────────────────

    private void subscribePrivateChannels(WebSocket ws) {
        // Subscribe balance, order, position channels
        String subscribeFrame = "{"
                + "\"op\":\"subscribe\","
                + "\"args\":[\""  + CH_BALANCE  + "\","
                + "\""           + CH_ORDER     + "\","
                + "\""           + CH_POSITION  + "\""
                + "]}";
        log.info("Subscribing to private channels: {}, {}, {}", CH_BALANCE, CH_ORDER, CH_POSITION);
        ws.send(subscribeFrame);
    }

    // ── Message dispatch ──────────────────────────────────────────────────────

    private void handleMessage(String text) {
        try {
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();

            // Control frame: pong
            if (json.has("op")) {
                String op = json.get("op").getAsString();
                switch (op) {
                    case "pong" -> log.trace("WS pong received");
                    case "login" -> {
                        int code = json.has("code") ? json.get("code").getAsInt() : -1;
                        if (code == 0) {
                            log.info("WS login successful — subscribing to private channels");
                            loggedIn.set(true);
                            reconnectDelaySec.set(1);  // reset back-off on successful login
                            WebSocket ws = currentWs.get();
                            if (ws != null) subscribePrivateChannels(ws);
                        } else {
                            log.error("WS login failed: code={} msg={}",
                                    code, json.has("msg") ? json.get("msg").getAsString() : "");
                        }
                    }
                    case "subscribe" -> {
                        int code = json.has("code") ? json.get("code").getAsInt() : -1;
                        log.info("WS subscribe ack: code={}", code);
                    }
                    default -> log.debug("Unknown WS op frame: {}", text);
                }
                return;
            }

            // Push message: has "ch" field
            if (!json.has("ch")) {
                log.debug("WS message without ch/op field (ignored): {}", text);
                return;
            }

            String ch   = json.get("ch").getAsString();
            long   ts   = json.has("ts") ? json.get("ts").getAsLong() : 0L;
            var    data = json.get("data");

            switch (ch) {
                case CH_ORDER    -> dispatchOrderEvent(data, ts);
                case CH_BALANCE  -> dispatchBalanceEvent(data, ts);
                case CH_POSITION -> dispatchPositionEvent(data, ts);
                default          -> log.debug("Unknown WS channel '{}' — ignored", ch);
            }

        } catch (Exception e) {
            log.error("Error handling WS message: {} — text={}", e.getMessage(), text, e);
        }
    }

    private void dispatchOrderEvent(com.google.gson.JsonElement dataEl, long serverTs) {
        FuturesWsOrderData order = gson.fromJson(dataEl, FuturesWsOrderData.class);
        log.info("WS order push: orderId={} status={} symbol={} event={}",
                order.getOrderId(), order.getOrderStatus(),
                order.getSymbol(), order.getEvent());
        // Publish to Spring's application event bus; BitUnixFuturesOrderListener consumes it
        eventPublisher.publishEvent(new RawFuturesOrderPush(order, serverTs));
    }

    private void dispatchBalanceEvent(com.google.gson.JsonElement dataEl, long serverTs) {
        FuturesWsBalanceData balance = gson.fromJson(dataEl, FuturesWsBalanceData.class);
        log.info("WS balance push: coin={} available={}", balance.getCoin(), balance.getAvailable());
        eventPublisher.publishEvent(new RawFuturesBalancePush(balance, serverTs));
    }

    private void dispatchPositionEvent(com.google.gson.JsonElement dataEl, long serverTs) {
        FuturesWsPositionData pos = gson.fromJson(dataEl, FuturesWsPositionData.class);
        log.info("WS position push: positionId={} side={} qty={} event={}",
                pos.getPositionId(), pos.getSide(), pos.getQty(), pos.getEvent());
        eventPublisher.publishEvent(new RawFuturesPositionPush(pos, serverTs));
    }

    // ── Reconnect ─────────────────────────────────────────────────────────────

    private void scheduleReconnect() {
        if (!running.get()) return;
        cancelPingTask();
        loggedIn.set(false);

        long delay = Math.min(
                (long) reconnectDelaySec.get() * 1000L,
                RECONNECT_MAX_DELAY_MS);
        // Exponential back-off: double delay on each reconnect attempt
        reconnectDelaySec.updateAndGet(d -> (int) Math.min(d * 2L, 64));

        log.info("Scheduling WS reconnect in {} ms", delay);
        scheduler.schedule(this::connect, delay, TimeUnit.MILLISECONDS);
    }

    // ── Inner WS listener ─────────────────────────────────────────────────────

    private class FuturesWsListener extends WebSocketListener {

        @Override
        public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
            log.info("BitUnix Futures WS connected ({})", response.code());
            currentWs.set(webSocket);
            loggedIn.set(false);
            schedulePing(webSocket);
            sendLogin(webSocket);
        }

        @Override
        public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
            log.trace("WS ← {}", text);
            handleMessage(text);
        }

        @Override
        public void onClosing(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
            log.warn("BitUnix Futures WS closing: code={} reason={}", code, reason);
            webSocket.close(1000, null);
        }

        @Override
        public void onClosed(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
            log.warn("BitUnix Futures WS closed: code={} reason={}", code, reason);
            scheduleReconnect();
        }

        @Override
        public void onFailure(@NotNull WebSocket webSocket,
                              @NotNull Throwable t,
                              @Nullable Response response) {
            log.error("BitUnix Futures WS failure: {} (response={})",
                    t.getMessage(),
                    response != null ? response.code() : "none", t);
            scheduleReconnect();
        }
    }

    // ── Internal event wrappers (intra-service only) ──────────────────────────

    /** Spring application event wrapping a raw futures order push frame. */
    public record RawFuturesOrderPush(FuturesWsOrderData data, long serverTs) {}

    /** Spring application event wrapping a raw futures balance push frame. */
    public record RawFuturesBalancePush(FuturesWsBalanceData data, long serverTs) {}

    /** Spring application event wrapping a raw futures position push frame. */
    public record RawFuturesPositionPush(FuturesWsPositionData data, long serverTs) {}
}
