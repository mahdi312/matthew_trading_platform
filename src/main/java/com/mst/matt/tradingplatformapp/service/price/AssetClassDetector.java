package com.mst.matt.tradingplatformapp.service.price;

import com.mst.matt.tradingplatformapp.model.UserProfile.ProfileAssetFocus;

/**
 * Classifies symbols for provider routing (crypto / forex / stock).
 */
public final class AssetClassDetector {

    public enum AssetClass { CRYPTO, FOREX, STOCK }

    private AssetClassDetector() {}

    public static AssetClass detect(String symbol) {
        String s = SymbolNormalizer.normalize(symbol);
        if (isCrypto(s)) return AssetClass.CRYPTO;
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
}
