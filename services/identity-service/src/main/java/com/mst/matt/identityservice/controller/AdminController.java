package com.mst.matt.identityservice.controller;

import com.mst.matt.identityservice.dto.*;
import com.mst.matt.identityservice.model.AppUser;
import com.mst.matt.identityservice.repository.AppUserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Admin-only REST controller for user management.
 *
 * <p>All endpoints require {@code ROLE_ADMIN} (enforced by {@code @PreAuthorize}).
 * Business logic ported from the desktop's {@code AdminUserManagementController} service calls —
 * not the JavaFX FXML binding code.</p>
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code GET    /api/admin/users}                         — list all users</li>
 *   <li>{@code POST   /api/admin/users}                         — create a new user</li>
 *   <li>{@code GET    /api/admin/users/{id}}                    — get a user by ID</li>
 *   <li>{@code PUT    /api/admin/users/{id}/role}               — change a user's role</li>
 *   <li>{@code PUT    /api/admin/users/{id}/active}             — toggle active flag</li>
 *   <li>{@code DELETE /api/admin/users/{id}}                    — delete a user</li>
 *   <li>{@code PUT    /api/admin/users/{id}/tab-permissions}    — set per-user tab visibility</li>
 *   <li>{@code GET    /api/admin/roles}                         — list all available roles</li>
 * </ul>
 *
 * <p>All known tab names are exposed via {@code GET /api/admin/roles} for the frontend
 * to populate the permissions editor.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    /** All tabs that can have per-user visibility controlled. */
    public static final List<String> ALL_TABS = List.of(
            "CHART", "TRADE_JOURNAL", "YEARLY_PROFIT", "PORTFOLIO",
            "ALERTS", "AI_ANALYSIS", "SETTINGS", "ADMIN"
    );

    private final AppUserRepository userRepository;

    // ── User listing ──────────────────────────────────────────────────────────

    /**
     * GET /api/admin/users
     * Returns all users in the system, ordered by creation date ascending.
     */
    @GetMapping("/users")
    public List<AppUserResponse> listUsers() {
        return userRepository.findAll()
                .stream()
                .map(AppUserResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * GET /api/admin/users/{id}
     * Returns a single user by ID.
     */
    @GetMapping("/users/{id}")
    public AppUserResponse getUser(@PathVariable Long id) {
        return AppUserResponse.from(findOrThrow(id));
    }

    // ── User creation ─────────────────────────────────────────────────────────

    /**
     * POST /api/admin/users
     * Admin creates a new local user with a specified role.
     */
    @PostMapping("/users")
    public ResponseEntity<AppUserResponse> createUser(
            @Valid @RequestBody AdminCreateUserRequest req) {

        if (userRepository.existsByUsername(req.getUsername())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Username already taken: " + req.getUsername());
        }
        if (req.getEmail() != null && userRepository.existsByEmail(req.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Email already registered: " + req.getEmail());
        }

        AppUser user = AppUser.builder()
                .username(req.getUsername())
                .displayName(req.getDisplayName() != null
                        && !req.getDisplayName().isBlank()
                        ? req.getDisplayName() : req.getUsername())
                .email(req.getEmail())
                .role(req.getRole() != null ? req.getRole() : AppUser.Role.REGULAR_USER)
                .authProvider(AppUser.AuthProvider.LOCAL)
                .active(true)
                .build();
        user.setPassword(req.getPassword());

        AppUser saved = userRepository.save(user);
        log.info("Admin created user: id={} username={} role={}", saved.getId(), saved.getUsername(), saved.getRole());
        return ResponseEntity.status(HttpStatus.CREATED).body(AppUserResponse.from(saved));
    }

    // ── Role management ───────────────────────────────────────────────────────

    /**
     * PUT /api/admin/users/{id}/role
     * Change a user's role. Request body: {@code { "role": "PRO_USER" }}.
     */
    @PutMapping("/users/{id}/role")
    public AppUserResponse changeRole(
            @PathVariable Long id,
            @Valid @RequestBody ChangeRoleRequest req) {

        AppUser user = findOrThrow(id);
        AppUser.Role oldRole = user.getRole();
        user.setRole(req.getRole());
        AppUser saved = userRepository.save(user);
        log.info("Admin changed role: userId={} {} -> {}", id, oldRole, req.getRole());
        return AppUserResponse.from(saved);
    }

    // ── Active flag ───────────────────────────────────────────────────────────

    /**
     * PUT /api/admin/users/{id}/active
     * Enable or disable a user account. Request body: {@code { "active": true/false }}.
     */
    @PutMapping("/users/{id}/active")
    public AppUserResponse setActive(
            @PathVariable Long id,
            @RequestBody SetActiveRequest req) {

        AppUser user = findOrThrow(id);
        user.setActive(req.isActive());
        AppUser saved = userRepository.save(user);
        log.info("Admin set active: userId={} active={}", id, req.isActive());
        return AppUserResponse.from(saved);
    }

    // ── User deletion ─────────────────────────────────────────────────────────

    /**
     * DELETE /api/admin/users/{id}
     * Hard-delete a user. This cannot be undone.
     */
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        AppUser user = findOrThrow(id);
        userRepository.delete(user);
        log.info("Admin deleted user: userId={} username={}", id, user.getUsername());
        return ResponseEntity.noContent().build();
    }

    // ── Tab permissions ───────────────────────────────────────────────────────

    /**
     * PUT /api/admin/users/{id}/tab-permissions
     * Set per-user tab visibility.
     * Hidden tabs are stored as a comma-separated list in {@code AppUser.hiddenTabs}.
     *
     * <p>Request body: {@code { "tabVisibility": { "CHART": true, "ADMIN": false } } }</p>
     */
    @PutMapping("/users/{id}/tab-permissions")
    public AppUserResponse setTabPermissions(
            @PathVariable Long id,
            @RequestBody TabPermissionsRequest req) {

        AppUser user = findOrThrow(id);

        if (req.getTabVisibility() != null) {
            List<String> hidden = new ArrayList<>();
            for (Map.Entry<String, Boolean> entry : req.getTabVisibility().entrySet()) {
                if (Boolean.FALSE.equals(entry.getValue())) {
                    hidden.add(entry.getKey());
                }
            }
            user.setHiddenTabs(hidden.isEmpty() ? null : String.join(",", hidden));
        }

        AppUser saved = userRepository.save(user);
        log.info("Admin set tab permissions: userId={} hiddenTabs={}", id, saved.getHiddenTabs());
        return AppUserResponse.from(saved);
    }

    /**
     * GET /api/admin/users/{id}/tab-permissions
     * Returns the current tab visibility map for the given user.
     */
    @GetMapping("/users/{id}/tab-permissions")
    public Map<String, Boolean> getTabPermissions(@PathVariable Long id) {
        AppUser user = findOrThrow(id);
        List<String> hidden = user.hiddenTabList();
        return ALL_TABS.stream()
                .collect(Collectors.toMap(
                        tab -> tab,
                        tab -> !hidden.contains(tab)));
    }

    // ── Roles metadata ────────────────────────────────────────────────────────

    /**
     * GET /api/admin/roles
     * Returns all available roles and the full list of controllable tab names.
     * Used by the frontend admin panel to populate role selector and tab checkboxes.
     */
    @GetMapping("/roles")
    public Map<String, Object> getRolesMetadata() {
        return Map.of(
                "roles", List.of(AppUser.Role.values()),
                "allTabs", ALL_TABS
        );
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private AppUser findOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found: " + id));
    }
}
