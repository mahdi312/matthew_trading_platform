package com.mst.matt.tradingservice.bitunix.ws;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mst.matt.tradingservice.bitunix.auth.BitUnixCredential;
import com.mst.matt.tradingservice.bitunix.auth.BitUnixSignatureService;
import com.mst.matt.tradingservice.bitunix.ws.dto.OrderStatusUpdated;
import com.mst.matt.tradingservice.config.TradingProperties;
import com.mst.matt.contracts.enums.BrokerType;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * WebSocket RPC client for BitUnix's <em>Spot</em> private API (Step 6 — Spot WS RPC model).
 *
 * <h3>Protocol: RPC request / reply</h3>
 * <p>Unlike the Futures push model, BitUnix Spot WebSocket uses an
 * <em>id-correlated RPC</em> pattern.  Every outbound request carries a
 * caller-generated {@code id}; the server echoes the same {@code id} in its
 * reply, allowing in-flight requests to be matched via a
 * {@link ConcurrentHashMap}&lt;String, CompletableFuture&gt; pending-reply map.</p>
 *
 * <h3>Wire format (request)</h3>
 * <pre>{@code
 * {
 *   "id":        "<uuid>",
 *   "method":    "order.place_order",
 *   "params": {
 *     "apiKey":    "...",
 *     "timestamp": "1712345678901",
 *     "nonce":     "abc123...",
 *     "sign":      "deadbeef...",
 *     // method-specific params:
 *     "symbol":    "BTCUSDT",
 *     "side":      2,
 *     "type":      1,
 *     "volume":    "0.001",
 *     "price":     "60000"
 *   }
 * }
 * }</pre>
 *
 * <h3>Wire format (reply)</h3>
 * <pre>{@code
 * {
 *   "id":     "<same-uuid>",
 *   "code":   0,
 *   "msg":    "Success",
 *   "data":   { ... }
 * }
 * }</pre>
 *
 * <h3>Supported methods</h3>
 * <ul>
 *   <li>{@code user.account}          — fetch spot account balances</li>
 *   <li>{@code order.place_order}     — place a single spot order</li>
 *   <li>{@code order.place_order.batch} — place multiple orders (not yet implemented here)</li>
 *   <li>{@code order.cancel}          — cancel a single spot order</li>
 *   <li>{@code order.pending.list}    — list open orders (poll fallback)</li>
 * </ul>
 *
 * <h3>Auth</h3>
 * <p>Auth fields (apiKey, timestamp, nonce, sign) are embedded inside
 * {@code params} of <em>every</em> request frame — there is no separate
 * login handshake (unlike the Futures WS).</p>
 *
 * <h3>Reconnect</h3>
 * <p>On disconnect, in-flight {@link CompletableFuture}s are completed
 * exceptionally so callers are not left hanging.  Exponential back-off
 * reconnect (1 s → 2 s → … → 60 s) is applied before the next connection
 * attempt.</p>
 *
 * <h3>Heartbeat</h3>
 * <p>Spot WS also requires a ping/pong keepalive.  This client sends the
 * same {@code {"op":"ping","ping":<ms>}} frame as the Futures client.</p>
 */
@Slf4j
@Component
public class BitUnixSpotWsClient {

    // ── Method constants ──────────────────────────────────────────────────────

    public static final String METHOD_USER_ACCOUNT     = "user.account";
    public static final String METHOD_PLACE_ORDER      = "order.place_order";
    public static final String METHOD_CANCEL_ORDER     = "order.cancel";
    public static final String METHOD_PENDING_LIST     = "order.pending.list";

    // ── Spot WS URL ───────────────────────────────────────────────────────────

    private static final String SPOT_WS_URL = "wss://openapi.bitunix.com:443/ws-api/v1/spot";

    // ── Timeouts / back-off ───────────────────────────────────────────────────

    private static final long   RPC_TIMEOUT_MS         = 10_000L;
    private static final long   RECONNECT_MIN_DELAY_MS = 1_000L;
    private static final long   RECONNECT_MAX_DELAY_MS = 60_000L;

