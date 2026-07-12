package com.mst.matt.contracts.provider.sentiment;

import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.dto.SentimentSnapshotDto;

import java.util.List;
import java.util.Optional;

/**
 * Unified contract for social / crowd sentiment data and fear-and-greed
 * style indices.
 *
 * <h3>Coverage</h3>
 * <p>Returns normalized {@link SentimentSnapshotDto} objects. No consumer
 * (AI tab, Analysis tab) should ever see a provider-specific shape.</p>
 *
 * <h3>Supported providers (future implementations)</h3>
 * <ul>
 *   <li><b>Alternative.me</b> — Crypto Fear &amp; Greed Index</li>
 *   <li><b>LunarCrush</b> — social volume and sentiment per crypto asset</li>
 *   <li><b>StockTwits</b> — equity crowd sentiment (bullish/bearish ratios)</li>
 *   <li><b>Reddit/Pushshift</b> — subreddit mention volume</li>
 * </ul>
 *
 * <h3>Implementation home</h3>
 * <p>Concrete implementations live in {@code reference-data-service}.
 * Only the no-op mock ({@code NoOpSentimentProvider}) is wired in Step 4.5.</p>
 *
 * <h3>Registry</h3>
 * <p>Implementations are registered in the
 * {@link com.mst.matt.contracts.provider.registry.ProviderRegistry} keyed by
 * {@code (AssetClass, providerName)}.</p>
 */
public interface SentimentProvider {

    /**
     * Returns the logical provider name (e.g., "ALTERNATIVE_ME",
     * "LUNARCRUSH", "STOCKTWITS").
     */
    String providerName();

    /**
     * Returns the asset classes this provider can supply sentiment for.
     *
     * @return non-empty list of supported asset classes
     */
    List<AssetClass> supportedAssetClasses();

    // ── Symbol-level sentiment ────────────────────────────────────────────────

    /**
     * Fetch the current sentiment snapshot for a specific symbol.
     *
     * @param symbol     canonical platform symbol (e.g., "BTC", "AAPL", "EURUSD")
     * @param assetClass asset class context
     * @return an {@link Optional} containing the snapshot,
     *         or empty if no data is available for this symbol
     */
    Optional<SentimentSnapshotDto> getSentiment(String symbol, AssetClass assetClass);

    /**
     * Fetch sentiment snapshots for multiple symbols in a single call.
     *
     * @param symbols    list of canonical platform symbols
     * @param assetClass asset class context (assumed the same for all symbols)
     * @return list of {@link SentimentSnapshotDto} in the same order as the input;
     *         entries may contain {@code null} or zeroed-out values if a symbol
     *         is not found
     */
    List<SentimentSnapshotDto> getSentimentBatch(List<String> symbols, AssetClass assetClass);

    // ── Asset-class-level indices ─────────────────────────────────────────────

    /**
     * Fetch a market-wide sentiment index for an asset class
     * (e.g., the Crypto Fear &amp; Greed Index for {@link AssetClass#CRYPTO},
     * the CNN Fear &amp; Greed for {@link AssetClass#STOCK}).
     *
     * @param assetClass the asset class whose index to fetch
     * @return normalized {@link SentimentSnapshotDto} representing the index;
     *         {@link SentimentSnapshotDto#getSymbol()} will be {@code null}
     * @throws UnsupportedOperationException if no market-wide index exists
     *         for this provider / asset class combination
     */
    SentimentSnapshotDto getMarketSentimentIndex(AssetClass assetClass);

    // ── Trending ──────────────────────────────────────────────────────────────

    /**
     * Fetch the most-discussed / trending symbols by social volume.
     *
     * @param assetClass asset class to search within
     * @param limit      maximum number of symbols to return
     * @return list of {@link SentimentSnapshotDto} ordered by social volume
     *         (highest first), or empty list if not supported
     */
    List<SentimentSnapshotDto> getTrendingByVolume(AssetClass assetClass, int limit);

    /**
     * Fetch the most bullish symbols (highest positive sentiment score).
     *
     * @param assetClass asset class to search within
     * @param limit      maximum number of symbols to return
     * @return list of {@link SentimentSnapshotDto} ordered by
     *         {@link SentimentSnapshotDto#getSentimentScore()} descending
     */
    List<SentimentSnapshotDto> getMostBullish(AssetClass assetClass, int limit);

    /**
     * Fetch the most bearish symbols (lowest / most negative sentiment score).
     *
     * @param assetClass asset class to search within
     * @param limit      maximum number of symbols to return
     * @return list of {@link SentimentSnapshotDto} ordered by
     *         {@link SentimentSnapshotDto#getSentimentScore()} ascending
     */
    List<SentimentSnapshotDto> getMostBearish(AssetClass assetClass, int limit);
}
