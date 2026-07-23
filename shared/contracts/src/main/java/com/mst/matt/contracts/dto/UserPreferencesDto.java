package com.mst.matt.contracts.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cross-service snapshot of a user's notification preferences, passed to every
 * {@code NotificationChannel} implementation alongside the triggering event so
 * that each channel can decide whether/how to deliver without querying
 * {@code identity-service} itself.
 *
 * <p>Owned conceptually by {@code identity-service} (as part of the user's
 * profile) but kept here, DTO-only, so {@code alert-service} and
 * {@code notification-service} can both depend on the shape without coupling
 * to identity-service's persistence model.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPreferencesDto {

    /** Platform user id these preferences belong to. */
    private Long userId;

    // ── In-app (WebSocket/STOMP) ─────────────────────────────────────────────

    /** Whether in-app toast/notification-bell delivery is enabled. */
    private boolean inAppEnabled;

    // ── Email ─────────────────────────────────────────────────────────────────

    /** Whether email delivery is enabled for this user. */
    private boolean emailEnabled;

    /** Destination email address; {@code null}/blank disables delivery even if enabled. */
    private String email;

    // ── Telegram ──────────────────────────────────────────────────────────────

    /** Whether Telegram delivery is enabled for this user. */
    private boolean telegramEnabled;

    /**
     * Telegram chat id the bot should message; {@code null}/blank disables
     * delivery even if enabled (user has not linked/started the bot yet).
     */
    private String telegramChatId;
}