    // ── Dependencies ──────────────────────────────────────────────────────────

    private final OkHttpClient              wsHttpClient;
    private final Gson                      gson;
    private final BitUnixSignatureService   signer;
    private final TradingProperties         props;
    private final ApplicationEventPublisher eventPublisher;

    // ── State ─────────────────────────────────────────────────────────────────

    private final AtomicReference<WebSocket>                          currentWs    = new AtomicReference<>();
    private final AtomicBoolean                                       running      = new AtomicBoolean(false);
    private final AtomicInteger                                       reconnectSec = new AtomicInteger(1);
    /** Pending RPC futures keyed by request {@code id}. */
    private final ConcurrentHashMap<String, CompletableFuture<JsonObject>> pending = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "bitunix-spot-ws-scheduler");
                t.setDaemon(true);
                return t;
            });

    private volatile ScheduledFuture<?> pingTask;

    // ── Constructor ───────────────────────────────────────────────────────────

    public BitUnixSpotWsClient(
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
     * Lazily connects on first RPC call.  Call {@link #ensureConnected()} before
     * any {@code send*()} method to guarantee the WebSocket is open.
     *
     * <p>Alternatively, the application can explicitly call this method at startup;
     * it is idempotent (no-op if already connected).</p>
     */
    public synchronized void ensureConnected() {
        if (currentWs.get() != null) return;
        if (props.getBitunixApiKey() == null || props.getBitunixApiKey().isBlank()) {
            log.warn("BitUnix API key not configured — Spot WS client will NOT connect.");
            return;
        }
        running.set(true);
        connect();
    }

    @PreDestroy
    public void stop() {
        log.info("Shutting down BitUnix Spot WS client");
        running.set(false);
        cancelPingTask();
        WebSocket ws = currentWs.getAndSet(null);
        if (ws != null) ws.close(1000, "service shutdown");
        failAllPending(new IllegalStateException("Spot WS client is shutting down"));
        scheduler.shutdownNow();
    }

    // ── Public RPC API ────────────────────────────────────────────────────────

    /**
     * Fetch spot account balances via WS RPC ({@code user.account}).
     *
     * @param credential per-user API credential
     * @return raw JSON reply {@code data} object; callers parse the specific fields
     * @throws RuntimeException if the RPC times out or returns an error code
     */
    public JsonObject getUserAccount(BitUnixCredential credential) {
        JsonObject params = buildAuthParams(credential);
        return sendRpc(METHOD_USER_ACCOUNT, params);
    }

    /**
     * Place a spot order via WS RPC ({@code order.place_order}).
     *
     * <p>Numeric enum encoding: {@code side} 1=Sell / 2=Buy;
     * {@code type} 1=Limit / 2=Market — consistent with
     * {@link com.mst.matt.tradingservice.bitunix.spot.BitUnixSpotOrderClient}.</p>
     *
     * @param symbol     trading symbol (e.g., {@code "BTCUSDT"})
     * @param side       1=Sell, 2=Buy
     * @param type       1=Limit, 2=Market
     * @param volume     order quantity as string
     * @param price      limit price as string; pass {@code null} for market orders
     * @param credential per-user API credential
     * @return raw JSON reply; orderId is at {@code data.orderId}
     */
    public JsonObject placeOrder(String symbol, int side, int type,
                                  String volume, @Nullable String price,
                                  BitUnixCredential credential) {
        JsonObject params = buildAuthParams(credential);
        params.addProperty("symbol", symbol);
        params.addProperty("side",   side);
        params.addProperty("type",   type);
        params.addProperty("volume", volume);
        if (price != null && !price.isBlank()) params.addProperty("price", price);
        JsonObject reply = sendRpc(METHOD_PLACE_ORDER, params);

        // Publish OrderStatusUpdated for the initial PENDING state
        if (reply != null && reply.has("orderId")) {
            String orderId = reply.get("orderId").getAsString();
            eventPublisher.publishEvent(OrderStatusUpdated.builder()
                    .orderId(orderId)
                    .brokerType(BrokerType.BITUNIX)
                    .symbol(symbol)
                    .newStatus("PENDING")
                    .eventTime(Instant.now())
                    .serverTime(Instant.now())
                    .build());
        }
        return reply;
    }

    /**
     * Cancel a spot order via WS RPC ({@code order.cancel}).
     *
     * @param orderId    broker-assigned order identifier
     * @param symbol     trading symbol (required by BitUnix)
     * @param credential per-user API credential
     * @return raw JSON reply data object
     */
    public JsonObject cancelOrder(String orderId, String symbol,
                                   BitUnixCredential credential) {
        JsonObject params = buildAuthParams(credential);
        params.addProperty("orderId", orderId);
        params.addProperty("symbol",  symbol);
        JsonObject reply = sendRpc(METHOD_CANCEL_ORDER, params);

        // Publish CANCEL_REQUESTED; WS reply or REST confirm provides final state
        eventPublisher.publishEvent(OrderStatusUpdated.builder()
                .orderId(orderId)
                .brokerType(BrokerType.BITUNIX)
                .symbol(symbol)
                .newStatus("CANCEL_REQUESTED")
                .eventTime(Instant.now())
                .serverTime(Instant.now())
                .build());
        return reply;
    }

    /**
     * List pending (open) spot orders via WS RPC ({@code order.pending.list}).
     * Used as a poll-fallback to reconcile order state when WS push is unavailable.
     *
     * @param symbol     optional symbol filter; pass {@code null} to list all
     * @param credential per-user API credential
     * @return raw JSON reply data object (array of pending orders)
     */
    public JsonObject listPendingOrders(@Nullable String symbol,
                                         BitUnixCredential credential) {
        JsonObject params = buildAuthParams(credential);
        if (symbol != null && !symbol.isBlank()) params.addProperty("symbol", symbol);
        return sendRpc(METHOD_PENDING_LIST, params);
    }

    // ── Internal RPC mechanics ────────────────────────────────────────────────

    /**
     * Sends an RPC request and blocks (up to {@link #RPC_TIMEOUT_MS} ms) for
     * the correlated reply.
     *
     * @param method the BitUnix WS method name
     * @param params the fully-built params object (must include auth fields)
     * @return the {@code data} field of the reply, or {@code null} if the
     *         reply carries no data
     * @throws RuntimeException on timeout or non-zero reply code
     */
    private JsonObject sendRpc(String method, JsonObject params) {
        ensureConnected();
        WebSocket ws = currentWs.get();
        if (ws == null) {
            throw new IllegalStateException(
                    "Spot WS not connected — cannot call method=" + method);
        }

        String id = UUID.randomUUID().toString();
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        pending.put(id, future);

        JsonObject frame = new JsonObject();
        frame.addProperty("id",     id);
        frame.addProperty("method", method);
        frame.add("params", params);

        String frameStr = gson.toJson(frame);
        log.debug("Spot WS → method={} id={}", method, id);
        ws.send(frameStr);

        try {
            JsonObject reply = future.get(RPC_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            int code = reply != null && reply.has("code") ? reply.get("code").getAsInt() : 0;
            if (code != 0) {
                String msg = reply.has("msg") ? reply.get("msg").getAsString() : "";
                throw new RuntimeException(
                        "Spot WS RPC error: method=" + method + " code=" + code + " msg=" + msg);
            }
            return reply != null && reply.has("data") ? reply.getAsJsonObject("data") : null;
        } catch (TimeoutException e) {
            pending.remove(id);
            throw new RuntimeException("Spot WS RPC timeout for method=" + method, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pending.remove(id);
            throw new RuntimeException("Spot WS RPC interrupted for method=" + method, e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new RuntimeException("Spot WS RPC execution error for method=" + method,
                    e.getCause());
        }
    }

    /** Builds the auth sub-object required in every Spot WS RPC {@code params}. */
    private JsonObject buildAuthParams(BitUnixCredential credential) {
        String nonce     = signer.generateNonce();
        String timestamp = signer.generateTimestamp();
        // For Spot WS, the sign is the WS login variant (nonce+ts+apiKey, no body)
        String sign      = signer.signWsLogin(nonce, timestamp, credential);

        JsonObject auth = new JsonObject();
        auth.addProperty("apiKey",    credential.apiKey());
        auth.addProperty("timestamp", timestamp);
        auth.addProperty("nonce",     nonce);
        auth.addProperty("sign",      sign);
        return auth;
    }

    // ── Message dispatch ──────────────────────────────────────────────────────

    private void handleMessage(String text) {
        try {
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();

            // Heartbeat pong
            if (json.has("op") && "pong".equals(json.get("op").getAsString())) {
                log.trace("Spot WS pong received");
                return;
            }

            // Correlated RPC reply (has "id")
            if (json.has("id")) {
                String id = json.get("id").getAsString();
                CompletableFuture<JsonObject> future = pending.remove(id);
                if (future != null) {
                    log.debug("Spot WS ← reply id={} code={}",
                            id, json.has("code") ? json.get("code").getAsInt() : "?");
                    future.complete(json);
                } else {
                    log.warn("Spot WS: reply for unknown id={} (timed out?)", id);
                }
                return;
            }

            log.debug("Spot WS: unhandled frame: {}", text);

        } catch (Exception e) {
            log.error("Error handling Spot WS message: {} — {}", e.getMessage(), text, e);
        }
    }

    // ── Connect / ping / reconnect ────────────────────────────────────────────

    private void connect() {
        log.info("Connecting to BitUnix Spot WS: {}", SPOT_WS_URL);
        Request req = new Request.Builder().url(SPOT_WS_URL).build();
        wsHttpClient.newWebSocket(req, new SpotWsListener());
    }

    private void schedulePing(WebSocket ws) {
        cancelPingTask();
        int intervalSec = props.getWsPingIntervalSeconds();
        pingTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                ws.send("{\"op\":\"ping\",\"ping\":" + System.currentTimeMillis() + "}");
            } catch (Exception e) {
                log.warn("Spot WS ping failed: {}", e.getMessage());
            }
        }, intervalSec, intervalSec, TimeUnit.SECONDS);
    }

    private void cancelPingTask() {
        if (pingTask != null && !pingTask.isCancelled()) pingTask.cancel(false);
    }

    private void scheduleReconnect() {
        if (!running.get()) return;
        cancelPingTask();
        failAllPending(new RuntimeException("Spot WS disconnected — reconnecting"));

        long delay = Math.min((long) reconnectSec.get() * 1000L, RECONNECT_MAX_DELAY_MS);
        reconnectSec.updateAndGet(d -> (int) Math.min(d * 2L, 64));
        log.info("Scheduling Spot WS reconnect in {} ms", delay);
        scheduler.schedule(this::connect, delay, TimeUnit.MILLISECONDS);
    }

    private void failAllPending(Throwable cause) {
        pending.forEach((id, future) -> future.completeExceptionally(cause));
        pending.clear();
    }

    // ── Inner WS listener ─────────────────────────────────────────────────────

    private class SpotWsListener extends WebSocketListener {

        @Override
        public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
            log.info("BitUnix Spot WS connected ({})", response.code());
            currentWs.set(webSocket);
            reconnectSec.set(1);  // reset back-off
            schedulePing(webSocket);
        }

        @Override
        public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
            log.trace("Spot WS ← {}", text);
            handleMessage(text);
        }

        @Override
        public void onClosing(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
            log.warn("Spot WS closing: code={} reason={}", code, reason);
            webSocket.close(1000, null);
        }

        @Override
        public void onClosed(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
            log.warn("Spot WS closed: code={} reason={}", code, reason);
            currentWs.set(null);
            scheduleReconnect();
        }

        @Override
        public void onFailure(@NotNull WebSocket webSocket,
                              @NotNull Throwable t,
                              @Nullable Response response) {
            log.error("Spot WS failure: {} (response={})",
                    t.getMessage(),
                    response != null ? response.code() : "none", t);
            currentWs.set(null);
            scheduleReconnect();
        }
    }
}
