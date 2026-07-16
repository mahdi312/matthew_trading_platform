package com.mst.matt.marketservice.service;

import java.util.Set;

/**
 * Classifies symbols for provider routing.
 * Ported from desktop monolith's {@code service.price.AssetClassDetector}.
 * Adapted: removed ProfileAssetFocus dependency (UI concern, not needed in market-service).
 */
public final class AssetClassDetector {

    public enum AssetClass { CRYPTO, FOREX, STOCK, COMMODITY, INDEX }

    private static final Set<String> COMMODITY_ALIASES = Set.of(
            "GOLD","XAU","SILVER","XAG","PLATINUM","XPT","PALLADIUM","XPD",
            "OIL","WTI","BRENT","NATGAS","GAS","COPPER","CORN","WHEAT");

    private static final Set<String> INDEX_ALIASES = Set.of(
            "SPX","SP500","NDX","NASDAQ","DJI","DOW","RUT","RUSSELL",
            "VIX","FTSE","DAX","CAC","NIKKEI","HSI","STOXX");

    private AssetClassDetector() {}

    public static AssetClass detect(String symbol) {
        String s = SymbolNormalizer.normalize(symbol);
        if (isCrypto(s))     return AssetClass.CRYPTO;
        if (isIndex(s))      return AssetClass.INDEX;
        if (isCommodity(s))  return AssetClass.COMMODITY;
        if (isForex(s))      return AssetClass.FOREX;
        return AssetClass.STOCK;
    }

    public static boolean isCrypto(String s) {
        return s.endsWith("USDT")||s.endsWith("BUSD")||s.endsWith("USDC")
               ||s.endsWith("BTC")||s.endsWith("ETH")||s.endsWith("BNB");
    }
    public static boolean isForex(String s)     { return s.length()==6 && s.chars().allMatch(Character::isLetter); }
    public static boolean isIndex(String s)      { return s.startsWith("^") || INDEX_ALIASES.contains(s); }
    public static boolean isCommodity(String s)  { return s.endsWith("=F") || COMMODITY_ALIASES.contains(s); }
}
