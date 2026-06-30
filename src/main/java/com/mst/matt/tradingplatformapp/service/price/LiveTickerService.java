package com.mst.matt.tradingplatformapp.service.price;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.mst.matt.tradingplatformapp.model.UserProfile;
import com.mst.matt.tradingplatformapp.service.AppSettingsService;
import com.mst.matt.tradingplatformapp.service.WatchlistDefaults;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Manages the live ticker bar.
 *
 * For crypto: uses Binance WebSocket (real-time, no polling).
 * For stocks/forex: polls at a user-configured interval (default 15 s, min 5 s).
 *   (Yahoo Finance and Frankfurter don't have WebSocket APIs.)
 *
 * The poll interval is fully runtime-configurable — changing it in Settings
 * takes effect immediately on the next reschedule cycle without a restart.
 */
@Service
public class LiveTickerService {

    private static final Logger log = LoggerFactory.getLogger(LiveTickerService.class);

    @Autowired private PriceRouter priceRouter;
    @Autowired private BinanceService binanceService;
    @Autowired private AppSettingsService appSettings;
    @Autowired private com.mst.matt.tradingplatformapp.service.auth.AuthService authService;
    /** WS ticks feed the last-known-good cache so the chart never
     *  goes blank when REST providers are down. */
    @Autowired private PriceCacheService priceCache;

    /**
     * Set to {@code true} once a user has successfully logged in and
     * {@link #startLiveStreams()} has been called.  All polling/WS activity
     * is gated on this flag so we never make external API calls before login.
     */
    private volatile boolean streamsStarted = false;

    // ── Watchlists (user-managed, persisted via Settings) ─────────────────
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

    // ── UI callbacks registered by controllers ─────────────────────────────
    private final List<Consumer<PriceQuote>> tickerListeners = new CopyOnWriteArrayList<>();

    // ── Dynamic poll scheduler ─────────────────────────────────────────────
    /** Single-thread scheduler for the stock/forex polling task. */
    private final ScheduledExecutorService pollScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ticker-poll-scheduler");
                t.setDaemon(true);
                return t;
            });

    /** The currently-scheduled future; replaced whenever the interval changes. */
    private volatile ScheduledFuture<?> pollFuture;

    /** The interval (seconds) that the current {@code pollFuture} was scheduled with. */
    private volatile int scheduledIntervalSeconds = -1;

    // ── Lifecycle ──────────────────────────────────────────────────────────

    @PostConstruct
    void initScheduler() {
        // Schedule at the persisted interval right away; startLiveStreams() will be
        // called by AppStartupService after the application is fully wired.
        reschedulePoller(appSettings.getTickerPollIntervalSeconds());
    }

    @PreDestroy
    void shutdown() {
        pollScheduler.shutdownNow();
    }

    /**
     * Reschedule the stock/forex polling task at a new interval.
     * Called once on startup, and again whenever the user changes the setting.
     *
     * @param seconds new poll interval; clamped to [5, 3600].
     */
    public synchronized void reschedulePoller(int seconds) {
        int interval = Math.max(5, Math.min(3600, seconds));
        if (interval == scheduledIntervalSeconds && pollFuture != null && !pollFuture.isDone()) {
            return; // already running at the correct rate
        }
        if (pollFuture != null) {
            pollFuture.cancel(false);
        }
        pollFuture = pollScheduler.scheduleAtFixedRate(
                this::pollStocksAndForex, 0, interval, TimeUnit.SECONDS);
        scheduledIntervalSeconds = interval;
        log.info("Stock/forex ticker poll interval set to {} s", interval);
    }

    /**
     * Start WebSocket streams for enabled crypto symbols.
     *
     * <p>Must be called <b>after</b> the user has successfully logged in
     * (triggered by {@code LoginController.onLoginSuccess()}).
     * Calling this before login is a no-op (guarded by {@link #streamsStarted}).
     */
    public synchronized void startLiveStreams() {
        if (!authService.isLoggedIn()) {
            log.warn("startLiveStreams() called without an authenticated user — ignored.");
            return;
        }
        if (streamsStarted) {
            log.debug("Live streams already running — skipping duplicate start.");
            return;
        }
        if (!appSettings.isApiFetchEnabled()) {
            log.info("Offline mode — live WebSocket streams skipped");
            replayCachedQuotes();
            streamsStarted = true;
            return;
        }
        List<String> enabledCrypto = cryptoWatchlist.stream()
                .filter(s -> appSettings.isTickerSymbolEnabled(s))
                .toList();
        binanceService.subscribeToMultiTicker(enabledCrypto);
        binanceService.addLiveListener(quote -> {
            // Only forward ticks for explicitly-enabled symbols
            if (quote != null && quote.getSymbol() != null
                    && appSettings.isTickerSymbolEnabled(quote.getSymbol())) {
                notifyListeners(quote);
            }
        });
        streamsStarted = true;
        log.info("Live WebSocket streams started for {} crypto pairs ({} enabled)",
                cryptoWatchlist.size(), enabledCrypto.size());
    }

    /**
     * Stop all live streams and reset the started flag.
     * Called by {@code AuthService.logout()} so streams stop when the user logs out.
     */
    public synchronized void stopLiveStreams() {
        if (!streamsStarted) return;
        binanceService.unsubscribeAll();
        if (pollFuture != null) {
            pollFuture.cancel(false);
            pollFuture = null;
            scheduledIntervalSeconds = -1;
        }
        streamsStarted = false;
        log.info("Live streams stopped (user logged out).");
    }

    private void replayCachedQuotes() {
        allSymbols().forEach(sym ->
                priceCache.getLastKnown(sym).ifPresent(this::notifyListeners));
    }

    /**
     * Polls stock and forex prices.  The interval is controlled by
     * {@link AppSettingsService#getTickerPollIntervalSeconds()} and applied
     * dynamically via {@link #reschedulePoller(int)}.
     * Crypto is handled by the Binance WebSocket above (no polling needed).
     */
    public void pollStocksAndForex() {
        // Guard: never poll external APIs before a user has logged in
        if (!streamsStarted || !authService.isLoggedIn()) {
            return;
        }
        if (!appSettings.isApiFetchEnabled()) {
            replayCachedQuotes();
            return;
        }
        // Re-check the configured interval and reschedule if the user changed it
        int configuredInterval = appSettings.getTickerPollIntervalSeconds();
        if (configuredInterval != scheduledIntervalSeconds) {
            reschedulePoller(configuredInterval);
            return; // the new schedule will fire shortly
        }

        List<String> all = new ArrayList<>();
        stockWatchlist.stream()
                .filter(s -> appSettings.isTickerSymbolEnabled(s))
                .forEach(all::add);
        forexWatchlist.stream()
                .filter(s -> appSettings.isTickerSymbolEnabled(s))
                .forEach(all::add);

        if (all.isEmpty()) return;
        Map<String, PriceQuote> quotes = priceRouter.getMultipleQuotes(all);
        quotes.values().forEach(this::notifyListeners);
    }

    /**
     * Called after the user saves a new poll interval in Settings.
     * Immediately reschedules the poller at the new rate.
     */
    public void applyPollIntervalSetting() {
        reschedulePoller(appSettings.getTickerPollIntervalSeconds());
    }

    // ── Listener management ────────────────────────────────────────────────

    private void notifyListeners(PriceQuote quote) {
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

    // ── Watchlist management ───────────────────────────────────────────────

    /**
     * Classify and add a single symbol to the appropriate watchlist.
     * If the symbol is crypto, the Binance WS is updated immediately.
     */
    public synchronized void addToWatchlist(String symbol) {
        String s = symbol.toUpperCase().trim();
        if (s.isEmpty()) return;
        if (isCrypto(s)) {
            if (!cryptoWatchlist.contains(s)) {
                cryptoWatchlist.add(s);
                if (appSettings.isApiFetchEnabled() && appSettings.isTickerSymbolEnabled(s)) {
                    // Re-subscribe with updated list
                    List<String> enabled = cryptoWatchlist.stream()
                            .filter(sym -> appSettings.isTickerSymbolEnabled(sym))
                            .toList();
                    binanceService.subscribeToMultiTicker(enabled);
                }
            }
        } else if (isForex(s)) {
            if (!forexWatchlist.contains(s)) forexWatchlist.add(s);
        } else {
            if (!stockWatchlist.contains(s)) stockWatchlist.add(s);
        }
        log.info("Added {} to watchlist", s);
    }

    /**
     * Remove a symbol from whichever watchlist contains it.
     * Crypto removal also closes the corresponding Binance WS stream.
     */
    public synchronized void removeFromWatchlist(String symbol) {
        String s = symbol.toUpperCase().trim();
        boolean removed = cryptoWatchlist.remove(s)
                || stockWatchlist.remove(s)
                || forexWatchlist.remove(s);
        if (removed) {
            // Also clear the enabled/disabled state so it's clean if the user re-adds it
            appSettings.setTickerSymbolEnabled(s, true); // reset to default
            // Re-subscribe Binance WS without the removed symbol
            if (appSettings.isApiFetchEnabled()) {
                List<String> enabledCrypto = cryptoWatchlist.stream()
                        .filter(sym -> appSettings.isTickerSymbolEnabled(sym))
                        .toList();
                binanceService.subscribeToMultiTicker(enabledCrypto);
            }
            log.info("Removed {} from watchlist", s);
        }
    }

    /**
     * Replace every watchlist from a single comma/space-separated string supplied by
     * Profile Settings. Symbols are classified so the Binance WS stays in sync.
     */
    public synchronized void replaceWatchlistFromString(String csv) {
        if (csv == null) return;
        cryptoWatchlist.clear();
        stockWatchlist.clear();
        forexWatchlist.clear();
        for (String token : csv.split("[,;\\s]+")) {
            String s = token.trim();
            if (s.isEmpty()) continue;
            // Add directly without triggering re-subscribe on every symbol
            if (isCrypto(s)) {
                if (!cryptoWatchlist.contains(s)) cryptoWatchlist.add(s);
            } else if (isForex(s)) {
                if (!forexWatchlist.contains(s)) forexWatchlist.add(s);
            } else {
                if (!stockWatchlist.contains(s)) stockWatchlist.add(s);
            }
        }
        log.info("Watchlist updated — crypto={}, stocks={}, forex={}",
                cryptoWatchlist.size(), stockWatchlist.size(), forexWatchlist.size());
        // Single re-subscribe with the complete new list
        if (appSettings.isApiFetchEnabled()) {
            List<String> enabledCrypto = cryptoWatchlist.stream()
                    .filter(sym -> appSettings.isTickerSymbolEnabled(sym))
                    .toList();
            binanceService.subscribeToMultiTicker(enabledCrypto);
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

    /**
     * Called after the user changes ticker symbol enabled/disabled status in Settings.
     * Re-subscribes Binance WS to only the currently-enabled crypto symbols,
     * ensuring no unwanted symbols are streamed.
     */
    public void applyTickerSymbolSettings() {
        if (!appSettings.isApiFetchEnabled()) return;
        List<String> enabledCrypto = cryptoWatchlist.stream()
                .filter(s -> appSettings.isTickerSymbolEnabled(s))
                .toList();
        binanceService.subscribeToMultiTicker(enabledCrypto);
        log.info("Ticker symbol settings applied — {} of {} crypto pairs enabled",
                enabledCrypto.size(), cryptoWatchlist.size());
    }

    // ── Accessors ──────────────────────────────────────────────────────────

    public List<String> allSymbols() {
        return Stream.of(cryptoWatchlist, stockWatchlist, forexWatchlist)
                .flatMap(Collection::stream)
                .distinct()
                .toList();
    }

    public List<String> getCryptoWatchlist() { return Collections.unmodifiableList(cryptoWatchlist); }
    public List<String> getStockWatchlist()  { return Collections.unmodifiableList(stockWatchlist); }
    public List<String> getForexWatchlist()  { return Collections.unmodifiableList(forexWatchlist); }

    // ── Symbol classification helpers ──────────────────────────────────────

    private static boolean isCrypto(String s) {
        return s.endsWith("USDT") || s.endsWith("BUSD")
                || s.endsWith("BTC") || s.endsWith("ETH") || s.endsWith("BNB");
    }

    private static boolean isForex(String s) {
        return s.length() == 6 && !s.contains(".") && s.chars().allMatch(Character::isLetter);
    }
}
