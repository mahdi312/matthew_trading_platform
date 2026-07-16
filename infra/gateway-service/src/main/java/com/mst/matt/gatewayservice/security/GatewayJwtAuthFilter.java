package com.mst.matt.gatewayservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Reactive (WebFlux) global gateway filter that validates the JWT on every
 * incoming request and injects user-identity headers for downstream services.
 *
 * <h3>Responsibility</h3>
 * <p>JWT is validated <em>once</em> here at the edge.  Downstream services
 * ({@code identity-service}, {@code market-service}, {@code trading-service},
 * {@code notification-service}, {@code alert-service}, {@code reference-data-service},
 * {@code ai-service}) trust these headers without re-validating the token themselves:</p>
 * <ul>
 *   <li>{@code X-User-Id}       — numeric AppUser.id</li>
 *   <li>{@code X-User-Name}     — username (JWT {@code sub} claim)</li>
 *   <li>{@code X-User-Role}     — role name (e.g., "REGULAR_USER")</li>
 *   <li>{@code X-Auth-Provider} — "LOCAL" or "GOOGLE"</li>
 * </ul>
 *
 * <h3>Public paths</h3>
 * <p>The following paths bypass JWT validation entirely:</p>
 * <ul>
 *   <li>{@code /api/auth/login}     — login endpoint (identity-service)</li>
 *   <li>{@code /api/auth/register}  — self-registration (identity-service)</li>
 *   <li>{@code /api/auth/oauth2/**} — OAuth2 callback flows (identity-service)</li>
 *   <li>{@code /oauth2/**}          — OAuth2 authorization redirect</li>
 *   <li>{@code /actuator/**}        — health / metrics endpoints</li>
 * </ul>
 *
 * <p>All other paths (including the rest of {@code /api/auth/**} such as
 * {@code /api/auth/me} and {@code /api/auth/logout}) <em>require</em> a valid JWT
 * so that downstream services always receive authenticated identity headers.</p>
 *
 * <h3>Algorithm</h3>
 * <p>HS256 with the same {@code jwt.secret} shared with {@code identity-service}.
 * Both services must be configured with the same secret — deliver it via
 * Spring Cloud Config Server or environment variable in production.</p>
 */
@Slf4j
@Component
public class GatewayJwtAuthFilter implements GlobalFilter, Ordered {

    /**
     * Exact-prefix paths that are allowed through without a valid JWT.
     *
     * <p>Only the specific unauthenticated actions are listed here.
     * {@code /api/auth/me}, {@code /api/auth/logout}, etc. still require a
     * JWT so that downstream identity-service always gets the identity headers.</p>
     */
    private static final List<String> PUBLIC_PATH_PREFIXES = List.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/oauth2",
            "/oauth2",
            "/actuator"
    );

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    private final SecretKey signingKey;

    public GatewayJwtAuthFilter(@Value("${jwt.secret}") String secret) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException(
                    "jwt.secret must be at least 32 characters long");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // ── Filter order — run before routing ────────────────────────────────────

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    // ── Filter logic ─────────────────────────────────────────────────────────

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        String correlationId = resolveCorrelationId(request);

        // ── Allow public paths through — still stamp the correlation ID ────
        if (isPublicPath(path)) {
            ServerHttpRequest stamped = request.mutate()
                    .header(CORRELATION_ID_HEADER, correlationId)
                    .build();
            return chain.filter(exchange.mutate().request(stamped).build());
        }

        // ── Extract Bearer token ───────────────────────────────────────────
        String token = extractBearerToken(request);
        if (token == null) {
            return unauthorised(exchange, "Missing Authorization header");
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String username     = claims.getSubject();
            String userId       = String.valueOf(claims.get("userId"));
            String role         = String.valueOf(claims.get("role"));
            String authProvider = String.valueOf(claims.get("authProvider"));

            ServerHttpRequest mutated = request.mutate()
                    .header("X-User-Id",       userId)
                    .header("X-User-Name",     username)
                    .header("X-User-Role",     role)
                    .header("X-Auth-Provider", authProvider)
                    .header(CORRELATION_ID_HEADER, correlationId)
                    .build();

            return chain.filter(exchange.mutate().request(mutated).build());

        } catch (JwtException ex) {
            log.debug("JWT validation failed for path={}: {}", path, ex.getMessage());
            return unauthorised(exchange, "Invalid or expired JWT");
        }
    }

    /** Forward the client's correlation ID if it sent one; otherwise generate a fresh one. */
    private String resolveCorrelationId(ServerHttpRequest request) {
        String existing = request.getHeaders().getFirst(CORRELATION_ID_HEADER);
        return (existing != null && !existing.isBlank()) ? existing : java.util.UUID.randomUUID().toString();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private boolean isPublicPath(String path) {
        return PUBLIC_PATH_PREFIXES.stream()
                .anyMatch(prefix -> path.startsWith(prefix));
    }

    private String extractBearerToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private Mono<Void> unauthorised(ServerWebExchange exchange, String reason) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("WWW-Authenticate", "Bearer realm=\"trading-platform\"");
        log.debug("Rejecting request: {}", reason);
        return response.setComplete();
    }
}
