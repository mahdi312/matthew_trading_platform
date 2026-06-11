package com.mst.matt.tradingplatformapp.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.crypto.bcrypt.BCrypt;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Application user with role-based access control.
 * Separate from {@link UserProfile} (trading profiles); one AppUser can have
 * multiple UserProfiles.
 */
@Entity
@Table(name = "app_users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_app_users_username", columnNames = "username")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppUser {

    public enum Role {
        ADMIN,
        PRO_PLUS_USER,
        PRO_USER,
        REGULAR_USER;

        /** Human-readable label. */
        public String label() {
            return switch (this) {
                case ADMIN          -> "Admin";
                case PRO_PLUS_USER  -> "Pro Plus";
                case PRO_USER       -> "Pro";
                case REGULAR_USER   -> "Regular";
            };
        }

        /** Maximum candle history per timeframe. */
        public int maxCandles() {
            return switch (this) {
                case ADMIN          -> 5000;
                case PRO_PLUS_USER  -> 2000;
                case PRO_USER       -> 1000;
                case REGULAR_USER   -> 200;
            };
        }

        /** Allowed timeframes for this role. */
        public List<String> allowedTimeframes() {
            return switch (this) {
                case ADMIN, PRO_PLUS_USER ->
                        List.of("1m","3m","5m","15m","30m","1h","2h","4h","6h","8h","12h","1d","3d","1w","1mo");
                case PRO_USER ->
                        List.of("1m","5m","15m","30m","1h","4h","1d","1w");
                case REGULAR_USER ->
                        List.of("1h","4h","1d","1w","1mo");
            };
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.REGULAR_USER;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime lastLoginAt;

    /** Comma-separated list of tab names this user can NOT see (per-user override). */
    @Column(length = 512)
    private String hiddenTabs;

    /** Comma-separated favorite timeframes in display order. */
    @Column(length = 256)
    private String favoriteTimeframes;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    /** Verify a plain-text password against the stored BCrypt hash. */
    public boolean checkPassword(String plain) {
        if (plain == null || passwordHash == null) return false;
        return BCrypt.checkpw(plain, passwordHash);
    }

    /** Hash and store a plain-text password. */
    public void setPassword(String plain) {
        this.passwordHash = BCrypt.hashpw(plain, BCrypt.gensalt());
    }

    /** Returns true if this user has permission to use the given timeframe. */
    public boolean canUseTimeframe(String tf) {
        if (role == null) return false;
        return role.allowedTimeframes().contains(tf.toLowerCase());
    }

    /** Returns the list of hidden tabs (empty list if none). */
    public List<String> hiddenTabList() {
        if (hiddenTabs == null || hiddenTabs.isBlank()) return List.of();
        return List.of(hiddenTabs.split(","));
    }

    /** Returns the list of favorite timeframes (empty = no favorites set). */
    public List<String> favoriteTimeframeList() {
        if (favoriteTimeframes == null || favoriteTimeframes.isBlank()) return List.of();
        return List.of(favoriteTimeframes.split(","));
    }
}
