package com.mst.matt.referencedataservice.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Null-safe JSON parsing helpers for reference-data providers.
 */
public final class JsonUtil {

    private JsonUtil() {}

    public static BigDecimal bd(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) return null;
        String s = o.get(key).getAsString().trim();
        if (s.isEmpty() || "None".equalsIgnoreCase(s) || "-".equals(s)
                || "N/A".equalsIgnoreCase(s) || "null".equalsIgnoreCase(s)) return null;
        try {
            return new BigDecimal(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static BigDecimal bd(JsonElement el) {
        if (el == null || el.isJsonNull()) return null;
        try {
            return new BigDecimal(el.getAsString().trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String str(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) return null;
        String s = o.get(key).getAsString().trim();
        return (s.isEmpty() || "None".equalsIgnoreCase(s) || "N/A".equalsIgnoreCase(s)) ? null : s;
    }

    public static Integer integer(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) return null;
        try {
            return o.get(key).getAsInt();
        } catch (Exception e) {
            return null;
        }
    }

    public static Long longVal(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) return null;
        try {
            return o.get(key).getAsLong();
        } catch (Exception e) {
            return null;
        }
    }

    public static Double dbl(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) return null;
        try {
            return o.get(key).getAsDouble();
        } catch (Exception e) {
            return null;
        }
    }

    public static LocalDate localDate(JsonObject o, String key) {
        String s = str(o, key);
        if (s == null) return null;
        try {
            return LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public static Instant epochInstant(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) return null;
        try {
            return Instant.ofEpochSecond(o.get(key).getAsLong());
        } catch (Exception e) {
            return null;
        }
    }

    /** Normalise AV sentiment label to POSITIVE / NEGATIVE / NEUTRAL. */
    public static String normaliseSentimentLabel(String avLabel) {
        if (avLabel == null) return null;
        String l = avLabel.toLowerCase();
        if (l.contains("bull") || l.contains("positive")) return "POSITIVE";
        if (l.contains("bear") || l.contains("negative")) return "NEGATIVE";
        return "NEUTRAL";
    }
}
