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
        // Yahoo forex tickers use =X suffix
        if (s.length() == 6 && s.chars().allMatch(Character::isLetter))
            return s + "=X";
        return s;
    }

    public static String forForex(String symbol) {
        return normalize(symbol);
    }
}
