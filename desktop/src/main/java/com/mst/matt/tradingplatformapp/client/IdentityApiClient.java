package com.mst.matt.tradingplatformapp.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;

/**
 * Typed HTTP client for the {@code identity-service} exposed through the Gateway.
 *
 * <p>Covers the two public unauthenticated actions (login, register) and the
 * one authenticated action (fetch current-user) needed by the desktop app in
 * Phase 2, Step 12.  All calls are synchronous from the caller's perspective:
 * they block the calling (virtual) thread so JavaFX controllers need not change
 * their threading model — keep network calls on {@code Thread.ofVirtual()}.</p>
 *
 * <p>Only the first domain — identity — is wired here.  Later steps add
 * {@code TradeApiClient}, {@code MarketApiClient}, etc., one at a time.</p>
 */
@Slf4j
@Component
public class IdentityApiClient {

    private final WebClient webClient;
    private final TokenStore tokenStore;

    public IdentityApiClient(WebClient gatewayWebClient, TokenStore tokenStore) {
        this.webClient  = gatewayWebClient;
        this.tokenStore = tokenStore;
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    /**
     * Response body from {@code POST /api/auth/login}.
     * Only the fields the desktop app actually needs are mapped.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LoginResponse {
        private String token;
        private String username;
        private String displayName;
        private String role;          // e.g. "ADMIN", "REGULAR_USER"
        private Long   userId;
    }

    /**
     * Response body from {@code POST /api/auth/register}.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RegisterResponse {
        private Long   userId;
        private String username;
        private String displayName;
        private String role;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Calls {@code POST /api/auth/login} (public — no JWT needed).
     *
     * <p>On success the returned JWT is stored in {@link TokenStore} so all
     * subsequent API calls are automatically authenticated.</p>
     *
     * @param username plain username
     * @param password plain password
     * @return {@link LoginResponse} wrapped in Optional, or empty on bad credentials / server error
     */
    public Optional<LoginResponse> login(String username, String password) {
        try {
            LoginResponse response = webClient.post()
                    .uri("/api/auth/login")
                    .bodyValue(Map.of("username", username, "password", password))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, resp -> {
                        log.debug("Login rejected with status {}", resp.statusCode());
                        return Mono.empty(); // handled as empty Optional below
                    })
                    .bodyToMono(LoginResponse.class)
                    .block();

            if (response != null && response.getToken() != null) {
                tokenStore.setToken(response.getToken());
                log.info("Logged in as '{}' (role={})", response.getUsername(), response.getRole());
                return Optional.of(response);
            }
            return Optional.empty();

        } catch (Exception ex) {
            log.warn("Login call failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Calls {@code POST /api/auth/register} (public — no JWT needed).
     *
     * @param username    desired username
     * @param password    desired password
     * @param displayName human-readable display name
     * @param role        role string, e.g. {@code "REGULAR_USER"}
     * @return the created user's summary
     * @throws IllegalArgumentException if the server returns 409 Conflict (username taken)
     *                                  or 400 Bad Request (validation error)
     */
    public RegisterResponse register(String username, String password,
                                     String displayName, String role) {
        try {
            RegisterResponse response = webClient.post()
                    .uri("/api/auth/register")
                    .bodyValue(Map.of(
                            "username",    username,
                            "password",    password,
                            "displayName", displayName,
                            "role",        role))
                    .retrieve()
                    .onStatus(status -> status.value() == 409, resp ->
                            resp.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(
                                        new IllegalArgumentException(
                                                "Username '" + username + "' is already taken."))))
                    .onStatus(HttpStatusCode::is4xxClientError, resp ->
                            resp.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(
                                        new IllegalArgumentException(
                                                "Registration failed: " + body))))
                    .bodyToMono(RegisterResponse.class)
                    .block();

            if (response == null) {
                throw new IllegalArgumentException("Registration returned an empty response.");
            }
            log.info("Registered new user '{}' (role={})", response.getUsername(), response.getRole());
            return response;

        } catch (IllegalArgumentException ex) {
            throw ex; // re-throw domain errors as-is for the controller to display
        } catch (Exception ex) {
            log.warn("Register call failed: {}", ex.getMessage());
            throw new IllegalArgumentException("Could not connect to the server. Please try again.");
        }
    }

    /**
     * Calls {@code GET /api/auth/me} — requires a valid JWT in {@link TokenStore}.
     *
     * @return current user info, or empty if not authenticated
     */
    public Optional<LoginResponse> currentUser() {
        if (!tokenStore.hasToken()) return Optional.empty();
        try {
            LoginResponse response = webClient.get()
                    .uri("/api/auth/me")
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, resp -> Mono.empty())
                    .bodyToMono(LoginResponse.class)
                    .block();
            return Optional.ofNullable(response);
        } catch (Exception ex) {
            log.warn("currentUser call failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Clears the locally stored JWT (logout).
     * Does NOT call the server; the server-side token is stateless (JWT) so
     * clearing it client-side is sufficient.
     */
    public void logout() {
        tokenStore.clear();
        log.info("JWT cleared — user logged out.");
    }
}
