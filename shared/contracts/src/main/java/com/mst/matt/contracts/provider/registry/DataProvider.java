package com.mst.matt.contracts.provider.registry;

import com.mst.matt.contracts.enums.AssetClass;

import java.util.List;

/**
 * Marker interface that every data-provider interface in the platform must
 * extend (directly or indirectly).
 *
 * <h3>Purpose</h3>
 * <p>The generic {@link ProviderRegistry}{@code <T extends DataProvider>} uses
 * this bound to ensure only properly typed provider implementations can be
 * registered. It also provides the two methods the registry relies on to
 * build its internal lookup tables:</p>
 * <ul>
 *   <li>{@link #providerName()} — unique string key per implementation</li>
 *   <li>{@link #supportedAssetClasses()} — which asset classes this provider
 *       can serve</li>
 * </ul>
 *
 * <h3>Provider interfaces that extend this</h3>
 * <ul>
 *   <li>{@link com.mst.matt.contracts.provider.ohlcv.OhlcvDataProvider}</li>
 *   <li>{@link com.mst.matt.contracts.provider.fundamentals.FundamentalsProvider}</li>
 *   <li>{@link com.mst.matt.contracts.provider.nft.NftDataProvider}</li>
 *   <li>{@link com.mst.matt.contracts.provider.news.NewsProvider}</li>
 *   <li>{@link com.mst.matt.contracts.provider.ai.AiAnalysisProvider}</li>
 *   <li>{@link com.mst.matt.contracts.provider.sentiment.SentimentProvider}</li>
 *   <li>{@link com.mst.matt.contracts.provider.calendar.EconomicCalendarProvider}</li>
 *   <li>{@link com.mst.matt.contracts.provider.search.SymbolSearchProvider}</li>
 * </ul>
 */
public interface DataProvider {

    /**
     * Returns the logical provider name used as a registry key
     * (e.g., "ALPHA_VANTAGE", "BINANCE", "OPENAI_GPT4O").
     *
     * <p>Must be unique within a given provider interface type (i.e., two
     * {@code OhlcvDataProvider} implementations cannot share the same name,
     * but an {@code OhlcvDataProvider} and a {@code NewsProvider} may share
     * the same name if they belong to the same external service).</p>
     */
    String providerName();

    /**
     * Returns the asset classes this provider implementation can serve.
     *
     * @return non-empty, immutable list of {@link AssetClass} values
     */
    List<AssetClass> supportedAssetClasses();
}
