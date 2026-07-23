package com.mst.matt.alertservice.notification;

import com.mst.matt.contracts.dto.AlertTriggeredEventDto;
import com.mst.matt.contracts.dto.UserPreferencesDto;
import com.mst.matt.contracts.notification.NotificationChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Real, fully-working implementation of {@link NotificationChannel} for
 * in-app delivery — the only channel that does NOT wait for Step 7.
 *
 * <p>Broadcasts directly over STOMP to {@code /topic/alerts/{userId}} the
 * moment an alert fires, with no Kafka round-trip required: the same
 * process that detects the trigger ({@code AlertEvaluationService}) also
 * owns this channel and the {@link SimpMessagingTemplate} bean, so delivery
 * is synchronous and immediate.</p>
 *
 * <p>The frontend's global notification-bell/toast component (this step)
 * subscribes to its own user's topic after connecting via SockJS/STOMP —
 * see {@code WebSocketConfig} for the endpoint and destination prefix.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InAppNotificationChannel implements NotificationChannel {

    public static final String CHANNEL_NAME = "IN_APP";

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void send(AlertTriggeredEventDto event, UserPreferencesDto prefs) {
        if (prefs != null && !prefs.isInAppEnabled()) {
            log.debug("In-app channel disabled for userId={}, skipping alertId={}",
                    event.getUserId(), event.getAlertId());
            return;
        }
        try {
            String destination = "/topic/alerts/" + event.getUserId();
            messagingTemplate.convertAndSend(destination, event);
            log.info("In-app alert broadcast sent to {} (alertId={}, symbol={})",
                    destination, event.getAlertId(), event.getSymbol());
        } catch (Exception ex) {
            // A broken/slow channel must never prevent other channels from running.
            log.error("Failed to broadcast in-app alert for userId={}, alertId={}: {}",
                    event.getUserId(), event.getAlertId(), ex.getMessage(), ex);
        }
    }

    @Override
    public String channelName() {
        return CHANNEL_NAME;
    }
}
