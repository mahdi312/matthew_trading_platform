package com.mst.matt.tradingplatformapp.service.price;

/**
 * Normalizes user-entered symbols before routing to providers.
 * e.g. "BTC" → "BTCUSDT" for Binance, "EUR/USD" → "EURUSD" for Frankfurter.
 */
public final class SymbolNormalizer {

    private SymbolNormalizer() {}

    public static String normalize(String symbol) {
        if (symbol == null) return "";
        return symbol.trim().toUpperCase()
                .replace("/", "")
                .replace("-", "")
                .replace("_", "")
                .replace("=X", "");
    }

    /** Binance spot pairs — append USDT when user types base only (BTC, ETH). */
    public static String forBinance(String symbol) {
        String s = normalize(symbol);
        if (s.isEmpty()) return s;
        if (s.endsWith("USDT") || s.endsWith("BUSD")) return s;
        // Cross pairs (ETHBTC, BNBBTC) — quote suffix only when symbol is longer than the quote alone
        if (s.length() > 3 && (s.endsWith("BTC") || s.endsWith("ETH") || s.endsWith("BNB"))) {
            return s;
        }
        if (s.length() <= 5 && s.chars().allMatch(Character::isLetter)) {
            return s + "USDT";
        }
        return s;
    }

    public static String forCoinGecko(String symbol) {
        return normalize(symbol);
    }

    public static String forYahoo(String symbol) {
        String s = normalize(symbol);
        // T-15: map common commodity / index aliases to Yahoo Finance tickers.
        String mapped = YAHOO_ALIASES.get(s);
        if (mapped != null) return mapped;
        // Forex tickers use =X suffix
        if (s.length() == 6 && s.chars().allMatch(Character::isLetter))
            return s + "=X";
        return s;
    }

    /** T-15: user-friendly aliases mapped to canonical Yahoo Finance tickers. */
    private static final java.util.Map<String, String> YAHOO_ALIASES =
            java.util.Map.ofEntries(
                    // Precious metals (futures contracts)
                    java.util.Map.entry("GOLD", "GC=F"),
                    java.util.Map.entry("XAU", "GC=F"),
                    java.util.Map.entry("SILVER", "SI=F"),
                    java.util.Map.entry("XAG", "SI=F"),
                    java.util.Map.entry("PLATINUM", "PL=F"),
                    java.util.Map.entry("XPT", "PL=F"),
                    java.util.Map.entry("PALLADIUM", "PA=F"),
                    java.util.Map.entry("XPD", "PA=F"),
                    // Energy
                    java.util.Map.entry("OIL", "CL=F"),
                    java.util.Map.entry("WTI", "CL=F"),
                    java.util.Map.entry("BRENT", "BZ=F"),
                    java.util.Map.entry("NATGAS", "NG=F"),
                    java.util.Map.entry("GAS", "NG=F"),
                    // Base metal
                    java.util.Map.entry("COPPER", "HG=F"),
                    // Indices
                    java.util.Map.entry("SPX", "^GSPC"),
                    java.util.Map.entry("SP500", "^GSPC"),
                    java.util.Map.entry("NDX", "^NDX"),
                    java.util.Map.entry("NASDAQ", "^IXIC"),
                    java.util.Map.entry("DJI", "^DJI"),
                    java.util.Map.entry("DOW", "^DJI"),
                    java.util.Map.entry("RUT", "^RUT"),
                    java.util.Map.entry("RUSSELL", "^RUT"),
                    java.util.Map.entry("VIX", "^VIX"),
                    java.util.Map.entry("FTSE", "^FTSE"),
                    java.util.Map.entry("DAX", "^GDAXI"),
                    java.util.Map.entry("CAC", "^FCHI"),
                    java.util.Map.entry("NIKKEI", "^N225"),
                    java.util.Map.entry("HSI", "^HSI"),
                    java.util.Map.entry("STOXX", "^STOXX50E"));

    public static String forForex(String symbol) {
        return normalize(symbol);
    }
}
