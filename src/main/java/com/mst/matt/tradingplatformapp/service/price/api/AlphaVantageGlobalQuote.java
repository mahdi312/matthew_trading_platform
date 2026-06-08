package com.mst.matt.tradingplatformapp.service.price.api;

import com.google.gson.JsonObject;
import com.mst.matt.tradingplatformapp.model.Trade.AssetType;
import com.mst.matt.tradingplatformapp.service.price.JsonParseUtil;
import com.mst.matt.tradingplatformapp.service.price.PriceQuote;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Alpha Vantage {@code GLOBAL_QUOTE} — see report.html "Global Quote (Real-time)".
 */
public record AlphaVantageGlobalQuote(
        String symbol,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal price,
        BigDecimal volume,
        BigDecimal previousClose,
        BigDecimal change,
        BigDecimal changePercent) {

    public static Optional<AlphaVantageGlobalQuote> fromRoot(JsonObject root) {
        if (root == null || !root.has("Global Quote")) return Optional.empty();
        JsonObject q = root.getAsJsonObject("Global Quote");
        BigDecimal price = JsonParseUtil.asBigDecimal(q, "05. price");
        if (price.compareTo(BigDecimal.ZERO) == 0) return Optional.empty();
        return Optional.of(new AlphaVantageGlobalQuote(
                text(q, "01. symbol"),
                JsonParseUtil.asBigDecimal(q, "02. open"),
                JsonParseUtil.asBigDecimal(q, "03. high"),
                JsonParseUtil.asBigDecimal(q, "04. low"),
                price,
                JsonParseUtil.asBigDecimal(q, "06. volume"),
                JsonParseUtil.asBigDecimal(q, "08. previous close"),
                JsonParseUtil.asBigDecimal(q, "09. change"),
                JsonParseUtil.asPercent(q, "10. change percent")));
    }

    public PriceQuote toPriceQuote(String sym, AssetType assetType) {
        BigDecimal pct = changePercent;
        if (pct.compareTo(BigDecimal.ZERO) == 0
                && previousClose.compareTo(BigDecimal.ZERO) != 0) {
            pct = change.divide(previousClose, 6, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }
        return PriceQuote.builder()
                .symbol(sym)
                .assetName(sym)
                .assetType(assetType)
                .price(price)
                .open24h(open)
                .high24h(high)
                .low24h(low)
                .change24h(change)
                .changePct24h(pct)
                .volume24h(volume)
                .timestamp(LocalDateTime.now())
                .isUp(change.compareTo(BigDecimal.ZERO) >= 0)
                .build();
    }

    private static String text(JsonObject o, String key) {
        return o.has(key) ? o.get(key).getAsString() : "";
    }
}
