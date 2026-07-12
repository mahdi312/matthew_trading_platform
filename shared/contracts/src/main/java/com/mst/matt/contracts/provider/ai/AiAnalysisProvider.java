package com.mst.matt.contracts.provider.ai;

import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.dto.AiMarketSummaryDto;
import com.mst.matt.contracts.provider.dto.AiSignalDto;
import com.mst.matt.contracts.provider.dto.AiTradeJournalCritiqueDto;
import com.mst.matt.contracts.provider.dto.NewsArticleDto;
import com.mst.matt.contracts.provider.dto.NormalizedOhlcvBar;
import com.mst.matt.contracts.provider.registry.DataProvider;

import java.util.List;

/**
 * Unified contract for AI-powered market analysis: summarization, sentiment
 * scoring, signal/insight generation, and trade-journal critique.
 *
 * <h3>What this wraps</h3>
 * <p>This interface abstracts the LLM/AI backend (OpenAI, Gemini, Claude, a
 * fine-tuned model …). Implementations translate the platform's normalized
 * DTOs into prompts, call the AI API, parse the response, and return
 * normalized result DTOs.</p>
 *
 * <h3>Consumer tabs (reference)</h3>
 * <ul>
 *   <li><b>AI tab</b> — {@link #summarizeMarket}, {@link #scoreNewsSentiment},
 *       {@link #generateSignals}</li>
 *   <li><b>Analysis tab</b> — {@link #summarizeMarket},
 *       {@link #generateSignals}</li>
 *   <li><b>Trade Journal tab</b> — {@link #critiqueTradeJournalEntry}</li>
 * </ul>
 *
 * <h3>Implementation home</h3>
 * <p>Concrete implementations live in {@code ai-service}.
 * Only the no-op mock ({@code NoOpAiAnalysisProvider}) is wired in Step 4.5.</p>
 *
 * <h3>Registry</h3>
 * <p>Implementations are registered in the
 * {@link com.mst.matt.contracts.provider.registry.ProviderRegistry} keyed by
 * {@code (AssetClass, providerName)}. The registry handles fallback and
 * circuit-breaker logic — if the primary AI provider fails, a fallback
 * (cheaper/simpler) provider can be tried.</p>
 */
public interface AiAnalysisProvider extends DataProvider {

    /**
     * Returns the logical provider name (e.g., "OPENAI_GPT4O",
     * "GEMINI_PRO", "CLAUDE_SONNET", "LOCAL_LLAMA").
     */
    String providerName();

    /**
     * Returns the asset classes this AI provider can analyse.
     * Most LLM-based providers support all asset classes.
     *
     * @return non-empty list of supported asset classes
     */
    List<AssetClass> supportedAssetClasses();

    // ── Market summarization ──────────────────────────────────────────────────

    /**
     * Generate a narrative summary of current market conditions for a symbol
     * by combining OHLCV data and recent news articles.
     *
     * @param symbol       canonical platform symbol (e.g., "BTCUSDT", "AAPL")
     * @param assetClass   asset class context
     * @param ohlcvBars    recent OHLCV bars used as market-data context
     * @param newsArticles recent news articles used as fundamental/sentiment context
     * @return normalized {@link AiMarketSummaryDto} with summary and sentiment
     */
    AiMarketSummaryDto summarizeMarket(String symbol,
                                        AssetClass assetClass,
                                        List<NormalizedOhlcvBar> ohlcvBars,
                                        List<NewsArticleDto> newsArticles);

    // ── Sentiment scoring ─────────────────────────────────────────────────────

    /**
     * Score the sentiment of a list of news articles for a specific symbol.
     *
     * <p>Returns the same articles enriched with
     * {@link NewsArticleDto#getSentimentScore()} and
     * {@link NewsArticleDto#getSentimentLabel()} populated by the AI.</p>
     *
     * @param symbol       canonical platform symbol
     * @param assetClass   asset class context
     * @param newsArticles articles to score (without sentiment data)
     * @return the same articles with sentiment fields populated;
     *         order is preserved
     */
    List<NewsArticleDto> scoreNewsSentiment(String symbol,
                                             AssetClass assetClass,
                                             List<NewsArticleDto> newsArticles);

    // ── Signal generation ─────────────────────────────────────────────────────

    /**
     * Generate discrete trading signals / insights from market data and news.
     *
     * <p>Signals are <em>informational only</em> — the platform never
     * auto-executes trades based on AI signals.</p>
     *
     * @param symbol       canonical platform symbol
     * @param assetClass   asset class context
     * @param ohlcvBars    recent OHLCV bars
     * @param newsArticles recent news articles
     * @return list of {@link AiSignalDto} ordered by signal strength (highest first);
     *         empty list if no clear signals are identified
     */
    List<AiSignalDto> generateSignals(String symbol,
                                       AssetClass assetClass,
                                       List<NormalizedOhlcvBar> ohlcvBars,
                                       List<NewsArticleDto> newsArticles);

    // ── Trade journal critique ────────────────────────────────────────────────

    /**
     * Generate an AI-driven critique of a trade journal entry.
     *
     * <p>Called by the Trade Journal tab when a user requests AI feedback
     * on a specific trade. The {@code tradeContext} is a free-form string
     * containing the trade details (entry/exit price, size, PnL, user notes,
     * market conditions) pre-serialized by the calling service.</p>
     *
     * @param tradeId      internal trade ID (for correlation in the response)
     * @param tradeContext JSON or structured-text representation of the trade
     *                     and optional market context at the time of the trade
     * @param assetClass   asset class of the traded instrument
     * @return normalized {@link AiTradeJournalCritiqueDto}
     */
    AiTradeJournalCritiqueDto critiqueTradeJournalEntry(String tradeId,
                                                         String tradeContext,
                                                         AssetClass assetClass);
}
