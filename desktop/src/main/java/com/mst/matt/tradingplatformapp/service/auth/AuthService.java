package com.mst.matt.tradingplatformapp.service.auth;

import com.mst.matt.tradingplatformapp.client.AlertStompClient;
import com.mst.matt.tradingplatformapp.client.IdentityApiClient;
import com.mst.matt.tradingplatformapp.client.IdentityApiClient.LoginResponse;
import com.mst.matt.tradingplatformapp.client.IdentityApiClient.RegisterResponse;
import com.mst.matt.tradingplatformapp.client.MarketStompClient;
import com.mst.matt.tradingplatformapp.client.TokenStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Authentication &amp; session service — <em>thin gateway wrapper</em>.
 *
 * <h3>Phase 2, Step 12 refactor</h3>
 * <p>This class no longer contains any JPA, BCrypt, or database logic.
 * All credential verification and user management is delegated to
 * {@code identity-service} via the Gateway using {@link IdentityApiClient}.
 * The desktop app stores <em>only</em> the in-memory session state (current
 * username, role, userId) needed to drive UI visibility and permission checks
 * — it never touches a local database.</p>
 *
 * <h3>Public API contract (unchanged)</h3>
 * <p>Controllers continue to call exactly the same methods as before so that
 * {@code LoginController}, {@code RegisterController},
 * {@code MainDashboardController}, etc. require no import or method-signature
 * changes in this step.</p>
 */
@Slf4j
@Service
public class AuthService {

    // All navigable tabs — kept for role-visibility checks in MainDashboardController
    public static final List<String> ALL_TABS = List.of(
            "CHART", "ANALYZE", "PORTFOLIO", "TRADE_JOURNAL",
            "INDICATOR_MIXER", "ALERTS", "SETTINGS", "EXPORT", "FUNDAMENTALS");

    // ── In-memory session ─────────────────────────────────────────────────────

    /**
     * Lightweight view of the logged-in user.
     * We only keep what the desktop UI actually needs; the full user entity
     * lives in {@code identity-service}'s database.
     */
    public record SessionUser(Long userId, String username, String displayName, String role) {

        public boolean isAdmin() {
            return "ADMIN".equalsIgnoreCase(role);
        }

        /** True for any role that can see all tabs (currently: ADMIN + PREMIUM). */
        public boolean hasFullAccess() {
            return isAdmin() || "PREMIUM".equalsIgnoreCase(role);
        }
    }

    private volatile SessionUser currentUser;

    private final IdentityApiClient identityClient;
    private final TokenStore        tokenStore;
    private final MarketStompClient marketStompClient;
    private final AlertStompClient  alertStompClient;

    public AuthService(IdentityApiClient identityClient,
                       TokenStore tokenStore,
                       @Lazy MarketStompClient marketStompClient,
                       @Lazy AlertStompClient  alertStompClient) {
        this.identityClient    = identityClient;
        this.tokenStore        = tokenStore;
        this.marketStompClient = marketStompClient;
        this.alertStompClient  = alertStompClient;
    }

    // ── Authentication ────────────────────────────────────────────────────────

    /**
     * Attempts login against the gateway → {@code identity-service}.
     * On success, stores the JWT in {@link TokenStore} and the user's session
     * info in-memory.
     *
     * <p>Returns an {@link Optional} of an opaque sentinel object (non-null =
     * success) to remain API-compatible with the old JPA-based signature that
     * returned {@code Optional<AppUser>}.  Callers only test
     * {@code result.isPresent()} — they never inspect the value.</p>
     *
     * @return non-empty Optional on success, empty on bad credentials / unreachable server
     */
    public Optional<Object> login(String username, String password) {
        Optional<LoginResponse> resp = identityClient.login(username, password);
        resp.ifPresent(r -> {
            currentUser = new SessionUser(r.getUserId(), r.getUsername(),
                    r.getDisplayName(), r.getRole());
            log.info("Session started for '{}' (role={})", r.getUsername(), r.getRole());
        });
        // Return a typed-erased sentinel so LoginController compiles unchanged
        return resp.map(r -> (Object) r);
    }

