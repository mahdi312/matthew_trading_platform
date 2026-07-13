package com.mst.matt.alertservice.notification;

import com.mst.matt.contracts.dto.UserPreferencesDto;
import org.springframework.stereotype.Component;

/**
 * Resolves the {@link UserPreferencesDto} passed to every
 * {@link com.mst.matt.contracts.notification.NotificationChannel} for a
 * given user.
 *
 * <h3>Current implementation (Step 4.75)</h3>
 * <p>{@code identity-service} does not yet expose a per-user notification
 * preferences endpoint, so this resolver returns a conservative default:
 * in-app delivery enabled, email/Telegram disabled (since no destination is
 * known for either). This keeps the pipeline fully functional end-to-end
 * without inventing cross-service persistence that belongs elsewhere.</p>
 *
 * <h3>Planned evolution</h3>
 * <p>Once {@code identity-service} grows a notification-preferences
 * endpoint (email address + opt-in flags, Telegram chat-id linking), this
 * class should call it via the Gateway/service discovery instead of
 * returning a static default — no other class in {@code alert-service}
 * needs to change, since everything downstream depends only on
 * {@link UserPreferencesDto}.</p>
 */
@Component
public class UserPreferencesResolver {

    public UserPreferencesDto resolve(Long userId) {
        return UserPreferencesDto.builder()
                .userId(userId)
                .inAppEnabled(true)
                .emailEnabled(false)
                .email(null)
                .telegramEnabled(false)
                .telegramChatId(null)
                .build();
    }
}
