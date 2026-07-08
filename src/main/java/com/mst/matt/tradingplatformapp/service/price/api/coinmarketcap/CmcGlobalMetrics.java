package com.mst.matt.tradingplatformapp.service.price.api.coinmarketcap;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

/**
 * Response for {@code GET /v1/global-metrics/quotes/latest}.
 */
public final class CmcGlobalMetrics {

    private CmcGlobalMetrics() {}

    public record GlobalQuote(
            @SerializedName("total_market_cap")              double totalMarketCap,
            @SerializedName("total_volume_24h")              double totalVolume24h,
            @SerializedName("total_volume_24h_reported")     double totalVolume24hReported,
            @SerializedName("altcoin_volume_24h")            double altcoinVolume24h,
            @SerializedName("altcoin_market_cap")            double altcoinMarketCap,
            @SerializedName("last_updated")                  String lastUpdated
    ) {}

    public record GlobalData(
            @SerializedName("btc_dominance")                 double btcDominance,
            @SerializedName("eth_dominance")                 double ethDominance,
            @SerializedName("active_cryptocurrencies")       int    activeCryptocurrencies,
            @SerializedName("total_cryptocurrencies")        int    totalCryptocurrencies,
            @SerializedName("active_market_pairs")           int    activeMarketPairs,
            @SerializedName("active_exchanges")              int    activeExchanges,
            @SerializedName("total_exchanges")               int    totalExchanges,
            @SerializedName("last_updated")                  String lastUpdated,
            @SerializedName("quote")                         Map<String, GlobalQuote> quote
    ) {
        public GlobalQuote usdQuote() {
            return quote != null ? quote.get("USD") : null;
        }
    }

    public record GlobalMetricsResponse(
            @SerializedName("data")   GlobalData data,
            @SerializedName("status") CmcStatus  status
    ) {}
}
