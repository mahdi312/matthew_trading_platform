package com.mst.matt.tradingplatformapp.service.price.api.coinmarketcap;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

/**
 * Response model for {@code GET /v2/cryptocurrency/market-pairs/latest}.
 */
public final class CmcMarketPairs {

    private CmcMarketPairs() {}

    public record MarketPairQuote(
            @SerializedName("price")         double price,
            @SerializedName("volume_24h")    double volume24h,
            @SerializedName("last_updated")  String lastUpdated
    ) {}

    public record MarketPair(
            @SerializedName("exchange")           ExchangeRef           exchange,
            @SerializedName("market_id")          long                  marketId,
            @SerializedName("market_pair")        String                marketPair,
            @SerializedName("category")           String                category,
            @SerializedName("fee_type")           String                feeType,
            @SerializedName("market_pair_base")   CurrencyRef           marketPairBase,
            @SerializedName("market_pair_quote")  CurrencyRef           marketPairQuote,
            @SerializedName("quote")              Map<String, MarketPairQuote> quote
    ) {}

    public record ExchangeRef(
            @SerializedName("id")   int    id,
            @SerializedName("name") String name,
            @SerializedName("slug") String slug
    ) {}

    public record CurrencyRef(
            @SerializedName("currency_id")     int    currencyId,
            @SerializedName("currency_symbol") String currencySymbol,
            @SerializedName("currency_type")   String currencyType
    ) {}

    public record MarketPairsData(
            @SerializedName("id")           int              id,
            @SerializedName("name")         String           name,
            @SerializedName("symbol")       String           symbol,
            @SerializedName("num_market_pairs") int          numMarketPairs,
            @SerializedName("market_pairs") List<MarketPair> marketPairs
    ) {}

    public record MarketPairsResponse(
            @SerializedName("data")   MarketPairsData data,
            @SerializedName("status") CmcStatus       status
    ) {}
}
