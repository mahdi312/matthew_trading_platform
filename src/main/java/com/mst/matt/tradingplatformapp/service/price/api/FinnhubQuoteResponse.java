package com.mst.matt.tradingplatformapp.service.price.api;

import com.google.gson.JsonObject;
import com.mst.matt.tradingplatformapp.model.Trade.AssetType;
import com.mst.matt.tradingplatformapp.service.price.JsonParseUtil;
import com.mst.matt.tradingplatformapp.service.price.PriceQuote;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;

/** Finnhub {@code /quote} — fields {@code c,h,l,o,pc,t} per API docs. */
public record FinnhubQuoteResponse(
        BigDecimal current,
        BigDecimal high,
        BigDecimal low,
        BigDecimal open,
        BigDecimal previousClose) {

    public static Optional<FinnhubQuoteResponse> fromJson(JsonObject json) {
        if (json == null) return Optional.empty();
        BigDecimal c = JsonParseUtil.asBigDecimal(json, "c");
        if (c.compareTo(BigDecimal.ZERO) == 0) return Optional.empty();
        return Optional.of(new FinnhubQuoteResponse(
                c,
                JsonParseUtil.asBigDecimal(json, "h"),
                JsonParseUtil.asBigDecimal(json, "l"),
                JsonParseUtil.asBigDecimal(json, "o"),
                JsonParseUtil.asBigDecimal(json, "pc")));
    }

    public PriceQuote toPriceQuote(String sym, AssetType assetType) {
        BigDecimal change = current.subtract(previousClose);
        BigDecimal pct = previousClose.compareTo(BigDecimal.ZERO) != 0
                ? change.divide(previousClose, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;
        return PriceQuote.builder()
                .symbol(sym)
                .assetName(sym)
                .assetType(assetType)
                .price(current)
                .open24h(open)
                .high24h(high)
                .low24h(low)
                .change24h(change)
                .changePct24h(pct)
                .timestamp(LocalDateTime.now())
                .isUp(change.compareTo(BigDecimal.ZERO) >= 0)
                .build();
    }
}
