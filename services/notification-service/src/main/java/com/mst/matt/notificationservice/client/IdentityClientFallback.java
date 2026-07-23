
package com.mst.matt.notificationservice.client;

import com.mst.matt.contracts.dto.UserPreferencesDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback for {@link IdentityClient} — a down identity-service must not take out
 * notification delivery entirely. Degrades to in-app-only (the one channel that
 * doesn't depend on this data, since alert-service's in-app WS channel is separate
 * from this consumer) rather than throwing.
 */
@Slf4j
@Component
public class IdentityClientFallback implements IdentityClient {

    @Override
    public UserPreferencesDto getPreferences(Long userId) {
        log.warn("IdentityClient fallback — identity-service unavailable for userId={}, " +
                "degrading to no email/Telegram delivery for this event", userId);
        return UserPreferencesDto.builder()
                .userId(userId)
                .inAppEnabled(false)
                .emailEnabled(false)
                .telegramEnabled(false)
                .build();
    }
}