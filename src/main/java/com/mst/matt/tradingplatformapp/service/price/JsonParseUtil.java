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
                if (p.isString()) return new BigDecimal(p.getAsString());
            }
        } catch (Exception ignored) {}
        return BigDecimal.ZERO;
    }
}
