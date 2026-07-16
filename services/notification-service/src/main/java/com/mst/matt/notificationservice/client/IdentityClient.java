package com.mst.matt.notificationservice.client;

import com.mst.matt.contracts.dto.UserPreferencesDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client for identity-service's notification-preferences endpoint.
 *
 * <p>Same pattern as {@code trading-service}'s {@code FundamentalsClient} —
 * service name resolves via Eureka; the {@code url} property lets you override
 * for local/Docker environments without Eureka.</p>
 */
@FeignClient(
        name = "identity-service",
        url  = "${identity.service.url:}",
        fallback = IdentityClientFallback.class
)
public interface IdentityClient {

    @GetMapping("/api/profile/{userId}/preferences")
    UserPreferencesDto getPreferences(@PathVariable("userId") Long userId);
}