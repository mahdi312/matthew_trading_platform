package com.mst.matt.tradingplatformapp.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

// NOTE: UserProfile stores a per-user "trading profile" (portfolio/account).
// Linking to AppUser enables per-user isolation of trade journal data.

/**
 * Represents an individual trader profile.
 * All trades, alerts, and indicator configs are scoped to a profile.
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
    private boolean active;

    /**
     * The AppUser who owns this profile.
     * Null for legacy profiles created before user auth was required;
     * new profiles created after login will always have this set.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_user_id")
    private AppUser appUser;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime lastAccessedAt;

    /** Primary asset class for this profile (drives default symbol & provider chain).
     *
     * <p>P2 (LOG-FIX): {@code nullable = false} + {@link #getAssetFocus()} fallback +
     * {@link #onCreate()} default ensure no caller ever observes {@code null}, which
     * was the root cause of the {@code NullPointerException} thrown from
     * {@code YearlyProfitController.setProfile}, {@code ProfileSettingsController.setProfile},
     * and {@code ChartController.refreshProviderCombo}. */
    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    @Builder.Default
    private ProfileAssetFocus assetFocus = ProfileAssetFocus.MULTI;

    @Column(length = 32)
    private String defaultSymbol;

    /** {@link com.mst.matt.tradingplatformapp.service.price.MarketDataProvider} name or AUTO. */
    @Column(length = 32)
    @Builder.Default
    private String chartProvider = "AUTO";

    /** Fundamental data provider: AUTO, ALPHA_VANTAGE, FINNHUB, POLYGON. */
    @Column(length = 32)
    @Builder.Default
    private String fundamentalProvider = "AUTO";

    /**
     * T-12: comma-separated watchlist that overrides the LiveTickerService default.
     * Blank/null means “use the hard-coded defaults”. Symbols are normalised to upper case
     * when persisted.
     */
    @Column(length = 1024)
    private String watchlist;

    /**
     * Per-profile drawing settings stored as a JSON blob.
     * Contains default colours, line widths, styles, fill opacity, etc.
     * Null means "use application defaults" ({@link GlobalDrawingSettings}).
     *
     * <p>Issue 7.1 fix: drawing settings are now scoped to the profile, not to the machine.
     */
    @Column(columnDefinition = "TEXT")
    private String drawingSettingsJson;

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Trade> trades;

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<PriceAlert> alerts;

    @OneToOne(mappedBy = "profile", cascade = CascadeType.ALL)
    private IndicatorConfig indicatorConfig;

    @PrePersist
    @PreUpdate                                              // P2 (LOG-FIX): also patch updates
    protected void onCreate() {
        if (createdAt == null)       createdAt       = LocalDateTime.now();
        if (lastAccessedAt == null)  lastAccessedAt  = LocalDateTime.now();
        if (assetFocus == null)      assetFocus      = ProfileAssetFocus.MULTI;
        if (chartProvider == null)   chartProvider   = "AUTO";
        if (fundamentalProvider == null) fundamentalProvider = "AUTO";
        // watchlist may legitimately remain null — LiveTickerService falls back to defaults.
    }

    /**
     * P2 (LOG-FIX): never return {@code null} for the asset focus. Older rows that
     * predate the {@code nullable=false} migration can still contain {@code null};
     * we fall back to {@link ProfileAssetFocus#MULTI} so the UI keeps working.
     */
    public ProfileAssetFocus getAssetFocus() {
        return assetFocus != null ? assetFocus : ProfileAssetFocus.MULTI;
    }

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