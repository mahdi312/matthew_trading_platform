package com.mst.matt.tradingplatformapp.service;

import com.mst.matt.tradingplatformapp.model.UserProfile.ProfileAssetFocus;

import java.util.ArrayList;
import java.util.List;

/** Default watchlist symbols seeded per profile asset focus. */
public final class WatchlistDefaults {

    private static final List<String> CRYPTO = List.of(
            "BTCUSDT", "ETHUSDT", "BNBUSDT", "SOLUSDT", "ADAUSDT",
            "XRPUSDT", "DOGEUSDT", "AVAXUSDT", "LINKUSDT");

    private static final List<String> STOCK = List.of(
            "AAPL", "MSFT", "NVDA", "TSLA", "GOOGL", "AMZN", "META");

    private static final List<String> FOREX = List.of(
            "EURUSD", "GBPUSD", "USDJPY", "USDCHF", "AUDUSD");

    private WatchlistDefaults() {}

    public static List<String> forFocus(ProfileAssetFocus focus) {
        if (focus == null) focus = ProfileAssetFocus.MULTI;
        return switch (focus) {
            case CRYPTO -> new ArrayList<>(CRYPTO);
            case STOCK  -> new ArrayList<>(STOCK);
            case FOREX  -> new ArrayList<>(FOREX);
            case MULTI  -> {
                List<String> all = new ArrayList<>(CRYPTO);
                all.addAll(STOCK);
                all.addAll(FOREX);
                yield all;
            }
        };
    }

    public static String csvForFocus(ProfileAssetFocus focus) {
        return String.join(", ", forFocus(focus));
    }
}
