package com.mst.matt.contracts.notification;

import com.mst.matt.contracts.dto.AlertTriggeredEventDto;
import com.mst.matt.contracts.dto.UserPreferencesDto;

/**
 * Unified contract every alert-delivery channel must implement.
 *
 * <h3>Design rules (Step 4.75)</h3>
 * <ul>
 *   <li>{@code alert-service} depends only on this interface — never on a
 *       concrete channel implementation or a third-party SDK (SMTP client,
 *       Telegram Bot API client, etc.) directly.</li>
 *   <li>Each implementation decides for itself, using {@code prefs}, whether
 *       it should actually deliver anything (e.g., the user disabled this
 *       channel, or has no destination configured for it).</li>
 *   <li>Implementations must be non-blocking-friendly: callers may invoke all
 *       registered channels for a single event in a fire-and-forget loop, so a
 *       slow/broken channel must not throw uncaught exceptions that would
 *       stop the remaining channels from running.</li>
 * </ul>
 *
 * <h3>Implementation locations</h3>
 * <ul>
 *   <li>{@code InAppNotificationChannel} — implemented fully in
 *       {@code alert-service} in Step 4.75 (direct WebSocket/STOMP broadcast,
 *       no Kafka dependency).</li>
 *   <li>{@code EmailNotificationChannel} / {@code TelegramNotificationChannel}
 *       — stubbed (no-op) in Step 4.75; real SMTP / Telegram Bot API wiring
 *       lands in {@code notification-service} in Step 7. Both implementation
 *       classes are expected to move/be reimplemented there without changing
 *       this interface or {@code alert-service}'s calling code.</li>
 * </ul>
 */
public interface NotificationChannel {

    /**
     * Deliver (or attempt to deliver) a triggered-alert notification through
     * this channel.
     *
     * @param event the alert-triggered event to notify about
     * @param prefs the target user's notification preferences; implementations
     *              must check the relevant enabled-flag/destination before
     *              sending anything
     */
    void send(AlertTriggeredEventDto event, UserPreferencesDto prefs);

    /**
     * Returns a short, stable channel name for logging/metrics
     * (e.g., "IN_APP", "EMAIL", "TELEGRAM").
     */
    String channelName();
}
