package com.mst.matt.contracts.provider.dto;

import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.enums.InstrumentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Normalized result entry for a symbol search query.
 *
 * <p>Returned by {@link com.mst.matt.contracts.provider.search.SymbolSearchProvider}.
 * No consumer should ever see a provider-specific response shape.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SymbolSearchResultDto {

    // ── Identity ──────────────────────────────────────────────────────────────

    /**
     * Canonical platform symbol (e.g., "AAPL", "BTCUSDT", "EURUSD",
     * "boredapeyachtclub").
     * This is the symbol consumers should store and use when calling
     * other provider interfaces.
     */
    private String symbol;

    /**
     * Display name (e.g., "Apple Inc.", "Bitcoin / USDT", "Euro / US Dollar",
     * "Bored Ape Yacht Club").
     */
    private String displayName;

    /**
     * The underlying asset class this symbol belongs to.
     */
    private AssetClass assetClass;

    /**
     * Fine-grained instrument type within the asset class
     * (e.g., {@link InstrumentType#EQUITY}, {@link InstrumentType#CRYPTO_SPOT}).
     */
    private InstrumentType instrumentType;

    // ── Listing metadata ──────────────────────────────────────────────────────

    /**
     * Exchange or marketplace where this symbol is primarily listed
     * (e.g., "NASDAQ", "NYSE", "BINANCE", "OPENSEA").
     * May be {@code null} for forex pairs (no single exchange).
     */
    private String exchange;

    /**
     * ISO-4217 currency in which this instrument is quoted
     * (e.g., "USD", "USDT", "ETH").
     */
    private String quoteCurrency;

    /**
     * ISO-3166 alpha-2 country code for the issuing/listing country
     * (e.g., "US" for AAPL, "US" for BTC); may be {@code null} for
     * forex or cross-border assets.
     */
    private String country;

    // ── Provider info ─────────────────────────────────────────────────────────

    /**
     * The provider-specific symbol (may differ from {@link #symbol}).
     * Implementations should map this to the canonical {@link #symbol}
     * before returning. Exposed here for debugging / logging.
     */
    private String providerSymbol;

    /** Name of the data provider that returned this result. */
    private String providerName;

    // ── Relevance ─────────────────────────────────────────────────────────────

    /**
     * Search relevance score in the range [0.0 … 1.0]; higher = more relevant.
     * Used by consumers to sort or filter results.
     * {@code null} if the provider does not return relevance scores.
     */
    private Double relevanceScore;

    // ── Status ────────────────────────────────────────────────────────────────

    /**
     * {@code true} if this symbol is actively traded / listed.
     * {@code false} for delisted or deprecated symbols.
     */
    private boolean active;

    /**
     * {@code true} if this is a supported symbol in the current platform
     * (i.e., data providers for this asset class are configured and the
     * symbol can be used in charts, analysis, and trading).
     */
    private boolean platformSupported;
}
