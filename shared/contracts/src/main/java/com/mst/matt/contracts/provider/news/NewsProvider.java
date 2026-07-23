package com.mst.matt.contracts.provider.news;

import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.dto.NewsArticleDto;
import com.mst.matt.contracts.provider.registry.DataProvider;

import java.time.Instant;
import java.util.List;

/**
 * Unified contract for raw news / article aggregation per symbol or asset class.
 *
 * <h3>Coverage</h3>
 * <p>Returns normalized {@link NewsArticleDto} objects so that no consumer
 * (AI tab, Analysis tab, Trade Journal) ever sees a provider-specific shape.</p>
 *
 * <h3>Supported providers (future implementations)</h3>
 * <ul>
 *   <li><b>Finnhub</b> — equity and crypto news with sentiment labels</li>
 *   <li><b>Benzinga</b> — US equity news, earnings calendar news</li>
 *   <li><b>CryptoPanic</b> — crypto-specific news aggregator</li>
 *   <li><b>NewsAPI</b> — general news by keyword / symbol</li>
 * </ul>
 *
 * <h3>Implementation home</h3>
 * <p>Concrete implementations live in {@code reference-data-service}.
 * Only the no-op mock ({@code NoOpNewsProvider}) is wired in Step 4.5.</p>
 *
 * <h3>Registry</h3>
 * <p>Implementations are registered in the
 * {@link com.mst.matt.contracts.provider.registry.ProviderRegistry} keyed by
 * {@code (AssetClass, providerName)}.</p>
 */
public interface NewsProvider extends DataProvider {

    /**
     * Returns the logical provider name (e.g., "FINNHUB", "BENZINGA",
     * "CRYPTOPANIC", "NEWSAPI").
     */
    String providerName();

    /**
     * Returns the asset classes this provider can supply news for.
     *
     * @return non-empty list of supported asset classes
     */
    List<AssetClass> supportedAssetClasses();

    // ── Symbol-level news ─────────────────────────────────────────────────────

    /**
     * Fetch the most recent news articles for a specific symbol.
     *
     * @param symbol     canonical platform symbol (e.g., "AAPL", "BTCUSDT", "EURUSD")
     * @param assetClass asset class context for provider routing
     * @param limit      maximum number of articles to return
     * @return list of {@link NewsArticleDto} ordered most-recent-first;
     *         empty list if no news is found
     */
    List<NewsArticleDto> getNewsBySymbol(String symbol, AssetClass assetClass, int limit);

    /**
     * Fetch news articles for a symbol within a time range.
     *
     * @param symbol     canonical platform symbol
     * @param assetClass asset class context
     * @param from       range start (inclusive)
     * @param to         range end (inclusive)
     * @param limit      maximum number of articles to return
     * @return list of {@link NewsArticleDto} ordered most-recent-first
     */
    List<NewsArticleDto> getNewsBySymbol(String symbol,
                                          AssetClass assetClass,
                                          Instant from,
                                          Instant to,
                                          int limit);

    // ── Asset-class-level news ────────────────────────────────────────────────

    /**
     * Fetch the most recent news for an entire asset class
     * (e.g., top crypto news, top stock market news).
     *
     * @param assetClass asset class to fetch news for
     * @param limit      maximum number of articles to return
     * @return list of {@link NewsArticleDto} ordered most-recent-first
     */
    List<NewsArticleDto> getNewsByAssetClass(AssetClass assetClass, int limit);

    // ── Keyword search ────────────────────────────────────────────────────────

    /**
     * Search news articles by keyword / phrase.
     *
     * @param query      free-text search query
     * @param assetClass optional asset class filter; {@code null} for all classes
     * @param limit      maximum number of articles to return
     * @return list of matching {@link NewsArticleDto} objects ordered by relevance
     */
    List<NewsArticleDto> searchNews(String query, AssetClass assetClass, int limit);

    // ── Multiple symbols ──────────────────────────────────────────────────────

    /**
     * Fetch news for multiple symbols in a single call (batch).
     *
     * <p>Implementations that do not support batching should iterate
     * {@link #getNewsBySymbol} internally, but are encouraged to use
     * native batch endpoints when available.</p>
     *
     * @param symbols    list of canonical platform symbols
     * @param assetClass asset class context (assumed the same for all symbols)
     * @param limitEach  maximum number of articles per symbol
     * @return combined list of {@link NewsArticleDto} ordered most-recent-first;
     *         articles from different symbols are interleaved by publish time
     */
    List<NewsArticleDto> getNewsBatch(List<String> symbols, AssetClass assetClass, int limitEach);
}
