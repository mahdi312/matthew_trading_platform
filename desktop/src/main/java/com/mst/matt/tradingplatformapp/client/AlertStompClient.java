package com.mst.matt.tradingplatformapp.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * STOMP/WebSocket client for in-app alert notifications from {@code alert-service}.
 *
 * <h3>Phase 2, Step 12 — point 5</h3>
 * <p>Connects to the STOMP endpoint exposed by {@code alert-service} at
 * {@code /ws} (routed through the Gateway) and subscribes to the per-user
 * alert topic {@code /user/queue/alerts}.  Triggered alerts are delivered
 * to listeners registered via {@link #addAlertListener}.</p>
 *
 * <p>UI code must wrap any JavaFX state changes in {@code Platform.runLater()}.</p>
 */
@Slf4j
@Component
public class AlertStompClient {

    @Value("${gateway.base-url:http://localhost:8080}")
    private String gatewayBaseUrl;

    private final ObjectMapper objectMapper;

    private StompSession session;
    private final List<Consumer<AlertNotification>> alertListeners = new CopyOnWriteArrayList<>();

    public AlertStompClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Register a listener for incoming alert notifications. */
    public void addAlertListener(Consumer<AlertNotification> listener) {
        alertListeners.add(listener);
    }

    /** Remove an alert listener. */
    public void removeAlertListener(Consumer<AlertNotification> listener) {
        alertListeners.remove(listener);
    }

    /**
     * Connects to the alert-service STOMP endpoint.
     * Call after login; the JWT is used in the STOMP CONNECT headers so the
     * gateway's {@code GatewayJwtAuthFilter} can authenticate the upgrade request.
     *
     * @param jwt current user's JWT
     */
    public void connect(String jwt) {
        if (session != null && session.isConnected()) {
            log.debug("AlertStompClient already connected — skipping reconnect");
            return;
        }

        String wsUrl = gatewayBaseUrl
                .replace("https://", "wss://")
                .replace("http://",  "ws://")
                + "/ws/websocket";

        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter(objectMapper));

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + jwt);

        stompClient.connectAsync(wsUrl, null, connectHeaders, new StompSessionHandlerAdapter() {

            @Override
            public void afterConnected(StompSession s, StompHeaders connectedHeaders) {
                log.info("AlertStompClient connected to {}", wsUrl);
                session = s;
                // Per-user queue — the server routes triggered-alert messages here
                s.subscribe("/user/queue/alerts", new StompFrameHandler() {
                    @Override public Type getPayloadType(StompHeaders h) { return AlertNotification.class; }
                    @Override public void handleFrame(StompHeaders h, Object payload) {
                        if (payload instanceof AlertNotification n) {
                            alertListeners.forEach(l -> {
                                try { l.accept(n); }
                                catch (Exception ex) {
                                    log.warn("Alert listener threw: {}", ex.getMessage());
                                }
                            });
                        }
                    }
                });
            }

            @Override
            public void handleTransportError(StompSession s, Throwable ex) {
                log.warn("AlertStompClient transport error: {}", ex.getMessage());
            }

            @Override
            public void handleException(StompSession s, StompCommand cmd,
                                        StompHeaders h, byte[] payload, Throwable ex) {
                log.warn("AlertStompClient STOMP exception [{}]: {}", cmd, ex.getMessage());
            }
        });
    }

    /** Disconnects the STOMP session. */
    public void disconnect() {
        if (session != null && session.isConnected()) {
            session.disconnect();
            log.info("AlertStompClient disconnected");
        }
        session = null;
    }

    public boolean isConnected() {
        return session != null && session.isConnected();
    }

    // ── DTO ──────────────────────────────────────────────────────────────────

    /**
     * Alert notification pushed by {@code alert-service} when a price alert
     * crosses its threshold.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AlertNotification {
        private Long   alertId;
        private String symbol;
        private String message;
        private String type;        // e.g. "PRICE_ABOVE", "PRICE_BELOW"
        private double triggerPrice;
        private long   timestamp;
    }
}
