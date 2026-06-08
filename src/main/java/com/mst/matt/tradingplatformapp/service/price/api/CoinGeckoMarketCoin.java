package com.mst.matt.tradingplatformapp.service.price.api;

import com.google.gson.JsonObject;
import com.mst.matt.tradingplatformapp.model.Trade.AssetType;
import com.mst.matt.tradingplatformapp.service.price.JsonParseUtil;
import com.mst.matt.tradingplatformapp.service.price.PriceQuote;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * CoinGecko {@code /coins/markets} row — report.html "Coins Market".
 * Also supports {@code /simple/price} via {@link CoinGeckoSimplePrice}.
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

    public PriceQuote toPriceQuote(String displaySymbol) {
        return PriceQuote.builder()
                .symbol(displaySymbol)
                .assetName(name.isBlank() ? displaySymbol : name)
                .assetType(AssetType.CRYPTO)
                .price(currentPrice)
                .high24h(high24h)
                .low24h(low24h)
                .change24h(priceChange24h)
                .changePct24h(priceChangePct24h)
                .volume24h(totalVolume)
                .marketCap(marketCap)
                .currency("USD")
                .exchange("CoinGecko")
                .timestamp(LocalDateTime.now())
                .isUp(priceChangePct24h.compareTo(BigDecimal.ZERO) >= 0)
                .build();
    }

    private static String text(JsonObject o, String key) {
        return o.has(key) ? o.get(key).getAsString() : "";
    }
}
