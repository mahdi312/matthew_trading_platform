package com.mst.matt.contracts.provider.fundamentals;

import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.dto.CompanyFundamentalsDto;
import com.mst.matt.contracts.provider.dto.CryptoTokenomicsDto;
import com.mst.matt.contracts.provider.dto.ForexMacroIndicatorsDto;

import java.util.List;
import java.util.Optional;

/**
 * Unified, asset-class-aware contract for fetching fundamental / macro data.
 *
 * <h3>Asset class coverage</h3>
 * <ul>
 *   <li>{@link AssetClass#STOCK}  — company financials, valuation, dividends,
 *       analyst consensus ({@link CompanyFundamentalsDto})</li>
 *   <li>{@link AssetClass#CRYPTO} — tokenomics, on-chain metrics, vesting,
 *       DeFi TVL ({@link CryptoTokenomicsDto})</li>
 *   <li>{@link AssetClass#FOREX}  — macro indicators: interest rates, CPI,
 *       GDP, trade balance ({@link ForexMacroIndicatorsDto})</li>
 * </ul>
 *
 * <h3>Implementation homes</h3>
 * <p>Concrete implementations live in {@code reference-data-service}, NOT here.
 * Only the no-op mock ({@code NoOpFundamentalsProvider}) is wired in Step 4.5.</p>
 *
 * <h3>Registration</h3>
 * <p>Implementations are registered in the
 * {@link com.mst.matt.contracts.provider.registry.ProviderRegistry} keyed by
 * {@code (AssetClass, providerName)}. The registry handles fallback and
 * circuit-breaker logic transparently.</p>
 */
public interface FundamentalsProvider {

    /**
     * Returns the logical provider name used for registry key construction
     * (e.g., "FINNHUB", "ALPHA_VANTAGE", "COINGECKO", "FIXER").
     */
    String providerName();

    /**
     * Returns the asset classes this provider can serve fundamentals for.
     *
     * @return non-empty list of supported asset classes
     */
    List<AssetClass> supportedAssetClasses();

    // ── Stock / equity fundamentals ───────────────────────────────────────────

    /**
     * Fetch company financial fundamentals for an equity symbol.
     *
     * @param symbol canonical platform ticker (e.g., "AAPL", "MSFT")
     * @return an {@link Optional} containing the fundamentals,
     *         or {@link Optional#empty()} if the symbol is not found
     * @throws UnsupportedOperationException if this provider does not support
     *         {@link AssetClass#STOCK} — check {@link #supportedAssetClasses()} first
     */
    Optional<CompanyFundamentalsDto> getCompanyFundamentals(String symbol);

    // ── Crypto tokenomics ─────────────────────────────────────────────────────

    /**
     * Fetch tokenomics and on-chain fundamentals for a cryptocurrency.
     *
     * @param symbol canonical platform ticker (e.g., "BTC", "ETH", "SOL")
     * @return an {@link Optional} containing the tokenomics data,
     *         or {@link Optional#empty()} if the symbol is not found
     * @throws UnsupportedOperationException if this provider does not support
     *         {@link AssetClass#CRYPTO}
     */
    Optional<CryptoTokenomicsDto> getCryptoTokenomics(String symbol);

    // ── Forex macro indicators ────────────────────────────────────────────────

    /**
     * Fetch macroeconomic indicators for a forex pair or base currency.
     *
     * @param symbol canonical currency pair or currency code
     *               (e.g., "EUR/USD", "USD", "EUR")
     * @return an {@link Optional} containing the macro data,
     *         or {@link Optional#empty()} if the symbol is not found
     * @throws UnsupportedOperationException if this provider does not support
     *         {@link AssetClass#FOREX}
     */
    Optional<ForexMacroIndicatorsDto> getForexMacroIndicators(String symbol);
}
