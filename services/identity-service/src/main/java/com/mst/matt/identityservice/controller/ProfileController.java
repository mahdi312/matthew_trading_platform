package com.mst.matt.identityservice.controller;

import com.mst.matt.identityservice.dto.AppSettingsRequest;
import com.mst.matt.identityservice.dto.UserProfileRequest;
import com.mst.matt.identityservice.dto.UserProfileResponse;
import com.mst.matt.identityservice.model.AppUser;
import com.mst.matt.identityservice.model.UserProfile;
import com.mst.matt.identityservice.repository.AppUserRepository;
import com.mst.matt.identityservice.repository.UserProfileRepository;
import com.mst.matt.identityservice.service.AppSettingsService;
import com.mst.matt.identityservice.service.ProfilePersistenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller for user profile and app-settings management.
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code GET    /api/profile}         — list all profiles for the authenticated user</li>
 *   <li>{@code POST   /api/profile}         — create a new profile for the authenticated user</li>
 *   <li>{@code GET    /api/profile/{id}}    — get a specific profile (must belong to caller)</li>
 *   <li>{@code PUT    /api/profile/{id}}    — update a specific profile (must belong to caller)</li>
 *   <li>{@code DELETE /api/profile/{id}}    — delete a profile (must belong to caller)</li>
 *   <li>{@code GET    /api/profile/settings}         — get all app settings for caller</li>
 *   <li>{@code PUT    /api/profile/settings}         — update (upsert) app settings for caller</li>
 * </ul>
 *
 * <p>Business logic ported from the desktop's {@code ProfileSettingsController} service calls,
 * not the JavaFX FXML binding code.</p>
 */
