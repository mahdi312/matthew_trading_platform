package com.mst.matt.tradingplatformapp.service.price;

import com.mst.matt.tradingplatformapp.model.UserProfile.ProfileAssetFocus;

import java.util.Set;

/**
 * Classifies symbols for provider routing.
 *
 * <p>T-15 expansion: in addition to {@code CRYPTO / FOREX / STOCK}, the detector now also
 * recognises {@code COMMODITY} (e.g. gold, oil) and {@code INDEX} (e.g. S&amp;P 500) symbols
 * via well-known Yahoo Finance prefixes/suffixes.
 */
public final class AssetClassDetector {

    public enum AssetClass { CRYPTO, FOREX, STOCK, COMMODITY, INDEX }

    /** Common bare-ticker commodity aliases users tend to type. */
    private static final Set<String> COMMODITY_ALIASES = Set.of(
            "GOLD", "XAU", "SILVER", "XAG", "PLATINUM", "XPT", "PALLADIUM", "XPD",
            "OIL", "WTI", "BRENT", "NATGAS", "GAS", "COPPER",
            "CORN", "WHEAT", "SOYBEAN", "SUGAR", "COFFEE", "COCOA");

    /** Common bare-ticker index aliases. */
    private static final Set<String> INDEX_ALIASES = Set.of(
            "SPX", "SP500", "NDX", "NASDAQ", "DJI", "DOW", "RUT", "RUSSELL",
            "VIX", "FTSE", "DAX", "CAC", "NIKKEI", "HSI", "STOXX");

    private AssetClassDetector() {}

    public static AssetClass detect(String symbol) {
        String s = SymbolNormalizer.normalize(symbol);
        if (isCrypto(s)) return AssetClass.CRYPTO;
        if (isIndex(s)) return AssetClass.INDEX;
        if (isCommodity(s)) return AssetClass.COMMODITY;
        if (isForex(s)) return AssetClass.FOREX;
        return AssetClass.STOCK;
    }

    public static AssetClass fromProfileFocus(ProfileAssetFocus focus, String symbol) {
        if (focus == null || focus == ProfileAssetFocus.MULTI)
            return detect(symbol);
        return switch (focus) {
            case CRYPTO -> AssetClass.CRYPTO;
            case FOREX  -> AssetClass.FOREX;
            case STOCK  -> AssetClass.STOCK;
            default     -> detect(symbol);
        };
    }

    public static boolean isCrypto(String s) {
        return s.endsWith("USDT") || s.endsWith("BUSD") || s.endsWith("USDC")
                || s.endsWith("BTC") || s.endsWith("ETH") || s.endsWith("BNB");
    }

    public static boolean isForex(String s) {
        return s.length() == 6 && s.chars().allMatch(Character::isLetter);
    }

    /**
     * Yahoo Finance index tickers carry a leading {@code ^} (e.g. {@code ^GSPC}, {@code ^IXIC});
     * users may also type popular aliases ({@code SPX}, {@code NASDAQ}…).
     */
    public static boolean isIndex(String s) {
        if (s.startsWith("^")) return true;
        return INDEX_ALIASES.contains(s);
    }

    /**
     * Yahoo Finance futures tickers carry a trailing {@code =F} (e.g. {@code GC=F},
     * {@code CL=F}); user-friendly aliases like {@code GOLD}, {@code OIL}, etc. are also
     * accepted and rerouted through Yahoo by {@link YahooFinanceService}.
     */
    public static boolean isCommodity(String s) {
        if (s.endsWith("=F")) return true;
        return COMMODITY_ALIASES.contains(s);
    }
}
