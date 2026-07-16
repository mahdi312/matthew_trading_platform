package com.mst.matt.identityservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Per-user trading profile — ported from the desktop monolith's UserProfile.
 *
 * <p>References {@link AppUser} by its ID (no cross-entity FK outside the service boundary).
 * One AppUser may own many profiles (e.g. "Crypto Portfolio", "Stocks Journal").
 *
 * <p>Fields that belonged to the desktop's Trade/PriceAlert/IndicatorConfig
 * relationships are omitted here — those entities live in trading-service.
 * The profile is purely a named container with display and provider preferences.
 */
@Entity
@Table(name = "user_profiles", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_profiles_name", columnNames = "name")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;                     // e.g. "Crypto Portfolio", "Stocks Journal"

    @Column
    private String avatarColor;              // Hex color for UI avatar badge

    @Column
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = false;

    /**
     * The AppUser who owns this profile (stored as the numeric ID — no cross-entity FK
     * so the profiles table stays queryable independently of the users table structure).
     */
    @Column(name = "app_user_id")
    private Long appUserId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime lastAccessedAt;

    /**
     * Primary asset class for this profile (drives default symbol & provider chain).
     * Defaults to MULTI so older rows (appUserId null) never produce NPE.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    @Builder.Default
    private ProfileAssetFocus assetFocus = ProfileAssetFocus.MULTI;

    @Column(length = 32)
    private String defaultSymbol;

    /** Market-data provider name or AUTO. */
    @Column(length = 32)
    @Builder.Default
    private String chartProvider = "AUTO";

    /** Fundamental data provider: AUTO, ALPHA_VANTAGE, FINNHUB, POLYGON. */
    @Column(length = 32)
    @Builder.Default
    private String fundamentalProvider = "AUTO";

    /**
     * Comma-separated watchlist that overrides the LiveTickerService default.
     * Blank/null means "use the hard-coded defaults".
     */
    @Column(length = 1024)
    private String watchlist;

    /**
     * Per-profile drawing settings stored as a JSON blob.
     * Contains default colours, line widths, styles, fill opacity, etc.
     * Null means "use application defaults".
     */
    @Column(columnDefinition = "TEXT")
    private String drawingSettingsJson;

    @PrePersist
    @PreUpdate
    protected void onPersist() {
        if (createdAt == null)           createdAt           = LocalDateTime.now();
        if (lastAccessedAt == null)      lastAccessedAt      = LocalDateTime.now();
        if (assetFocus == null)          assetFocus          = ProfileAssetFocus.MULTI;
        if (chartProvider == null)       chartProvider       = "AUTO";
        if (fundamentalProvider == null) fundamentalProvider = "AUTO";
    }

    /**
     * Never return {@code null} for assetFocus — legacy rows may have null in the DB.
     */
    public ProfileAssetFocus getAssetFocus() {
        return assetFocus != null ? assetFocus : ProfileAssetFocus.MULTI;
    }

    // ── Nested enum ──────────────────────────────────────────────────────────

    public enum ProfileAssetFocus {
        CRYPTO, STOCK, FOREX, MULTI;

        public String defaultSymbol() {
            return switch (this) {
                case CRYPTO -> "BTCUSDT";
                case STOCK  -> "AAPL";
                case FOREX  -> "EURUSD";
                case MULTI  -> "BTCUSDT";
            };
        }
    }
}