@Tag(name = "User Profile", description = "User profiles, app settings, and notification preferences")
@SecurityRequirement(name = "bearer-jwt")
@Slf4j
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final AppUserRepository userRepository;
    private final UserProfileRepository profileRepository;
    private final ProfilePersistenceService profilePersistence;
    private final AppSettingsService settingsService;

    // ── Helper: resolve caller's AppUser ────────────────────────────────────

    private AppUser resolveCallerOrThrow(UserDetails principal) {
        return userRepository.findByUsername(principal.getUsername())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "User not found"));
    }

    // ── Profile CRUD ─────────────────────────────────────────────────────────

    /**
     * GET /api/profile
     * Returns all profiles owned by the authenticated user, most recently used first.
     */
    @Operation(summary = "List profiles for the authenticated user")
    @GetMapping
    public List<UserProfileResponse> listProfiles(
            @AuthenticationPrincipal UserDetails principal) {

        AppUser caller = resolveCallerOrThrow(principal);
        return profileRepository
                .findByAppUserIdOrderByLastAccessedAtDesc(caller.getId())
                .stream()
                .map(UserProfileResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * POST /api/profile
     * Create a new profile for the authenticated user.
     */
    @Operation(summary = "Create a new user profile")
    @PostMapping
    public ResponseEntity<UserProfileResponse> createProfile(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody UserProfileRequest req) {

        AppUser caller = resolveCallerOrThrow(principal);

        // Enforce uniqueness within this user's profiles
        if (profileRepository.findByAppUserIdAndName(caller.getId(), req.getName()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Profile name already exists: " + req.getName());
        }

        UserProfile profile = UserProfile.builder()
                .appUserId(caller.getId())
                .name(req.getName())
                .avatarColor(req.getAvatarColor())
                .description(req.getDescription())
                .active(req.isActive())
                .assetFocus(req.getAssetFocus() != null
                        ? req.getAssetFocus() : UserProfile.ProfileAssetFocus.MULTI)
                .defaultSymbol(req.getDefaultSymbol())
                .chartProvider(req.getChartProvider() != null ? req.getChartProvider() : "AUTO")
                .fundamentalProvider(req.getFundamentalProvider() != null
                        ? req.getFundamentalProvider() : "AUTO")
                .watchlist(req.getWatchlist())
                .drawingSettingsJson(req.getDrawingSettingsJson())
                .createdAt(LocalDateTime.now())
                .lastAccessedAt(LocalDateTime.now())
                .build();

        UserProfile saved = profileRepository.save(profile);
        log.info("Profile created: id={} name={} userId={}", saved.getId(), saved.getName(), caller.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserProfileResponse.from(saved));
    }

    /**
     * GET /api/profile/{id}
     * Get a specific profile; must be owned by the caller.
     */
    @Operation(summary = "Get a profile by ID")
    @GetMapping("/{id}")
    public UserProfileResponse getProfile(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable Long id) {

        AppUser caller = resolveCallerOrThrow(principal);
        UserProfile profile = findOwnedProfileOrThrow(caller, id);
        return UserProfileResponse.from(profile);
    }

    /**
     * PUT /api/profile/{id}
     * Update a specific profile; must be owned by the caller.
     * Also touches {@code lastAccessedAt} to reflect this activity.
     */
    @Operation(summary = "Update a profile by ID")
    @PutMapping("/{id}")
    public UserProfileResponse updateProfile(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable Long id,
            @Valid @RequestBody UserProfileRequest req) {

        AppUser caller = resolveCallerOrThrow(principal);
        UserProfile profile = findOwnedProfileOrThrow(caller, id);

        // Check name conflict if renaming
        if (!profile.getName().equals(req.getName())) {
            profileRepository.findByAppUserIdAndName(caller.getId(), req.getName())
                    .ifPresent(existing -> { throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Profile name already exists: " + req.getName()); });
        }

        // Apply updates from request
        profile.setName(req.getName());
        profile.setAvatarColor(req.getAvatarColor());
        profile.setDescription(req.getDescription());
        profile.setActive(req.isActive());
        if (req.getAssetFocus() != null)         profile.setAssetFocus(req.getAssetFocus());
        if (req.getDefaultSymbol() != null)       profile.setDefaultSymbol(req.getDefaultSymbol());
        if (req.getChartProvider() != null)       profile.setChartProvider(req.getChartProvider());
        if (req.getFundamentalProvider() != null) profile.setFundamentalProvider(req.getFundamentalProvider());
        if (req.getWatchlist() != null)           profile.setWatchlist(req.getWatchlist());
        if (req.getDrawingSettingsJson() != null) profile.setDrawingSettingsJson(req.getDrawingSettingsJson());
        profile.setLastAccessedAt(LocalDateTime.now());

        // Async save — fire-and-forget; read the saved result synchronously for the response
        profileRepository.save(profile);
        profilePersistence.saveAsync(profile);  // also triggers async side effects if needed
        log.info("Profile updated: id={} name={} userId={}", profile.getId(), profile.getName(), caller.getId());
        return UserProfileResponse.from(profile);
    }

    /**
     * DELETE /api/profile/{id}
     * Delete a profile owned by the caller.
     */
    @Operation(summary = "Delete a profile by ID")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProfile(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable Long id) {

        AppUser caller = resolveCallerOrThrow(principal);
        UserProfile profile = findOwnedProfileOrThrow(caller, id);
        profileRepository.delete(profile);
        log.info("Profile deleted: id={} userId={}", id, caller.getId());
        return ResponseEntity.noContent().build();
    }

    // ── App Settings ─────────────────────────────────────────────────────────

    /**
     * GET /api/profile/settings
     * Returns all app settings for the authenticated user as a key→value map.
     */
    @Operation(summary = "Get app settings for the authenticated user")
    @GetMapping("/settings")
    public Map<String, String> getSettings(
            @AuthenticationPrincipal UserDetails principal) {

        AppUser caller = resolveCallerOrThrow(principal);
        return settingsService.getAll(caller.getId());
    }

    /**
     * PUT /api/profile/settings
     * Upsert one or more settings for the authenticated user.
     * The request body is {@code { "settings": { "ui.theme": "dark", ... } }}.
     */
    @Operation(summary = "Update app settings for the authenticated user")
    @PutMapping("/settings")
    public Map<String, String> updateSettings(
            @AuthenticationPrincipal UserDetails principal,
            @RequestBody AppSettingsRequest req) {

        AppUser caller = resolveCallerOrThrow(principal);
        if (req.getSettings() != null) {
            req.getSettings().forEach((key, value) ->
                    settingsService.set(caller.getId(), key, value));
        }
        return settingsService.getAll(caller.getId());
    }

    /**
     * GET /api/profile/preferences
     * Returns notification preferences for the authenticated user.
     */
    @Operation(summary = "Get notification preferences for the authenticated user")
    @GetMapping("/preferences")
    public com.mst.matt.contracts.dto.UserPreferencesDto getOwnNotificationPreferences(
            @AuthenticationPrincipal UserDetails principal) {
        AppUser caller = resolveCallerOrThrow(principal);
        return settingsService.getNotificationPreferences(caller.getId());
    }

    /**
     * GET /api/profile/{userId}/preferences
     * Service-to-service endpoint (no {@code @AuthenticationPrincipal}) — called by
     * notification-service to resolve delivery preferences for an event's userId,
     * which is not the caller's own identity. Network-level trust only for now
     * (internal Docker/K8s network); add a service-to-service auth check here before
     * exposing this Gateway route publicly.
     */
    @Operation(summary = "Get notification preferences by user ID (service-to-service)")
    @SecurityRequirements
    @GetMapping("/{userId}/preferences")
    public com.mst.matt.contracts.dto.UserPreferencesDto getNotificationPreferences(
            @PathVariable Long userId) {
        return settingsService.getNotificationPreferences(userId);
    }

    /**
     * PUT /api/profile/preferences
     * Caller updates their own notification preferences.
     */
    @Operation(summary = "Update notification preferences for the authenticated user")
    @PutMapping("/preferences")
    public com.mst.matt.contracts.dto.UserPreferencesDto updateNotificationPreferences(
            @AuthenticationPrincipal UserDetails principal,
            @RequestBody com.mst.matt.contracts.dto.UserPreferencesDto prefs) {
        AppUser caller = resolveCallerOrThrow(principal);
        settingsService.setNotificationPreferences(caller.getId(), prefs);
        return settingsService.getNotificationPreferences(caller.getId());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private UserProfile findOwnedProfileOrThrow(AppUser caller, Long profileId) {
        UserProfile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Profile not found: " + profileId));
        if (!caller.getId().equals(profile.getAppUserId())
                && caller.getRole() != AppUser.Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Access denied to profile " + profileId);
        }
        return profile;
    }
}
