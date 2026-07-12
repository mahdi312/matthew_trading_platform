package com.mst.matt.identityservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.crypto.bcrypt.BCrypt;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Application user with role-based access control.
 *
 * <p>Copied as-is from the JavaFX monolith and re-homed in
 * {@code identity-service} — the only service that owns user identity
 * persistence. {@code AppUser} is a JPA {@code @Entity} here; it must NOT
 * be placed in {@code shared/contracts} which is entity-free.</p>
 *
 * <p>One {@code AppUser} can have multiple trading profiles (managed by
 * the trading-service); this entity represents only the authentication
 * identity.</p>
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

    // ── Role ─────────────────────────────────────────────────────────────────

    public enum Role {
        ADMIN,
        PRO_PLUS_USER,
        PRO_USER,
        REGULAR_USER;

        /** Human-readable label for UI display. */
        public String label() {
            return switch (this) {
                case ADMIN         -> "Admin";
                case PRO_PLUS_USER -> "Pro Plus";
                case PRO_USER      -> "Pro";
                case REGULAR_USER  -> "Regular";
            };
        }

        /** Maximum candle history per timeframe allowed for this role. */
        public int maxCandles() {
            return switch (this) {
                case ADMIN         -> 5000;
                case PRO_PLUS_USER -> 2000;
                case PRO_USER      -> 1000;
                case REGULAR_USER  -> 200;
            };
        }

        /** Allowed OHLCV timeframes for this role. */
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

        /**
         * Returns the set of market-data providers available to this role.
         *
         * <ul>
         *   <li>ADMIN / PRO_PLUS — all providers including premium ones.</li>
         *   <li>PRO — most providers except the most premium.</li>
         *   <li>REGULAR — only free providers: CoinGecko, Yahoo, Frankfurter.</li>
         * </ul>
         */
        public List<String> allowedProviders() {
            return switch (this) {
                case ADMIN, PRO_PLUS_USER -> List.of(
                        "BINANCE", "COINGECKO", "COINMARKETCAP", "YAHOO", "FINNHUB",
                        "ALPHA_VANTAGE", "POLYGON", "TWELVE_DATA",
                        "MARKETSTACK", "FRANKFURTER",
                        "FIXER", "FREE_CURRENCY_API", "OPEN_EXCHANGE_RATES",
                        "EXCHANGE_RATE_API", "CURRENCY_LAYER");
                case PRO_USER -> List.of(
                        "COINGECKO", "COINMARKETCAP", "YAHOO", "FINNHUB",
                        "ALPHA_VANTAGE", "TWELVE_DATA", "FRANKFURTER",
                        "FREE_CURRENCY_API", "OPEN_EXCHANGE_RATES", "EXCHANGE_RATE_API");
                case REGULAR_USER -> List.of(
                        "COINGECKO", "COINMARKETCAP", "YAHOO", "FRANKFURTER",
                        "FREE_CURRENCY_API", "EXCHANGE_RATE_API");
            };
        }

        /** Returns {@code true} if this role may use the named provider. */
        public boolean canUseProvider(String providerName) {
            if (providerName == null || providerName.equalsIgnoreCase("AUTO")) return true;
            return allowedProviders().stream()
                    .anyMatch(p -> p.equalsIgnoreCase(providerName));
        }
    }

    // ── OAuth2 provider ───────────────────────────────────────────────────────

    /**
     * Authentication provider used for this account.
     * {@code LOCAL} = username/password; {@code GOOGLE} = Google OAuth2.
     */
    public enum AuthProvider {
        LOCAL,
        GOOGLE
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String username;

    /**
     * BCrypt hash of the password. {@code null} for OAuth2-only accounts
     * where no local password has been set.
     */
    @Column
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String displayName;

    /**
     * Email address — required for OAuth2 accounts, optional for local.
     */
    @Column(length = 255)
    private String email;

    /**
     * External OAuth2 provider subject identifier (e.g., Google's "sub" claim).
     * Null for LOCAL accounts.
     */
    @Column(length = 255)
    private String providerSubject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AuthProvider authProvider = AuthProvider.LOCAL;

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

    /** Comma-separated list of tab names this user cannot see (per-user override). */
    @Column(length = 512)
    private String hiddenTabs;

    /** Comma-separated favourite timeframes in display order. */
    @Column(length = 256)
    private String favoriteTimeframes;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    // ── Password helpers ──────────────────────────────────────────────────────

    /** Verify a plain-text password against the stored BCrypt hash. */
    public boolean checkPassword(String plain) {
        if (plain == null || passwordHash == null) return false;
        return BCrypt.checkpw(plain, passwordHash);
    }

    /** Hash and store a plain-text password. */
    public void setPassword(String plain) {
        this.passwordHash = BCrypt.hashpw(plain, BCrypt.gensalt());
    }

    // ── Permission helpers ────────────────────────────────────────────────────

    /** Returns {@code true} if this user's role allows the given timeframe. */
    public boolean canUseTimeframe(String tf) {
        if (role == null) return false;
        return role.allowedTimeframes().contains(tf.toLowerCase());
    }

    /** Returns the list of hidden tabs (empty list if none). */
    public List<String> hiddenTabList() {
        if (hiddenTabs == null || hiddenTabs.isBlank()) return List.of();
        return List.of(hiddenTabs.split(","));
    }

    /** Returns the list of favourite timeframes (empty = no favourites set). */
    public List<String> favoriteTimeframeList() {
        if (favoriteTimeframes == null || favoriteTimeframes.isBlank()) return List.of();
        return List.of(favoriteTimeframes.split(","));
    }

    /** Spring Security authority string for this user's role. */
    public String authorityString() {
        return "ROLE_" + (role != null ? role.name() : "REGULAR_USER");
    }
}
