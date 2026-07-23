package com.mst.matt.contracts.provider.search;

import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.registry.DataProvider;
import com.mst.matt.contracts.provider.dto.SymbolSearchResultDto;

import java.util.List;
import java.util.Optional;

/**
 * Unified contract for cross-asset-class symbol search and lookup.
 *
 * <h3>Coverage</h3>
 * <p>Returns normalized {@link SymbolSearchResultDto} objects so that the
 * platform's symbol-search input (used in chart screens, watchlists, and order
 * placement) always sees a consistent shape, regardless of the underlying data
 * source.</p>
 *
 * <h3>Supported providers (future implementations)</h3>
 * <ul>
 *   <li><b>Finnhub</b> — stock symbol search ({@link AssetClass#STOCK})</li>
 *   <li><b>Alpha Vantage</b> — stock symbol search endpoint</li>
 *   <li><b>CoinGecko</b> — crypto coin/token search ({@link AssetClass#CRYPTO})</li>
 *   <li><b>Twelve Data</b> — cross-asset symbol search (stocks + forex + crypto)</li>
 *   <li><b>OpenSea / Reservoir</b> — NFT collection search ({@link AssetClass#NFT})</li>
 *   <li><b>Fixer / OANDA</b> — forex pair listing ({@link AssetClass#FOREX})</li>
 * </ul>
 *
 * <h3>Implementation home</h3>
 * <p>Concrete implementations live in {@code reference-data-service}.
 * Only the no-op mock ({@code NoOpSymbolSearchProvider}) is wired in Step 4.5.</p>
 *
 * <h3>Registry</h3>
 * <p>Implementations are registered in the
 * {@link com.mst.matt.contracts.provider.registry.ProviderRegistry} keyed by
 * {@code (AssetClass, providerName)}.</p>
 */
public interface SymbolSearchProvider extends DataProvider {

    /**
     * Returns the logical provider name (e.g., "FINNHUB", "COINGECKO",
     * "TWELVE_DATA", "ALPHA_VANTAGE").
     */
    String providerName();

    /**
     * Returns the asset classes this provider can search.
     *
     * @return non-empty list of supported asset classes
     */
    List<AssetClass> supportedAssetClasses();

    // ── Free-text search ──────────────────────────────────────────────────────

    /**
     * Search for symbols matching a free-text query across all supported
     * asset classes.
     *
     * @param query free-text query (e.g., "Apple", "BTC", "EUR/USD",
     *              "bored ape")
     * @param limit maximum number of results to return
     * @return list of {@link SymbolSearchResultDto} ordered by relevance
     *         (highest first); may be empty if no matches found
     */
    List<SymbolSearchResultDto> search(String query, int limit);

    /**
     * Search for symbols matching a free-text query within a specific
     * asset class.
     *
     * @param query      free-text query
     * @param assetClass asset class to restrict the search to
     * @param limit      maximum number of results to return
     * @return list of {@link SymbolSearchResultDto} ordered by relevance
     */
    List<SymbolSearchResultDto> search(String query, AssetClass assetClass, int limit);

    // ── Exact lookup ──────────────────────────────────────────────────────────

    /**
     * Resolve a canonical platform symbol to its full metadata.
     *
     * <p>Used to validate and enrich a symbol that the user has entered manually
     * or that has been loaded from stored state (e.g., watchlist, journal).</p>
     *
     * @param symbol     canonical platform symbol (exact match)
     * @param assetClass asset class hint for routing; can be {@code null}
     *                   for auto-detection (slower)
     * @return an {@link Optional} containing the resolved symbol info,
     *         or empty if not found
     */
    Optional<SymbolSearchResultDto> lookup(String symbol, AssetClass assetClass);

    // ── Browse / listing ──────────────────────────────────────────────────────

    /**
     * Fetch all available symbols for an asset class.
     *
     * <p>Useful for populating an initial symbol list (e.g., all supported
     * forex pairs, all supported crypto pairs). For large universes (equities)
     * this may be paginated — use {@code limit} and {@code offset}.</p>
     *
     * @param assetClass asset class to list symbols for
     * @param limit      maximum number of results
     * @param offset     pagination offset
     * @return list of {@link SymbolSearchResultDto} for the requested page
     */
    List<SymbolSearchResultDto> listAll(AssetClass assetClass, int limit, int offset);

    /**
     * Fetch the most popular / liquid symbols for an asset class.
     *
     * @param assetClass asset class context
     * @param limit      maximum number of results
     * @return list of top symbols ordered by popularity / liquidity descending
     */
    List<SymbolSearchResultDto> getTopSymbols(AssetClass assetClass, int limit);
}
