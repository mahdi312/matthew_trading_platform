package com.mst.matt.contracts.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Cross-service representation of an authenticated user, extracted from the
 * validated JWT by the API Gateway and propagated as request-scoped context
 * to downstream services via HTTP headers (e.g., {@code X-User-Id},
 * {@code X-User-Roles}).
 *
 * <p>Downstream services reconstruct this DTO from the headers injected by
 * the gateway — they never validate the JWT themselves.</p>
 *
 * <p>No passwords, hashes, or sensitive credentials are included here.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPrincipalDto {

    /**
     * Stable numeric identifier of the platform user (maps to {@code AppUser.id}).
     */
    private Long userId;

    /**
     * Unique login name (maps to {@code AppUser.username}).
     */
    private String username;

    /**
     * Human-readable display name.
     */
    private String displayName;

    /**
     * Role name as stored on the user (e.g., "ADMIN", "PRO_USER", "REGULAR_USER").
     * Single role per user on this platform.
     */
    private String role;

    /**
     * Granted Spring Security authorities derived from the role
     * (e.g., "ROLE_ADMIN", "ROLE_PRO_USER").
     * Kept as a set to allow future multi-authority expansion.
     */
    private Set<String> authorities;

    /**
     * OAuth2 provider used to authenticate this session, if applicable
     * (e.g., "google", "local" for username/password).
     */
    private String authProvider;
}
