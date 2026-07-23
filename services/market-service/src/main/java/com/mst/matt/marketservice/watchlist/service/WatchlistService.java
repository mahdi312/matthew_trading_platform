package com.mst.matt.marketservice.watchlist.service;

import com.mst.matt.marketservice.model.AssetType;
import com.mst.matt.marketservice.service.AssetClassDetector;
import com.mst.matt.marketservice.service.WatchlistDefaults;
import com.mst.matt.marketservice.watchlist.model.WatchlistItem;
import com.mst.matt.marketservice.watchlist.repository.WatchlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic for user watchlists.
 *
 * <h3>Auto-seed behaviour</h3>
 * On the first call to {@link #getWatchlist(Long)} for a new user
 * (count == 0) the method seeds the watchlist from
 * {@link WatchlistDefaults#CRYPTO_DEFAULTS}.  Callers that need to
 * control the asset class can call the overloaded {@link #getWatchlist(Long, String)}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WatchlistService {

    private final WatchlistRepository watchlistRepository;

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Return the current user's watchlist, seeding with crypto defaults on first access.
     */
    @Transactional
    public List<WatchlistItem> getWatchlist(Long userId) {
        seedIfEmpty(userId);
        return watchlistRepository.findByUserIdOrderByAddedAtDesc(userId);
    }

    /**
     * Overload that seeds with defaults from the appropriate asset class.
     * Falls back to crypto if assetClass is null or unrecognised.
     */
    @Transactional
    public List<WatchlistItem> getWatchlist(Long userId, String assetClass) {
        seedIfEmpty(userId, assetClass);
        return watchlistRepository.findByUserIdOrderByAddedAtDesc(userId);
    }

    /**
     * Add a symbol to the user's watchlist.
     * Idempotent — returns the existing entry if the symbol is already present.
     *
     * @return the saved (or existing) {@link WatchlistItem}
     */
    @Transactional
    public WatchlistItem addSymbol(Long userId, String symbol, String assetClass) {
        String upper = symbol.toUpperCase().trim();

        return watchlistRepository.findByUserIdAndSymbol(userId, upper)
                .orElseGet(() -> {
                    // Derive assetClass from symbol if not provided
                    String resolvedClass = resolveAssetClass(upper, assetClass);
                    WatchlistItem item = WatchlistItem.builder()
                            .userId(userId)
                            .symbol(upper)
                            .assetClass(resolvedClass)
                            .build();
                    WatchlistItem saved = watchlistRepository.save(item);
                    log.info("Watchlist: user={} added symbol={} assetClass={}", userId, upper, resolvedClass);
                    return saved;
                });
    }

    /**
     * Remove a symbol from the user's watchlist.
     * No-op if the symbol was not on the list.
     */
    @Transactional
    public void removeSymbol(Long userId, String symbol) {
        String upper = symbol.toUpperCase().trim();
        watchlistRepository.deleteByUserIdAndSymbol(userId, upper);
        log.info("Watchlist: user={} removed symbol={}", userId, upper);
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private void seedIfEmpty(Long userId) {
        seedIfEmpty(userId, null);
    }

    private void seedIfEmpty(Long userId, String assetClass) {
        if (watchlistRepository.countByUserId(userId) > 0) return;

        List<String> defaults = resolveDefaults(assetClass);
        String resolvedClass  = normaliseClass(assetClass);

        log.info("Watchlist: seeding {} defaults for new user={} assetClass={}", defaults.size(), userId, resolvedClass);

        List<WatchlistItem> seeds = defaults.stream()
                .map(sym -> WatchlistItem.builder()
                        .userId(userId)
                        .symbol(sym)
                        .assetClass(resolvedClass)
                        .build())
                .toList();

        watchlistRepository.saveAll(seeds);
    }

    private List<String> resolveDefaults(String assetClass) {
        if (assetClass == null) return WatchlistDefaults.CRYPTO_DEFAULTS;
        return switch (assetClass.toUpperCase()) {
            case "STOCK"  -> WatchlistDefaults.STOCK_DEFAULTS;
            case "FOREX"  -> WatchlistDefaults.FOREX_DEFAULTS;
            default       -> WatchlistDefaults.CRYPTO_DEFAULTS;
        };
    }

    private String normaliseClass(String assetClass) {
        if (assetClass == null) return "CRYPTO";
        return switch (assetClass.toUpperCase()) {
            case "STOCK" -> "STOCK";
            case "FOREX" -> "FOREX";
            default      -> "CRYPTO";
        };
    }

    private String resolveAssetClass(String symbol, String hint) {
        if (hint != null && !hint.isBlank()) return hint.toUpperCase();
        // Auto-detect from symbol pattern via AssetClassDetector
        AssetClassDetector.AssetClass detected = AssetClassDetector.detect(symbol);
        return switch (detected) {
            case CRYPTO -> "CRYPTO";
            case FOREX  -> "FOREX";
            default     -> "STOCK";
        };
    }
}
