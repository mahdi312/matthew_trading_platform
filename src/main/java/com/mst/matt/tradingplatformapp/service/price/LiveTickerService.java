package com.mst.matt.tradingplatformapp.service.price;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

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
        // Binance multi-stream for all crypto
        binanceService.subscribeToMultiTicker(cryptoWatchlist);
        binanceService.addLiveListener(quote -> notifyListeners(quote));
        log.info("Live WebSocket streams started for {} crypto pairs",
                cryptoWatchlist.size());
    }

    /**
     * Polls stock and forex prices every 15 seconds.
     * Crypto is handled by WebSocket above (no polling needed).
     */
    @Scheduled(fixedRateString = "15000")
    public void pollStocksAndForex() {
        List<String> all = new ArrayList<>();
        all.addAll(stockWatchlist);
        all.addAll(forexWatchlist);

        Map<String, PriceQuote> quotes = priceRouter.getMultipleQuotes(all);
        quotes.values().forEach(this::notifyListeners);
    }

    private void notifyListeners(PriceQuote quote) {
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

    public List<String> getCryptoWatchlist() { return Collections.unmodifiableList(cryptoWatchlist); }
    public List<String> getStockWatchlist()  { return Collections.unmodifiableList(stockWatchlist); }
    public List<String> getForexWatchlist()  { return Collections.unmodifiableList(forexWatchlist); }
}