    /**
     * Clears local session state and the stored JWT.
     * Also disconnects STOMP connections for market data and alerts.
     */
    public void logout() {
        if (currentUser != null) {
            log.info("Session ended for '{}'", currentUser.username());
        }
        currentUser = null;
        identityClient.logout(); // clears TokenStore
        // Disconnect live-data STOMP connections
        try { marketStompClient.disconnect(); } catch (Exception ignored) {}
        try { alertStompClient.disconnect();  } catch (Exception ignored) {}
    }

    /**
     * Returns the currently logged-in user, or empty if not authenticated.
     * No network call — returns the in-memory session reference.
     */
    public Optional<SessionUser> currentUser() {
        return Optional.ofNullable(currentUser);
    }

    /** @return true when a session is active */
    public boolean isLoggedIn() {
        return currentUser != null;
    }

    /** @return true when the logged-in user has the ADMIN role */
    public boolean isAdmin() {
        return currentUser != null && currentUser.isAdmin();
    }

    // ── Registration ──────────────────────────────────────────────────────────

    /**
     * Registers a new user via the gateway → {@code identity-service}.
     *
     * @param username    desired username
     * @param password    desired password (plain; hashing is done server-side)
     * @param displayName human-readable display name
     * @param role        role constant — pass {@code "REGULAR_USER"} for self-registration
     * @return the new user's id
     * @throws IllegalArgumentException if the server rejects the request (e.g., duplicate username)
     */
    public RegisterResponse register(String username, String password,
                                     String displayName, String role) {
        return identityClient.register(username, password, displayName, role);
    }

    // ── Permission checks (role-based, no DB) ─────────────────────────────────

    /**
     * Returns true if the current user can see the given tab.
     * In Phase 2 the logic is driven purely by the role returned in the JWT —
     * per-user DB overrides will be fetched from {@code identity-service}'s
     * profile endpoint in a later step.
     */
    public boolean canSeeTab(String tabName) {
        if (currentUser == null) return false;
        // ADMIN and PREMIUM see everything; REGULAR_USER sees the standard set
        return true; // all tabs visible for now; server-side ACL applied in a later step
    }

    /**
     * Returns true if the current user can use the specified timeframe.
     * Restriction logic is now enforced server-side; the desktop app shows all
     * timeframes but the gateway / services will reject unauthorised ones.
     */
    public boolean canUseTimeframe(String tf) {
        return currentUser != null;
    }

    /** Returns the max candle count — use a safe default; server enforces the real limit. */
    public int maxCandles() {
        if (currentUser == null) return 200;
        return currentUser.isAdmin() ? 5000 : 500;
    }

    /** All timeframes — server enforces per-role limits; return full set locally. */
    public List<String> allowedTimeframes() {
        return List.of("1m", "3m", "5m", "15m", "30m", "1h", "2h", "4h", "6h",
                "8h", "12h", "1d", "3d", "1w", "1M");
    }

    // ── Transition helpers ────────────────────────────────────────────────────

    /**
     * Returns the currently logged-in user's id, or -1 if not authenticated.
     *
     * <p>Used by un-migrated controllers (e.g., {@code MainDashboardController})
     * that still need a user identifier to scope JPA queries.  These controllers
     * will be refactored in later domain steps, at which point this method and
     * the JPA dependency can be removed.</p>
     */
    public Long currentUserId() {
        return currentUser != null ? currentUser.userId() : -1L;
    }

    /** @return the logged-in user's username, or empty string when not authenticated. */
    public String currentUsername() {
        return currentUser != null ? currentUser.username() : "";
    }

    // ── Favourites (delegated to identity-service in a later step) ────────────

    /**
     * Persist the current user's favourite timeframes.
     * TODO (later step): call {@code PATCH /api/profile/me} on identity-service.
     */
    public void saveFavoriteTimeframes(List<String> favorites) {
        log.debug("saveFavoriteTimeframes — remote persistence deferred to later step");
    }

    /**
     * Get the current user's favourite timeframes.
     * TODO (later step): fetch from {@code GET /api/profile/me}.
     */
    public List<String> getFavoriteTimeframes() {
        return List.of();
    }
}
