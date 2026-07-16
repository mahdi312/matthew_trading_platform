package com.mst.matt.identityservice.controller;

import com.mst.matt.identityservice.dto.AuthResponse;
import com.mst.matt.identityservice.dto.LoginRequest;
import com.mst.matt.identityservice.dto.RegisterRequest;
import com.mst.matt.identityservice.model.AppUser;
import com.mst.matt.identityservice.security.JwtUtil;
import com.mst.matt.identityservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Authentication REST controller.
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code POST /auth/login}  — username/password login, returns JWT.</li>
 *   <li>{@code POST /auth/register} — new local account registration, returns JWT.</li>
 *   <li>{@code GET  /auth/oauth2/google/callback} — informational endpoint explaining
 *       the OAuth2 flow; the actual callback is handled by Spring Security internally
 *       and the JWT is delivered by {@link com.mst.matt.identityservice.security.OAuth2AuthenticationSuccessHandler}.</li>
 *   <li>{@code GET  /auth/oauth2/success} — browser landing page after OAuth2 login;
 *       receives the JWT as a query parameter and surfaces it to the frontend SPA.</li>
 * </ul>
 *
 * <p>All sensitive operations (password verification, BCrypt hashing) are delegated
 * to {@link UserService} — this controller is intentionally thin.</p>
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    // ── POST /auth/login ──────────────────────────────────────────────────────

    /**
     * Authenticate with username + password and receive a JWT.
     *
     * @param request login credentials
     * @return 200 with {@link AuthResponse} on success, 401 on bad credentials
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            AppUser user = userService.authenticate(request.getUsername(), request.getPassword());
            String  jwt  = jwtUtil.generateToken(user);

            log.info("Login success for userId={}", user.getId());

            return ResponseEntity.ok(AuthResponse.builder()
                    .token(jwt)
                    .expiresIn(jwtUtil.getExpirationSeconds())
                    .userId(user.getId())
                    .username(user.getUsername())
                    .role(user.getRole().name())
                    .build());

        } catch (Exception ex) {
            log.warn("Login failed for username='{}': {}", request.getUsername(), ex.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid username or password"));
        }
    }

    // ── POST /auth/register ───────────────────────────────────────────────────

    /**
     * Register a new local account and receive a JWT for immediate use.
     *
     * @param request registration payload
     * @return 201 with {@link AuthResponse} on success, 400 if username/email taken
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            AppUser user = userService.register(request);
            String  jwt  = jwtUtil.generateToken(user);

            log.info("Registration success for userId={}", user.getId());

            return ResponseEntity.status(HttpStatus.CREATED).body(AuthResponse.builder()
                    .token(jwt)
                    .expiresIn(jwtUtil.getExpirationSeconds())
                    .userId(user.getId())
                    .username(user.getUsername())
                    .role(user.getRole().name())
                    .build());

        } catch (IllegalArgumentException ex) {
            log.warn("Registration failed: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    // ── GET /auth/oauth2/google/callback ──────────────────────────────────────

    /**
     * Informational endpoint describing the Google OAuth2 flow.
     *
     * <p>The actual OAuth2 callback URL is handled entirely by Spring Security's
     * OAuth2 Client internals ({@code /login/oauth2/code/google}).  After
     * Google redirects back, Spring Security invokes
     * {@link com.mst.matt.identityservice.security.OAuth2AuthenticationSuccessHandler}
     * which issues the JWT and redirects to {@code /auth/oauth2/success?token=...}.</p>
     *
     * <p>To initiate Google login, the client must navigate to:
     * {@code /oauth2/authorization/google}</p>
     */
    @GetMapping("/oauth2/google/callback")
    public ResponseEntity<Map<String, String>> googleCallbackInfo() {
        return ResponseEntity.ok(Map.of(
                "message",
                "Google OAuth2 is handled by Spring Security. "
                + "Initiate login at: GET /oauth2/authorization/google"
        ));
    }

    // ── GET /auth/oauth2/success ──────────────────────────────────────────────

    /**
     * Browser landing page after a successful Google OAuth2 login.
     *
     * <p>The {@link com.mst.matt.identityservice.security.OAuth2AuthenticationSuccessHandler}
     * redirects here with {@code ?token=<jwt>}.  In a real SPA the frontend
     * JavaScript reads the token from the URL and stores it (e.g., localStorage).</p>
     */
    @GetMapping("/oauth2/success")
    public ResponseEntity<Map<String, String>> oauth2Success(
            @RequestParam(required = false) String token) {
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "No token received"));
        }
        // In a real deployment this would render an HTML page / redirect to the SPA.
        return ResponseEntity.ok(Map.of(
                "token", token,
                "message", "OAuth2 authentication successful. Store this token securely."
        ));
    }
}
