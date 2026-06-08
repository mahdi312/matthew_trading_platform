package com.mst.matt.tradingplatformapp.service.price;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * P3 (LOG-FIX): in-memory cache of the last successful {@link PriceQuote} per symbol.
 *
 * <p>When every provider in {@link PriceRouter}'s fallback chain fails (e.g. when the
 * machine is offline, behind a captive portal, or behind a transparent proxy that
 * resolves {@code api.binance.com} to {@code 10.10.34.34} — exactly what the supplied
 * logs showed), the router falls back to this cache rather than returning
 * {@code Optional.empty()}. The UI therefore keeps showing the last known price with
 * a {@code stale} flag instead of going blank.
 *
 * <p>Cache entries that are older than {@link #FRESH_THRESHOLD} are still served, but
 * logged at {@code WARN} so the user/operator can see the data is stale.
 */
@Service
public class PriceCacheService {

    private static final Logger log = LoggerFactory.getLogger(PriceCacheService.class);

    /** After this age the cache hit is logged as stale. */
    private static final Duration FRESH_THRESHOLD = Duration.ofMinutes(15);

    private final ConcurrentHashMap<String, Entry> cache = new ConcurrentHashMap<>();

    /** Stores {@code quote} keyed by the normalized symbol. */
    public void update(String symbol, PriceQuote quote) {
        if (symbol == null || quote == null) return;
        cache.put(SymbolNormalizer.normalize(symbol), new Entry(quote, Instant.now()));
    }

    /** Returns the last cached quote regardless of age (may be very stale). */
    public Optional<PriceQuote> getLastKnown(String symbol) {
        if (symbol == null) return Optional.empty();
        Entry e = cache.get(SymbolNormalizer.normalize(symbol));
        if (e == null) return Optional.empty();
        if (e.timestamp.isBefore(Instant.now().minus(FRESH_THRESHOLD))) {
            log.warn("Serving STALE cached price for {} (cached at {})", symbol, e.timestamp);
        }
        return Optional.of(e.quote);
    }

    /** Wipes the cache. Mostly useful for tests. */
    public void clear() {
        cache.clear();
    }

    private record Entry(PriceQuote quote, Instant timestamp) {}
}
