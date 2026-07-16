package com.mst.matt.marketservice.service;

import java.util.Map;

/**
 * Normalizes user-entered symbols before routing to providers.
 * Ported from desktop monolith's {@code service.price.SymbolNormalizer}.
 */
public final class SymbolNormalizer {

    private SymbolNormalizer() {}

    public static String normalize(String symbol) {
        if (symbol == null) return "";
        return symbol.trim().toUpperCase().replace("/","").replace("-","").replace("_","").replace("=X","");
    }

    public static String forBinance(String symbol) {
        String s = normalize(symbol);
        if (s.isEmpty()) return s;
        if (s.endsWith("USDT") || s.endsWith("BUSD")) return s;
        if (s.length() > 3 && (s.endsWith("BTC") || s.endsWith("ETH") || s.endsWith("BNB"))) return s;
        if (s.length() <= 5 && s.chars().allMatch(Character::isLetter)) return s + "USDT";
        return s;
    }

    public static String forYahoo(String symbol) {
        String s = normalize(symbol);
        String mapped = YAHOO_ALIASES.get(s);
        if (mapped != null) return mapped;
        if (s.length() == 6 && s.chars().allMatch(Character::isLetter)) return s + "=X";
        return s;
    }

    public static String forCoinGecko(String symbol) { return normalize(symbol); }
    public static String forForex(String symbol)     { return normalize(symbol); }

    private static final Map<String, String> YAHOO_ALIASES = Map.ofEntries(
            Map.entry("GOLD","GC=F"), Map.entry("XAU","GC=F"),
            Map.entry("SILVER","SI=F"), Map.entry("XAG","SI=F"),
            Map.entry("OIL","CL=F"), Map.entry("WTI","CL=F"),
            Map.entry("SPX","^GSPC"), Map.entry("SP500","^GSPC"),
            Map.entry("NDX","^NDX"), Map.entry("NASDAQ","^IXIC"),
            Map.entry("DJI","^DJI"), Map.entry("VIX","^VIX"),
            Map.entry("FTSE","^FTSE"), Map.entry("DAX","^GDAXI"),
            Map.entry("NIKKEI","^N225"), Map.entry("HSI","^HSI"));
}
