package com.mst.matt.identityservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response body returned by {@code POST /auth/login} and
 * {@code POST /auth/register} upon successful authentication.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {

    /** Signed JWT the client must include in all subsequent requests. */
    private String token;

    /** Token type — always "Bearer". */
    @Builder.Default
    private String tokenType = "Bearer";

    /** Expiry in seconds from the time of issuance. */
    private long expiresIn;

    /** User id of the authenticated subject. */
    private Long userId;

    /** Username of the authenticated subject. */
    private String username;

    /** Role of the authenticated subject (e.g., "REGULAR_USER"). */
    private String role;
}
