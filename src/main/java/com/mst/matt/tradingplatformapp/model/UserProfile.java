package com.mst.matt.tradingplatformapp.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Represents an individual trader profile.
 * All trades, alerts, and indicator configs are scoped to a profile.
 */
@Entity
@Table(name = "user_profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;                     // e.g. "Crypto Portfolio", "Stocks Journal"

    @Column
    private String avatarColor;              // Hex color for UI avatar badge

    @Column
    private String description;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime lastAccessedAt;

    /** Primary asset class for this profile (drives default symbol & provider chain). */
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
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

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Trade> trades;

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<PriceAlert> alerts;

    @OneToOne(mappedBy = "profile", cascade = CascadeType.ALL)
    private IndicatorConfig indicatorConfig;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null)       createdAt       = LocalDateTime.now();
        if (lastAccessedAt == null)  lastAccessedAt  = LocalDateTime.now();
        if (assetFocus == null)      assetFocus      = ProfileAssetFocus.MULTI;
        if (chartProvider == null)   chartProvider   = "AUTO";
        if (fundamentalProvider == null) fundamentalProvider = "AUTO";
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