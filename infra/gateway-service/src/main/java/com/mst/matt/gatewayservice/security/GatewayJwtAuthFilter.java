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
 * {@code notification-service}) trust these headers without re-validating the
 * token themselves:</p>
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
 *   <li>{@code /auth/login}</li>
 *   <li>{@code /auth/register}</li>
 *   <li>{@code /auth/oauth2/**}</li>
 *   <li>{@code /oauth2/**}</li>
 *   <li>{@code /actuator/**}</li>
 * </ul>
 *
 * <h3>Algorithm</h3>
 * <p>HS256 with the same {@code jwt.secret} shared with {@code identity-service}.
 * Both services must be configured with the same secret — deliver it via
 * Spring Cloud Config Server or environment variable in production.</p>
 */
@Slf4j
@Component
public class GatewayJwtAuthFilter implements GlobalFilter, Ordered {

    /** Paths that are allowed through without a valid JWT. */
    private static final List<String> PUBLIC_PATH_PREFIXES = List.of(
            "/auth/login",
            "/auth/register",
            "/auth/oauth2",
            "/oauth2",
            "/actuator"
    );

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

        // ── Allow public paths through ─────────────────────────────────────
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        // ── Extract Bearer token ───────────────────────────────────────────
        String token = extractBearerToken(request);
        if (token == null) {
            return unauthorised(exchange, "Missing Authorization header");
        }

        // ── Validate token and inject user-identity headers ────────────────
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

            // Mutate the request to add identity headers for downstream services
            ServerHttpRequest mutated = request.mutate()
                    .header("X-User-Id",       userId)
                    .header("X-User-Name",     username)
                    .header("X-User-Role",     role)
                    .header("X-Auth-Provider", authProvider)
                    .build();

            return chain.filter(exchange.mutate().request(mutated).build());

        } catch (JwtException ex) {
            log.debug("JWT validation failed for path={}: {}", path, ex.getMessage());
            return unauthorised(exchange, "Invalid or expired JWT");
        }
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
