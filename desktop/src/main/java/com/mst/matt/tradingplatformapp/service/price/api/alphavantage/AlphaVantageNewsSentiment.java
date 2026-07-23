package com.mst.matt.tradingplatformapp.service.price.api.alphavantage;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * POJO for Alpha Vantage {@code NEWS_SENTIMENT} response (Alpha Intelligence™).
 *
 * <pre>
 * {
 *   "items": "50",
 *   "sentiment_score_definition": "...",
 *   "relevance_score_definition": "...",
 *   "feed": [
 *     {
 *       "title": "...",
 *       "url": "...",
 *       "time_published": "20260702T160000",
 *       "authors": [...],
 *       "summary": "...",
 *       "source": "...",
 *       "overall_sentiment_score": 0.25,
 *       "overall_sentiment_label": "Somewhat-Bullish",
 *       "ticker_sentiment": [...]
 *     }
 *   ]
 * }
 * </pre>
 */
public record AlphaVantageNewsSentiment(
        @SerializedName("items")                      String items,
        @SerializedName("sentiment_score_definition") String sentimentScoreDefinition,
        @SerializedName("relevance_score_definition") String relevanceScoreDefinition,
        @SerializedName("feed")                       List<Article> feed
) {

    public record Article(
            @SerializedName("title")                   String title,
            @SerializedName("url")                     String url,
            @SerializedName("time_published")          String timePublished,
            @SerializedName("authors")                 List<String> authors,
            @SerializedName("summary")                 String summary,
            @SerializedName("banner_image")            String bannerImage,
            @SerializedName("source")                  String source,
            @SerializedName("category_within_source")  String categoryWithinSource,
            @SerializedName("source_domain")           String sourceDomain,
            @SerializedName("topics")                  List<Topic> topics,
            @SerializedName("overall_sentiment_score") Double overallSentimentScore,
            @SerializedName("overall_sentiment_label") String overallSentimentLabel,
            @SerializedName("ticker_sentiment")        List<TickerSentiment> tickerSentiment
    ) {}

    public record Topic(
            @SerializedName("topic")           String topic,
            @SerializedName("relevance_score") String relevanceScore
    ) {}

    public record TickerSentiment(
            @SerializedName("ticker")                  String ticker,
            @SerializedName("relevance_score")         String relevanceScore,
            @SerializedName("ticker_sentiment_score")  String tickerSentimentScore,
            @SerializedName("ticker_sentiment_label")  String tickerSentimentLabel
    ) {}
}
