package com.mst.matt.tradingplatformapp.service.price.api;

import com.google.gson.JsonObject;
import com.mst.matt.tradingplatformapp.model.Trade.AssetType;
import com.mst.matt.tradingplatformapp.service.price.JsonParseUtil;
import com.mst.matt.tradingplatformapp.service.price.PriceQuote;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/** Binance {@code GET /api/v3/ticker/24hr} — report.html "24hr Ticker Statistics". */
public record BinanceTicker24hResponse(
        String symbol,
        BigDecimal lastPrice,
        BigDecimal openPrice,
        BigDecimal highPrice,
        BigDecimal lowPrice,
        BigDecimal priceChange,
        BigDecimal priceChangePercent,
        BigDecimal volume) {

    public static Optional<BinanceTicker24hResponse> fromJson(JsonObject json) {
        if (json == null || !json.has("lastPrice")) return Optional.empty();
        BigDecimal last = JsonParseUtil.asBigDecimal(json, "lastPrice");
        if (last.compareTo(BigDecimal.ZERO) == 0) return Optional.empty();
        return Optional.of(new BinanceTicker24hResponse(
                json.has("symbol") ? json.get("symbol").getAsString() : "",
                last,
                JsonParseUtil.asBigDecimal(json, "openPrice"),
                JsonParseUtil.asBigDecimal(json, "highPrice"),
                JsonParseUtil.asBigDecimal(json, "lowPrice"),
                JsonParseUtil.asBigDecimal(json, "priceChange"),
                JsonParseUtil.asBigDecimal(json, "priceChangePercent"),
                JsonParseUtil.asBigDecimal(json, "volume")));
    }

    public PriceQuote toPriceQuote(String displaySymbol) {
        return PriceQuote.builder()
                .symbol(displaySymbol)
                .assetName(displaySymbol)
                .assetType(AssetType.CRYPTO)
                .price(lastPrice)
                .open24h(openPrice)
                .high24h(highPrice)
                .low24h(lowPrice)
                .change24h(priceChange)
                .changePct24h(priceChangePercent)
                .volume24h(volume)
                .currency("USDT")
                .exchange("Binance")
                .timestamp(LocalDateTime.now())
                .isUp(priceChangePercent.compareTo(BigDecimal.ZERO) >= 0)
                .build();
    }
}
