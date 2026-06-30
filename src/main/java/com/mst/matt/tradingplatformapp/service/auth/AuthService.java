package com.mst.matt.tradingplatformapp.service.auth;

import com.mst.matt.tradingplatformapp.model.AppUser;
import com.mst.matt.tradingplatformapp.model.AppUser.Role;
import com.mst.matt.tradingplatformapp.model.RolePermission;
import com.mst.matt.tradingplatformapp.repository.AppUserRepository;
import com.mst.matt.tradingplatformapp.repository.RolePermissionRepository;
import com.mst.matt.tradingplatformapp.service.price.LiveTickerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Authentication & authorization service.
 *
 * <p>Session management is in-memory (single-user desktop app).
 * Passwords are BCrypt-hashed via {@link AppUser#setPassword}.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    // All navigable tabs
    public static final List<String> ALL_TABS = List.of(
            "CHART", "ANALYZE", "PORTFOLIO", "TRADE_JOURNAL",
            "INDICATOR_MIXER", "ALERTS", "SETTINGS", "EXPORT", "FUNDAMENTALS");

    private final AppUserRepository userRepo;
    private final RolePermissionRepository permRepo;

    /**
     * Lazy to break the circular dependency:
     * AuthService → LiveTickerService → AuthService
     */
    private final LiveTickerService liveTickerService;

    private volatile AppUser currentUser;

    public AuthService(AppUserRepository userRepo,
                       RolePermissionRepository permRepo,
                       @Lazy LiveTickerService liveTickerService) {
        this.userRepo = userRepo;
        this.permRepo = permRepo;
        this.liveTickerService = liveTickerService;
        ensureAdminExists();
    }

    // ── Bootstrap ──────────────────────────────────────────────

    /** Creates the default admin account if no users exist yet. */
    private void ensureAdminExists() {
        if (userRepo.count() == 0) {
            AppUser admin = AppUser.builder()
                    .username("admin")
                    .displayName("Administrator")
                    .role(Role.ADMIN)
                    .active(true)
                    .build();
            admin.setPassword("admin123");
            userRepo.save(admin);
            log.info("Default admin user created (username=admin, password=admin123). " +
                     "Please change the password after first login.");
        }
    }

    // ── Authentication ─────────────────────────────────────────

    /**
     * Attempt login. Returns the logged-in user on success, empty on failure.
     */
    @Transactional
    public Optional<AppUser> login(String username, String password) {
        Optional<AppUser> opt = userRepo.findByUsername(username.toLowerCase().trim());
        if (opt.isEmpty()) return Optional.empty();
        AppUser user = opt.get();
        if (!user.isActive() || !user.checkPassword(password)) return Optional.empty();
        user.setLastLoginAt(LocalDateTime.now());
        userRepo.save(user);
        this.currentUser = user;
        log.info("User '{}' logged in (role={})", username, user.getRole());
        return Optional.of(user);
    }

    public void logout() {
        if (currentUser != null)
            log.info("User '{}' logged out", currentUser.getUsername());
        this.currentUser = null;
        // Stop live market streams so no external API calls are made after logout
        try {
            liveTickerService.stopLiveStreams();
        } catch (Exception e) {
            log.warn("Error stopping live streams on logout: {}", e.getMessage());
        }
    }

    /** @return the currently logged-in user, or empty if not authenticated. */
    public Optional<AppUser> currentUser() {
        return Optional.ofNullable(currentUser);
    }

    public boolean isLoggedIn() { return currentUser != null; }

    public boolean isAdmin() {
        return currentUser != null && currentUser.getRole() == Role.ADMIN;
    }

    // ── Registration ───────────────────────────────────────────

    /**
     * Register a new user. Only ADMIN can register with non-REGULAR_USER roles.
     *
     * @return the created user
     * @throws IllegalArgumentException if username taken or caller lacks permission
     */
    @Transactional
    public AppUser register(String username, String password,
                            String displayName, Role role) {
        String lc = username.toLowerCase().trim();
        if (userRepo.existsByUsername(lc))
            throw new IllegalArgumentException("Username '" + lc + "' is already taken.");
        if (role != Role.REGULAR_USER && (currentUser == null || currentUser.getRole() != Role.ADMIN))
            throw new IllegalArgumentException("Only ADMIN can create non-regular users.");

        AppUser u = AppUser.builder()
                .username(lc)
                .displayName(displayName == null || displayName.isBlank() ? username : displayName)
                .role(role)
                .active(true)
                .build();
        u.setPassword(password);
        return userRepo.save(u);
    }

    // ── Permission checks ──────────────────────────────────────

    /**
     * Returns true if the current user can see the given tab.
     * Resolution order: per-user DB override → role DB override → built-in rules.
     */
    public boolean canSeeTab(String tabName) {
        if (currentUser == null) return false;

        // 1. Per-user DB override
        Optional<RolePermission> userOverride =
                permRepo.findBySubjectUserIdAndTabName(currentUser.getId(), tabName);
        if (userOverride.isPresent()) return userOverride.get().isVisible();

        // 2. Per-user hidden-tabs field override
        if (currentUser.hiddenTabList().contains(tabName)) return false;

        // 3. Role DB override
        Optional<RolePermission> roleOverride =
                permRepo.findBySubjectRoleAndTabName(currentUser.getRole(), tabName);
        if (roleOverride.isPresent()) return roleOverride.get().isVisible();

        // 4. ADMIN sees everything by default
        if (currentUser.getRole() == Role.ADMIN) return true;

        // 5. Default: everyone sees all main tabs (timeframe/data access is restricted elsewhere)
        return true;
    }

    /**
     * Returns true if the current user can use the specified timeframe.
     */
    public boolean canUseTimeframe(String tf) {
        if (currentUser == null) return false;
        return currentUser.getRole().allowedTimeframes().contains(tf.toLowerCase());
    }

    /**
     * Returns the max candle count for the current user.
     */
    public int maxCandles() {
        if (currentUser == null) return 200;
        return currentUser.getRole().maxCandles();
    }

    /** All timeframes allowed for the current user. */
    public List<String> allowedTimeframes() {
        if (currentUser == null) return Role.REGULAR_USER.allowedTimeframes();
        return currentUser.getRole().allowedTimeframes();
    }

    // ── User Management (ADMIN only) ───────────────────────────

    @Transactional
    public void changeRole(Long userId, Role newRole) {
        requireAdmin();
        AppUser u = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        u.setRole(newRole);
        userRepo.save(u);
    }

    @Transactional
    public void setUserActive(Long userId, boolean active) {
        requireAdmin();
        AppUser u = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        u.setActive(active);
        userRepo.save(u);
    }

    @Transactional
    public void deleteUser(Long userId) {
        requireAdmin();
        if (currentUser != null && currentUser.getId().equals(userId))
            throw new IllegalArgumentException("Cannot delete yourself.");
        userRepo.deleteById(userId);
        permRepo.deleteBySubjectUserId(userId);
    }

    @Transactional
    public void setTabVisibilityForRole(Role role, String tabName, boolean visible) {
        requireAdmin();
        Optional<RolePermission> existing =
                permRepo.findBySubjectRoleAndTabName(role, tabName);
        RolePermission perm = existing.orElse(
                RolePermission.builder().subjectRole(role).tabName(tabName).build());
        perm.setVisible(visible);
        permRepo.save(perm);
    }

    @Transactional
    public void setTabVisibilityForUser(Long userId, String tabName, boolean visible) {
        requireAdmin();
        Optional<RolePermission> existing =
                permRepo.findBySubjectUserIdAndTabName(userId, tabName);
        RolePermission perm = existing.orElse(
                RolePermission.builder().subjectUserId(userId).tabName(tabName).build());
        perm.setVisible(visible);
        permRepo.save(perm);
    }

    /** Returns all registered users (ADMIN only). */
    public List<AppUser> allUsers() {
        requireAdmin();
        return userRepo.findAllByOrderByCreatedAtDesc();
    }

    private void requireAdmin() {
        if (currentUser == null || currentUser.getRole() != Role.ADMIN)
            throw new SecurityException("ADMIN access required.");
    }

    // ── Favorites ──────────────────────────────────────────────

    /** Persist the current user's favorite timeframes. */
    @Transactional
    public void saveFavoriteTimeframes(List<String> favorites) {
        if (currentUser == null) return;
        String csv = String.join(",", favorites);
        currentUser.setFavoriteTimeframes(csv);
        userRepo.save(currentUser);
    }

    /** Get the current user's favorite timeframes (filtered to allowed). */
    public List<String> getFavoriteTimeframes() {
        if (currentUser == null) return List.of();
        List<String> favs = currentUser.favoriteTimeframeList();
        List<String> allowed = allowedTimeframes();
        return favs.stream().filter(allowed::contains).toList();
    }
}
