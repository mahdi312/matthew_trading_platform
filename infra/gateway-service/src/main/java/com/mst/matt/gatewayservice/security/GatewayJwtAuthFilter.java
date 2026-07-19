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
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reactive global gateway filter that validates JWT (or embed API keys) and
 * injects user-identity headers for downstream services.
 *
 * <h3>Auth modes</h3>
 * <ul>
 *   <li><b>JWT Bearer</b> — default for the Angular app and desktop thin client</li>
 *   <li><b>Embed API key</b> — {@code X-Embed-Key} header (or {@code embedKey} query)
 *       matching {@code embed.api-keys}; grants read-only access to market/chart
 *       paths so third-party apps can host the detachable chart widget</li>
 * </ul>
 */
@Slf4j
@Component
public class GatewayJwtAuthFilter implements GlobalFilter, Ordered {

    private static final List<String> PUBLIC_PATH_PREFIXES = List.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/oauth2",
            "/oauth2",
            "/actuator"
    );

    /** Paths an embed API key may access (read-only market/chart surface). */
    private static final List<String> EMBED_ALLOWED_PREFIXES = List.of(
            "/api/market/ohlcv",
            "/api/market/symbols",
            "/api/indicators",
            "/api/charts/layouts"
    );

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String EMBED_KEY_HEADER = "X-Embed-Key";

    private final SecretKey signingKey;
    private final Set<String> embedApiKeys;

    public GatewayJwtAuthFilter(
            @Value("${jwt.secret}") String secret,
            @Value("${embed.api-keys:}") String embedKeysCsv) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException(
                    "jwt.secret must be at least 32 characters long");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.embedApiKeys = Arrays.stream(embedKeysCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        String correlationId = resolveCorrelationId(request);

        if ("OPTIONS".equalsIgnoreCase(request.getMethod().name()) || isPublicPath(path)) {
            ServerHttpRequest stamped = request.mutate()
                    .header(CORRELATION_ID_HEADER, correlationId)
                    .build();
            return chain.filter(exchange.mutate().request(stamped).build());
        }

        String embedKey = extractEmbedKey(request);
        if (embedKey != null) {
            if (!embedApiKeys.contains(embedKey)) {
                return unauthorised(exchange, "Invalid embed API key");
            }
            if (!isEmbedAllowedPath(path)) {
                return unauthorised(exchange, "Embed key not permitted for this path");
            }
            ServerHttpRequest stamped = request.mutate()
                    .header(CORRELATION_ID_HEADER, correlationId)
                    .header("X-Auth-Mode", "EMBED")
                    .header("X-User-Id", "0")
                    .header("X-User-Name", "embed")
                    .header("X-User-Role", "EMBED")
                    .header("X-Auth-Provider", "EMBED_KEY")
                    .build();
            return chain.filter(exchange.mutate().request(stamped).build());
        }

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
                    .header("X-Auth-Mode",     "JWT")
                    .header(CORRELATION_ID_HEADER, correlationId)
                    .build();

            return chain.filter(exchange.mutate().request(mutated).build());

        } catch (JwtException ex) {
            log.debug("JWT validation failed for path={}: {}", path, ex.getMessage());
            return unauthorised(exchange, "Invalid or expired JWT");
        }
    }

    private String resolveCorrelationId(ServerHttpRequest request) {
        String existing = request.getHeaders().getFirst(CORRELATION_ID_HEADER);
        return (existing != null && !existing.isBlank()) ? existing : java.util.UUID.randomUUID().toString();
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATH_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private boolean isEmbedAllowedPath(String path) {
        return EMBED_ALLOWED_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private String extractEmbedKey(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(EMBED_KEY_HEADER);
        if (StringUtils.hasText(header)) {
            return header.trim();
        }
        List<String> query = request.getQueryParams().get("embedKey");
        if (query != null && !query.isEmpty() && StringUtils.hasText(query.get(0))) {
            return query.get(0).trim();
        }
        return null;
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
