package com.mst.matt.tradingplatformapp.service.price.api.alphavantage;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * POJO for Alpha Vantage {@code CURRENCY_EXCHANGE_RATE} response.
 *
 * <pre>
 * {
 *   "Realtime Currency Exchange Rate": {
 *     "1. From_Currency Code": "USD",
 *     "2. From_Currency Name": "United States Dollar",
 *     "3. To_Currency Code":   "EUR",
 *     "4. To_Currency Name":   "Euro",
 *     "5. Exchange Rate":      "0.9234",
 *     "6. Last Refreshed":     "2026-07-02 16:00:00",
 *     "7. Time Zone":          "UTC",
 *     "8. Bid Price":          "0.9232",
 *     "9. Ask Price":          "0.9236"
 *   }
 * }
 * </pre>
 */
public record AlphaVantageForexRate(
        String fromCurrencyCode,
        String fromCurrencyName,
        String toCurrencyCode,
        String toCurrencyName,
        BigDecimal exchangeRate,
        String lastRefreshed,
        String timeZone,
        BigDecimal bidPrice,
        BigDecimal askPrice
) {

    public static Optional<AlphaVantageForexRate> fromRoot(JsonObject root) {
        if (root == null || !root.has("Realtime Currency Exchange Rate")) return Optional.empty();
        JsonObject r = root.getAsJsonObject("Realtime Currency Exchange Rate");
        try {
            return Optional.of(new AlphaVantageForexRate(
                    text(r, "1. From_Currency Code"),
                    text(r, "2. From_Currency Name"),
                    text(r, "3. To_Currency Code"),
                    text(r, "4. To_Currency Name"),
                    parseBD(r, "5. Exchange Rate"),
                    text(r, "6. Last Refreshed"),
                    text(r, "7. Time Zone"),
                    parseBD(r, "8. Bid Price"),
                    parseBD(r, "9. Ask Price")
            ));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static String text(JsonObject o, String key) {
        return o.has(key) ? o.get(key).getAsString() : "";
    }

    private static BigDecimal parseBD(JsonObject o, String key) {
        if (!o.has(key)) return BigDecimal.ZERO;
        try { return new BigDecimal(o.get(key).getAsString()); }
        catch (Exception e) { return BigDecimal.ZERO; }
    }
}
