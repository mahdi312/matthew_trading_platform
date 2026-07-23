package com.mst.matt.identityservice.security;

import com.mst.matt.identityservice.model.AppUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * Stateless JWT utility — generate and validate signed tokens.
 *
 * <p>Algorithm: HS256 (HMAC-SHA256) with a shared secret read from
 * {@code jwt.secret} in application properties.</p>
 *
 * <p>Claims embedded in every token:</p>
 * <ul>
 *   <li>{@code sub} — username</li>
 *   <li>{@code userId} — numeric AppUser.id</li>
 *   <li>{@code role} — role name (e.g., "REGULAR_USER")</li>
 *   <li>{@code authProvider} — "LOCAL" or "GOOGLE"</li>
 *   <li>{@code iat} / {@code exp} — issued-at / expiry timestamps</li>
 * </ul>
 *
 * <p>The same validation logic is replicated in {@code gateway-service}'s
 * {@code JwtAuthFilter}; both read the same {@code jwt.secret} property
 * (delivered via Config Server in production).</p>
 */
@Slf4j
@Component
public class JwtUtil {

    private final SecretKey signingKey;
    private final long expirationSeconds;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-seconds:86400}") long expirationSeconds) {

        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException(
                    "jwt.secret must be at least 32 characters long");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = expirationSeconds;
    }

    // ── Token generation ──────────────────────────────────────────────────────

    /**
     * Generate a signed JWT for an authenticated {@link AppUser}.
     *
     * @param user the authenticated user
     * @return compact, URL-safe JWT string
     */
    public String generateToken(AppUser user) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + expirationSeconds * 1_000L);

        return Jwts.builder()
                .subject(user.getUsername())
                .issuedAt(now)
                .expiration(expiry)
                .claims(Map.of(
                        "userId",       user.getId(),
                        "role",         user.getRole().name(),
                        "authProvider", user.getAuthProvider().name()
                ))
                .signWith(signingKey)
                .compact();
    }

    // ── Token validation ──────────────────────────────────────────────────────

    /**
     * Validate the token and return its claims.
     *
     * @param token compact JWT string (without "Bearer " prefix)
     * @return parsed {@link Claims}
     * @throws JwtException if the token is invalid or expired
     */
    public Claims validateAndExtractClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Convenience: extract the username ({@code sub} claim).
     *
     * @throws JwtException if the token is invalid
     */
    public String extractUsername(String token) {
        return validateAndExtractClaims(token).getSubject();
    }

    /**
     * Check whether the token is structurally valid and not expired.
     *
     * @return {@code true} if valid; {@code false} on any JWT error
     */
    public boolean isTokenValid(String token) {
        try {
            validateAndExtractClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("JWT validation failed: {}", ex.getMessage());
            return false;
        }
    }

    /** Returns the configured expiry duration in seconds. */
    public long getExpirationSeconds() {
        return expirationSeconds;
    }
}
