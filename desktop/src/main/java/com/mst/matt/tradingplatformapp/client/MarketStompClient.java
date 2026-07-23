package com.mst.matt.tradingplatformapp.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * STOMP/WebSocket client for live price ticks from {@code market-service}.
 *
 * <h3>Phase 2, Step 12 — point 5</h3>
 * <p>Connects to the STOMP endpoint exposed by {@code market-service} at
 * {@code /ws-market} (routed through the Gateway).  Subscribes to
 * {@code /topic/prices} to receive live OHLCV tick updates.</p>
 *
 * <p>Listeners registered via {@link #addPriceListener} are called on the
 * STOMP delivery thread.  JavaFX controllers must wrap UI updates in
 * {@code Platform.runLater()}.</p>
 *
 * <h3>Activation</h3>
 * <p>Call {@link #connect(String)} after the user logs in (passing the JWT
 * from {@link TokenStore}).  Call {@link #disconnect()} on logout.</p>
 */
@Slf4j
@Component
public class MarketStompClient {

    @Value("${gateway.base-url:http://localhost:8080}")
    private String gatewayBaseUrl;

    private final TokenStore tokenStore;
    private final ObjectMapper objectMapper;

    private StompSession session;
    private final List<Consumer<PriceTick>> priceListeners = new CopyOnWriteArrayList<>();

    public MarketStompClient(TokenStore tokenStore, ObjectMapper objectMapper) {
        this.tokenStore   = tokenStore;
        this.objectMapper = objectMapper;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Register a listener that will be called on each incoming price tick. */
    public void addPriceListener(Consumer<PriceTick> listener) {
        priceListeners.add(listener);
    }

    /** Remove a price listener. */
    public void removePriceListener(Consumer<PriceTick> listener) {
        priceListeners.remove(listener);
    }

    /**
     * Connects to the market-service STOMP endpoint and subscribes to live ticks.
     * Must be called after the user has logged in (token available in TokenStore).
     *
     * @param jwt current user's JWT (from TokenStore after login)
     */
    public void connect(String jwt) {
        if (session != null && session.isConnected()) {
            log.debug("MarketStompClient already connected — skipping reconnect");
            return;
        }

        // Convert http(s):// base URL to ws(s):// for WebSocket
        String wsUrl = gatewayBaseUrl
                .replace("https://", "wss://")
                .replace("http://",  "ws://")
                + "/ws-market/websocket";

        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter(objectMapper));

        // Pass JWT in the STOMP CONNECT headers for gateway auth
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + jwt);

        stompClient.connectAsync(wsUrl, null, connectHeaders, new StompSessionHandlerAdapter() {

            @Override
            public void afterConnected(StompSession s, StompHeaders connectedHeaders) {
                log.info("MarketStompClient connected to {}", wsUrl);
                session = s;
                // Subscribe to the live price broadcast topic
                s.subscribe("/topic/prices", new StompFrameHandler() {
                    @Override public Type getPayloadType(StompHeaders h) { return PriceTick.class; }
                    @Override public void handleFrame(StompHeaders h, Object payload) {
                        if (payload instanceof PriceTick tick) {
                            priceListeners.forEach(l -> {
                                try { l.accept(tick); }
                                catch (Exception ex) {
                                    log.warn("Price listener threw: {}", ex.getMessage());
                                }
                            });
                        }
                    }
                });
            }

            @Override
            public void handleTransportError(StompSession s, Throwable ex) {
                log.warn("MarketStompClient transport error: {}", ex.getMessage());
            }

            @Override
            public void handleException(StompSession s, StompCommand cmd,
                                        StompHeaders h, byte[] payload, Throwable ex) {
                log.warn("MarketStompClient STOMP exception [{}]: {}", cmd, ex.getMessage());
            }
        });
    }

    /** Disconnects the STOMP session (call on logout). */
    public void disconnect() {
        if (session != null && session.isConnected()) {
            session.disconnect();
            log.info("MarketStompClient disconnected");
        }
        session = null;
    }

    /** @return true when the STOMP session is active. */
    public boolean isConnected() {
        return session != null && session.isConnected();
    }

    // ── DTO ──────────────────────────────────────────────────────────────────

    /**
     * Minimal price-tick DTO matching the payload broadcast by
     * {@code market-service}'s STOMP topic.
     */
    @lombok.Data
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public static class PriceTick {
        private String     symbol;
        private java.math.BigDecimal price;
        private java.math.BigDecimal changePct24h;
        private boolean    up;
        private long       timestamp;
    }
}
