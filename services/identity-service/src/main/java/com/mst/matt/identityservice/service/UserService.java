package com.mst.matt.identityservice.service;

import com.mst.matt.identityservice.dto.RegisterRequest;
import com.mst.matt.identityservice.model.AppUser;
import com.mst.matt.identityservice.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Core user-management service for identity-service.
 *
 * <p>Implements {@link UserDetailsService} so that Spring Security's
 * {@code DaoAuthenticationProvider} can load users for the username/password
 * login path.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final AppUserRepository userRepository;

    // ── UserDetailsService ────────────────────────────────────────────────────

    /**
     * Load a user by username for Spring Security's authentication mechanism.
     *
     * @throws UsernameNotFoundException if no active user exists with this username
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = userRepository.findByUsername(username)
                .filter(AppUser::isActive)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found or inactive: " + username));

        return User.builder()
                .username(user.getUsername())
                .password(user.getPasswordHash() != null ? user.getPasswordHash() : "")
                .authorities(List.of(new SimpleGrantedAuthority(user.authorityString())))
                .build();
    }

    // ── Application methods ───────────────────────────────────────────────────

    /**
     * Find an active {@link AppUser} by username (for JwtAuthFilter DB check).
     *
     * @return an Optional containing the user if found and active
     */
    @Transactional(readOnly = true)
    public Optional<AppUser> loadActiveUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .filter(AppUser::isActive);
    }

    /**
     * Authenticate a username/password credential.
     *
     * @param username the login username
     * @param rawPassword the plain-text password to verify
     * @return the authenticated {@link AppUser}
     * @throws UsernameNotFoundException if the user does not exist or is inactive
     * @throws IllegalArgumentException if the password is incorrect
     */
    @Transactional(readOnly = true)
    public AppUser authenticate(String username, String rawPassword) {
        AppUser user = userRepository.findByUsername(username)
                .filter(AppUser::isActive)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Invalid credentials"));

        if (!user.checkPassword(rawPassword)) {
            throw new IllegalArgumentException("Invalid credentials");
        }
        return user;
    }

    /**
     * Register a new local (username/password) account.
     *
     * @param request the registration payload
     * @return the newly created and persisted {@link AppUser}
     * @throws IllegalArgumentException if the username or email is already taken
     */
    @Transactional
    public AppUser register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException(
                    "Username already taken: " + request.getUsername());
        }
        if (request.getEmail() != null && userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException(
                    "Email already registered: " + request.getEmail());
        }

        AppUser newUser = AppUser.builder()
                .username(request.getUsername())
                .displayName(request.getDisplayName())
                .email(request.getEmail())
                .role(AppUser.Role.REGULAR_USER)
                .authProvider(AppUser.AuthProvider.LOCAL)
                .active(true)
                .build();
        newUser.setPassword(request.getPassword());   // BCrypt hash applied here

        AppUser saved = userRepository.save(newUser);
        log.info("Registered new user: id={}, username={}", saved.getId(), saved.getUsername());
        return saved;
    }

    /**
     * Find or create an account for a Google OAuth2 user.
     *
     * <p>Called by the OAuth2 success handler after Google authentication.
     * If the email is already registered as a LOCAL user, the Google provider
     * subject is linked to the existing account on first OAuth2 login.</p>
     *
     * @param email           Google account email
     * @param displayName     Google display name
     * @param providerSubject Google "sub" claim
     * @return the found or newly created {@link AppUser}
     */
    @Transactional
    public AppUser findOrCreateGoogleUser(String email, String displayName, String providerSubject) {
        // Already linked by provider subject
        Optional<AppUser> bySubject = userRepository.findByAuthProviderAndProviderSubject(
                AppUser.AuthProvider.GOOGLE, providerSubject);
        if (bySubject.isPresent()) {
            return bySubject.get();
        }

        // Existing local account with same email — link it
        Optional<AppUser> byEmail = userRepository.findByEmail(email);
        if (byEmail.isPresent()) {
            AppUser existing = byEmail.get();
            existing.setAuthProvider(AppUser.AuthProvider.GOOGLE);
            existing.setProviderSubject(providerSubject);
            return userRepository.save(existing);
        }

        // Brand new OAuth2 user
        String baseUsername = email.split("@")[0].replaceAll("[^a-zA-Z0-9_]", "_");
        String username = ensureUniqueUsername(baseUsername);

        AppUser newUser = AppUser.builder()
                .username(username)
                .email(email)
                .displayName(displayName != null ? displayName : username)
                .authProvider(AppUser.AuthProvider.GOOGLE)
                .providerSubject(providerSubject)
                .role(AppUser.Role.REGULAR_USER)
                .active(true)
                .build();

        AppUser saved = userRepository.save(newUser);
        log.info("Created Google OAuth2 user: id={}, email={}", saved.getId(), email);
        return saved;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String ensureUniqueUsername(String base) {
        if (!userRepository.existsByUsername(base)) return base;
        int suffix = 1;
        while (userRepository.existsByUsername(base + suffix)) {
            suffix++;
        }
        return base + suffix;
    }
}
