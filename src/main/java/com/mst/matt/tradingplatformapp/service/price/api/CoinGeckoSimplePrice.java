package com.mst.matt.tradingplatformapp.service.price.api;

import com.google.gson.JsonObject;
import com.mst.matt.tradingplatformapp.model.Trade.AssetType;
import com.mst.matt.tradingplatformapp.service.price.JsonParseUtil;
import com.mst.matt.tradingplatformapp.service.price.PriceQuote;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * CoinGecko {@code /simple/price} — report.html:
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

    public PriceQuote toPriceQuote(String displaySymbol, String assetName) {
        return PriceQuote.builder()
                .symbol(displaySymbol)
                .assetName(assetName)
                .assetType(AssetType.CRYPTO)
                .price(usd)
                .changePct24h(usd24hChangePct)
                .marketCap(usdMarketCap)
                .currency("USD")
                .exchange("CoinGecko")
                .timestamp(LocalDateTime.now())
                .isUp(usd24hChangePct.compareTo(BigDecimal.ZERO) >= 0)
                .build();
    }
}
