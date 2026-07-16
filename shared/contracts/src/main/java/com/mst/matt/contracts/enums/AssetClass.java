package com.mst.matt.contracts.enums;

/**
 * High-level asset-class grouping used by the platform-wide data-provider
 * abstraction layer (Step 4.5).
 *
 * <h3>Purpose</h3>
 * <p>While {@link InstrumentType} gives fine-grained instrument detail
 * (spot vs futures, ETF vs equity), {@code AssetClass} is the coarse key
 * by which the {@link com.mst.matt.contracts.provider.registry.ProviderRegistry}
 * resolves the correct data-provider implementation at runtime.</p>
 *
 * <p>Every provider interface in the
 * {@code com.mst.matt.contracts.provider} package is parameterised by
 * {@code AssetClass} — the registry routes requests to the implementation
 * that is registered for the matching {@code (AssetClass, providerName)} pair.</p>
 *
 * <h3>Mapping to {@link InstrumentType}</h3>
 * <pre>
 *   CRYPTO_SPOT / CRYPTO_FUTURES  →  CRYPTO
 *   EQUITY / ETF_INDEX / COMMODITY →  STOCK
 *   FOREX                          →  FOREX
 *   NFT                            →  NFT
 * </pre>
 * See {@link InstrumentType#toAssetClass()} for the canonical derivation.
 *
 * <h3>Provider coverage</h3>
 * <ul>
 *   <li>{@code STOCK}  — Alpha Vantage, Polygon, Finnhub, Yahoo Finance, Marketstack …</li>
 *   <li>{@code CRYPTO} — CoinGecko, CoinMarketCap, Binance, BitUnix, …</li>
 *   <li>{@code FOREX}  — OANDA, Fixer.io, Frankfurter, Twelve Data …</li>
 *   <li>{@code NFT}    — OpenSea, Reservoir …</li>
 * </ul>
 */
public enum AssetClass {

    /**
     * Traditional equities, ETFs, indices, and commodities traded on
     * regulated stock exchanges (e.g., AAPL on NASDAQ, GLD ETF, XAU/USD).
     *
     * <p>Data sources: Alpha Vantage, Polygon, Finnhub, Marketstack,
     * Yahoo Finance, Twelve Data (equity endpoint).</p>
     */
    STOCK,

    /**
     * Cryptocurrency spot and derivatives markets (BTC, ETH, altcoins,
     * perpetual futures, etc.).
     *
     * <p>Data sources: CoinGecko, CoinMarketCap, Binance, BitUnix,
     * Twelve Data (crypto endpoint).</p>
     */
    CRYPTO,

    /**
     * Foreign-exchange currency pairs (EUR/USD, GBP/JPY, etc.) and
     * macro-economic indicators tied to FX markets.
     *
     * <p>Data sources: OANDA, Fixer.io, Frankfurter, Twelve Data
     * (forex endpoint).</p>
     */
    FOREX,

    /**
     * Non-Fungible Tokens — on-chain digital collectibles with collection,
     * floor-price, rarity, and metadata characteristics.
     *
     * <p>Data sources: OpenSea API, Reservoir.</p>
     */
    NFT
}
