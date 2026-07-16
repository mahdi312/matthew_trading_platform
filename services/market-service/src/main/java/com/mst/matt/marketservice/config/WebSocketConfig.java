package com.mst.matt.marketservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP-over-WebSocket configuration for market-service's live OHLCV/price
 * relay (Step 5.4) — mirrors alert-service's {@code WebSocketConfig}
 * precedent.
 *
 * <h3>Endpoint</h3>
 * <p>Clients connect (via SockJS fallback) to {@code /ws-market} — a
 * dedicated sub-path distinct from alert-service's {@code /ws}, since
 * Spring Cloud Gateway matches routes by literal path pattern and two
 * services can't both claim the same bare {@code /ws} predicate. Routed
 * through the Gateway via {@code infra/gateway-service}'s market-service
 * route ({@code Path=/api/market/**,/ws-market/**}).</p>
 *
 * <h3>Destinations</h3>
 * <ul>
 *   <li>{@code /topic/ohlcv/{symbol}/{interval}} — live OHLCV bar updates,
 *       relayed from BitUnix's {@code market_kline_*} WS channel by
 *       {@link com.mst.matt.marketservice.bitunix.ws.BitUnixWebSocketClient}.</li>
 *   <li>{@code /topic/price/{symbol}} — live price ticks, relayed from
 *       BitUnix's {@code ticker} WS channel.</li>
 * </ul>
 *
 * <h3>Inbound application messages</h3>
 * <p>{@code /app/**} is registered as the application-destination prefix
 * so the frontend can request a new subscription (which triggers this
 * service to subscribe the corresponding BitUnix channel if not already
 * active) — see {@code MarketStreamController}.</p>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-market")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
