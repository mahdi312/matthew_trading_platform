package com.mst.matt.marketservice.client.api;

import com.google.gson.JsonObject;
import com.mst.matt.marketservice.client.JsonParseUtil;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * CoinGecko {@code /simple/price} coin node.
 * {@code {"bitcoin":{"usd":63329,"usd_24h_change":1.26,...}}}
 */
public record CoinGeckoSimplePrice(
        BigDecimal usd,
        BigDecimal usd24hChangePct,
        BigDecimal usdMarketCap) {

    public static Optional<CoinGeckoSimplePrice> fromCoinNode(JsonObject coinNode) {
        if (coinNode == null) return Optional.empty();
        BigDecimal usd = JsonParseUtil.asBigDecimal(coinNode, "usd");
        if (usd.compareTo(BigDecimal.ZERO) == 0) return Optional.empty();
        return Optional.of(new CoinGeckoSimplePrice(
                usd,
                JsonParseUtil.asBigDecimal(coinNode, "usd_24h_change"),
                JsonParseUtil.asBigDecimal(coinNode, "usd_market_cap")));
    }
}
