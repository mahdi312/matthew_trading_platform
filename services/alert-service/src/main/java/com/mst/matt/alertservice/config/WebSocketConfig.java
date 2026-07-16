package com.mst.matt.alertservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP-over-WebSocket configuration for real-time, in-app alert delivery.
 *
 * <h3>Endpoint</h3>
 * <p>Clients connect (via SockJS fallback) to {@code /ws} — routed through
 * the API Gateway like every other client-facing endpoint.</p>
 *
 * <h3>Destinations</h3>
 * <ul>
 *   <li>{@code /topic/alerts/{userId}} — server → client broadcast used by
 *       {@code InAppNotificationChannel} to push a fired
 *       {@code AlertTriggeredEventDto} to the owning user's session(s). The
 *       frontend's notification-bell component (this step) subscribes to its
 *       own {@code userId}'s topic after connecting.</li>
 * </ul>
 *
 * <p>No {@code /app/**} application-destination prefix is registered yet —
 * this channel is currently server → client only (no inbound STOMP messages
 * are expected from the browser for alerts).</p>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
