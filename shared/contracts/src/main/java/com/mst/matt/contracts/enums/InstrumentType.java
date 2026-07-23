package com.mst.matt.contracts.enums;

/**
 * Broad classification of tradeable instruments supported across the platform.
 *
 * <p>Used in DTOs, routing logic, and capability checks so that services
 * can decide how to handle a given instrument without inspecting raw symbols.</p>
 *
 * <p>See also {@link AssetClass} for the higher-level grouping used by the
 * data-provider abstraction layer (Step 4.5). {@code InstrumentType} gives
 * fine-grained detail (spot vs futures, ETF vs equity); {@code AssetClass}
 * gives the coarse category that data-providers are keyed on.</p>
 */
public enum InstrumentType {

    /**
     * Cryptocurrency spot market pair (e.g., BTC/USDT on a centralised exchange).
     * Maps to {@link AssetClass#CRYPTO}.
     */
    CRYPTO_SPOT,

    /**
     * Cryptocurrency perpetual or dated futures contract
     * (e.g., BTCUSDT-PERP on BitUnix).
     * Maps to {@link AssetClass#CRYPTO}.
     */
    CRYPTO_FUTURES,

    /**
     * Traditional equity (stock) listed on a recognised exchange
     * (e.g., AAPL on NASDAQ via Alpaca).
     * Maps to {@link AssetClass#STOCK}.
     */
    EQUITY,

    /**
     * Forex / currency pair (e.g., EUR/USD).
     * Maps to {@link AssetClass#FOREX}.
     */
    FOREX,

    /**
     * Commodity or precious metal (e.g., XAU/USD, WTI crude).
     * Maps to {@link AssetClass#STOCK} (treated as equity-adjacent) or
     * {@link AssetClass#FOREX} for FX-priced commodities.
     */
    COMMODITY,

    /**
     * Exchange-Traded Fund or Index instrument.
     * Maps to {@link AssetClass#STOCK}.
     */
    ETF_INDEX,

    /**
     * Non-Fungible Token (NFT) or digital collectible.
     * Maps to {@link AssetClass#NFT}.
     */
    NFT;

    /**
     * Derive the coarse {@link AssetClass} that this instrument belongs to.
     * Used by the data-provider abstraction layer to route requests to the
     * correct {@link com.mst.matt.contracts.provider.registry.ProviderRegistry}.
     *
     * @return the {@link AssetClass} that best describes this instrument type
     */
    public AssetClass toAssetClass() {
        return switch (this) {
            case CRYPTO_SPOT, CRYPTO_FUTURES -> AssetClass.CRYPTO;
            case EQUITY, ETF_INDEX, COMMODITY -> AssetClass.STOCK;
            case FOREX                        -> AssetClass.FOREX;
            case NFT                          -> AssetClass.NFT;
        };
    }
}
