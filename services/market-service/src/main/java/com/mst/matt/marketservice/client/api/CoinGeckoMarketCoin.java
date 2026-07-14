package com.mst.matt.marketservice.client.api;

import com.google.gson.JsonObject;
import com.mst.matt.marketservice.client.JsonParseUtil;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * CoinGecko {@code /coins/markets} row.
 */
public record CoinGeckoMarketCoin(
        String id,
        String symbol,
        String name,
        BigDecimal currentPrice,
        BigDecimal priceChange24h,
        BigDecimal priceChangePct24h,
        BigDecimal high24h,
        BigDecimal low24h,
        BigDecimal totalVolume,
        BigDecimal marketCap) {

    public static Optional<CoinGeckoMarketCoin> fromJson(JsonObject coin) {
        if (coin == null) return Optional.empty();
        BigDecimal price = JsonParseUtil.asBigDecimal(coin, "current_price");
        if (price.compareTo(BigDecimal.ZERO) == 0) return Optional.empty();
        return Optional.of(new CoinGeckoMarketCoin(
                text(coin, "id"),
                text(coin, "symbol"),
                text(coin, "name"),
                price,
                JsonParseUtil.asBigDecimal(coin, "price_change_24h"),
                JsonParseUtil.asBigDecimal(coin, "price_change_percentage_24h"),
                JsonParseUtil.asBigDecimal(coin, "high_24h"),
                JsonParseUtil.asBigDecimal(coin, "low_24h"),
                JsonParseUtil.asBigDecimal(coin, "total_volume"),
                JsonParseUtil.asBigDecimal(coin, "market_cap")));
    }

    private static String text(JsonObject o, String key) {
        return o.has(key) ? o.get(key).getAsString() : "";
    }
}
