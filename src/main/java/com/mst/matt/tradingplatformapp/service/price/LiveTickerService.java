package com.mst.matt.tradingplatformapp.service.price;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.mst.matt.tradingplatformapp.model.UserProfile;
import com.mst.matt.tradingplatformapp.service.AppSettingsService;
import com.mst.matt.tradingplatformapp.service.WatchlistDefaults;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Manages the live ticker bar.
 *
 * For crypto: uses Binance WebSocket (real-time, no polling).
 * For stocks/forex: polls every 15 seconds via @Scheduled
 *   (Yahoo Finance and Frankfurter don't have WebSocket APIs).
 */
@Service
public class LiveTickerService {

    private static final Logger log = LoggerFactory.getLogger(LiveTickerService.class);

    @Autowired private PriceRouter priceRouter;
    @Autowired private BinanceService binanceService;
    @Autowired private AppSettingsService appSettings;
    /** P3 (LOG-FIX): WS ticks feed the last-known-good cache so the chart never
     *  goes blank when REST providers are down. */
    @Autowired private PriceCacheService priceCache;

    // Default watchlist (user can customize in Settings — Phase 11)
    private final List<String> cryptoWatchlist = new ArrayList<>(List.of(
            "BTCUSDT", "ETHUSDT", "BNBUSDT", "SOLUSDT", "ADAUSDT",
            "XRPUSDT", "DOGEUSDT", "AVAXUSDT", "MATICUSDT", "LINKUSDT"
    ));

    private final List<String> stockWatchlist = new ArrayList<>(List.of(
            "AAPL", "MSFT", "NVDA", "TSLA", "GOOGL", "AMZN", "META"
    ));

    private final List<String> forexWatchlist = new ArrayList<>(List.of(
            "EURUSD", "GBPUSD", "USDJPY", "USDCHF", "AUDUSD"
    ));

    // UI callbacks registered by controllers
    private final List<Consumer<PriceQuote>> tickerListeners = new CopyOnWriteArrayList<>();

    /**
     * Start WebSocket streams for all crypto symbols on app startup.
     * Called by AppStartupService (Phase 3).
     */
    public void startLiveStreams() {
        if (!appSettings.isApiFetchEnabled()) {
            log.info("Offline mode — live WebSocket streams skipped");
            replayCachedQuotes();
            return;
        }
        binanceService.subscribeToMultiTicker(cryptoWatchlist);
        binanceService.addLiveListener(quote -> notifyListeners(quote));
        log.info("Live WebSocket streams started for {} crypto pairs",
                cryptoWatchlist.size());
    }

    private void replayCachedQuotes() {
        allSymbols().forEach(sym ->
                priceCache.getLastKnown(sym).ifPresent(this::notifyListeners));
    }

    /**
     * Polls stock and forex prices every 15 seconds.
     * Crypto is handled by WebSocket above (no polling needed).
     */
    @Scheduled(fixedRateString = "15000")
    public void pollStocksAndForex() {
        if (!appSettings.isApiFetchEnabled()) {
            replayCachedQuotes();
            return;
        }
        List<String> all = new ArrayList<>();
        all.addAll(stockWatchlist);
        all.addAll(forexWatchlist);

        Map<String, PriceQuote> quotes = priceRouter.getMultipleQuotes(all);
        quotes.values().forEach(this::notifyListeners);
    }

    private void notifyListeners(PriceQuote quote) {
        // P3 (LOG-FIX): cache every successful tick.
        if (quote != null && quote.getSymbol() != null) {
            priceCache.update(quote.getSymbol(), quote);
        }
        tickerListeners.forEach(l -> {
            try { l.accept(quote); }
            catch (Exception e) {
                log.warn("Ticker listener error: {}", e.getMessage());
            }
        });
    }

    public void addTickerListener(Consumer<PriceQuote> listener) {
        tickerListeners.add(listener);
    }

    public void removeTickerListener(Consumer<PriceQuote> listener) {
        tickerListeners.remove(listener);
    }

    public void addToWatchlist(String symbol) {
        String s = symbol.toUpperCase();
        if (s.endsWith("USDT") || s.endsWith("BTC") || s.endsWith("ETH")) {
            if (!cryptoWatchlist.contains(s)) {
                cryptoWatchlist.add(s);
                binanceService.subscribeToTicker(s);
            }
        } else if (s.length() == 6 && !s.contains(".")) {
            if (!forexWatchlist.contains(s)) forexWatchlist.add(s);
        } else {
            if (!stockWatchlist.contains(s)) stockWatchlist.add(s);
        }
    }

    /**
     * T-12: replace every watchlist from a single comma/space-separated string supplied by
     * Profile Settings. Symbols are classified by {@link #addToWatchlist(String)} so the
     * Binance WS subscription stays in sync automatically.
     */
    public synchronized void replaceWatchlistFromString(String csv) {
        if (csv == null) return;
        cryptoWatchlist.clear();
        stockWatchlist.clear();
        forexWatchlist.clear();
        for (String token : csv.split("[,;\\s]+")) {
            String s = token.trim();
            if (s.isEmpty()) continue;
            addToWatchlist(s);
        }
        log.info("Watchlist updated — crypto={}, stocks={}, forex={}",
                cryptoWatchlist.size(), stockWatchlist.size(), forexWatchlist.size());
        if (appSettings.isApiFetchEnabled()) {
            binanceService.subscribeToMultiTicker(cryptoWatchlist);
        }
    }

    /** Apply profile watchlist or asset-focus defaults. */
    public synchronized void applyProfileWatchlist(UserProfile profile) {
        if (profile == null) return;
        String csv = profile.getWatchlist();
        if (csv != null && !csv.isBlank()) {
            replaceWatchlistFromString(csv);
        } else {
            replaceWatchlistFromString(
                    WatchlistDefaults.csvForFocus(profile.getAssetFocus()));
        }
    }

    public List<String> allSymbols() {
        return Stream.of(cryptoWatchlist, stockWatchlist, forexWatchlist)
                .flatMap(Collection::stream)
                .distinct()
                .toList();
    }

    public List<String> getCryptoWatchlist() { return Collections.unmodifiableList(cryptoWatchlist); }
    public List<String> getStockWatchlist()  { return Collections.unmodifiableList(stockWatchlist); }
    public List<String> getForexWatchlist()  { return Collections.unmodifiableList(forexWatchlist); }
}
