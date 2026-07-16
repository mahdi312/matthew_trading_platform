package com.mst.matt.tradingplatformapp.service.price;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.math.BigDecimal;

/** Safe JSON number/string parsing for heterogeneous provider payloads. */
public final class JsonParseUtil {

    private JsonParseUtil() {}

    public static BigDecimal asBigDecimal(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)) return BigDecimal.ZERO;
        return asBigDecimal(obj.get(key));
    }

    public static BigDecimal asBigDecimal(JsonElement el) {
        if (el == null || el.isJsonNull()) return BigDecimal.ZERO;
        try {
            if (el.isJsonPrimitive()) {
                var p = el.getAsJsonPrimitive();
                if (p.isNumber()) return p.getAsBigDecimal();
                if (p.isString()) return parseNumericString(p.getAsString());
            }
        } catch (Exception ignored) {}
        return BigDecimal.ZERO;
    }

    /** Alpha Vantage {@code "10. change percent": "0.3126%"} and similar. */
    public static BigDecimal asPercent(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)) return BigDecimal.ZERO;
        return parsePercent(obj.get(key));
    }

    public static BigDecimal parsePercent(JsonElement el) {
        if (el == null || el.isJsonNull()) return BigDecimal.ZERO;
        if (!el.isJsonPrimitive()) return BigDecimal.ZERO;
        String raw = el.getAsString().trim();
        if (raw.endsWith("%")) {
            raw = raw.substring(0, raw.length() - 1).trim();
        }
        return parseNumericString(raw);
    }

    static BigDecimal parseNumericString(String raw) {
        if (raw == null || raw.isBlank()) return BigDecimal.ZERO;
        String s = raw.trim().replace(",", "");
        if (s.endsWith("%")) {
            s = s.substring(0, s.length() - 1).trim();
        }
        try {
            return new BigDecimal(s);
        } catch (Exception ignored) {
            return BigDecimal.ZERO;
        }
    }
}
