package com.mst.matt.tradingplatformapp.service.price;

import com.mst.matt.tradingplatformapp.model.OhlcvBar;
import com.mst.matt.tradingplatformapp.model.UserProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routes price requests through {@link PriceProviderRegistry} with profile-aware
 * provider preference and automatic fallback when a source fails.
 */
@Service
public class PriceRouter {

    private static final Logger log = LoggerFactory.getLogger(PriceRouter.class);
    private static volatile String lastProviderName = "—";

    private final PriceProviderRegistry registry;
    private final BinanceService binanceService;
    private volatile UserProfile activeProfile;

    public PriceRouter(PriceProviderRegistry registry, BinanceService binanceService) {
        this.registry = registry;
        this.binanceService = binanceService;
    }

    public void setActiveProfile(UserProfile profile) {
        this.activeProfile = profile;
    }

    public static String getLastProviderName() {
        return lastProviderName;
    }

    public Optional<PriceQuote> getQuote(String symbol) {
        return getQuote(symbol, activeProfile);
    }

    public Optional<PriceQuote> getQuote(String symbol, UserProfile profile) {
        String normalized = SymbolNormalizer.normalize(symbol);
        for (PriceService provider : resolveProviders(normalized, profile)) {
            try {
                Optional<PriceQuote> result = provider.getQuote(normalized);
                if (result.isPresent() && hasValidPrice(result.get())) {
                    lastProviderName = provider.getProviderName();
                    log.debug("Quote for {} from {}", normalized, lastProviderName);
                    return result;
                }
            } catch (Exception e) {
                log.warn("{} failed for {}: {}", provider.getProviderName(),
                        normalized, e.getMessage());
            }
        }
        log.error("All providers failed for symbol: {}", normalized);
        return Optional.empty();
    }

    public List<OhlcvBar> getOhlcv(String symbol, String timeframe, int limit) {
        return getOhlcv(symbol, timeframe, limit, activeProfile);
    }

    public List<OhlcvBar> getOhlcv(String symbol, String timeframe, int limit, UserProfile profile) {
        String normalized = SymbolNormalizer.normalize(symbol);
        for (PriceService provider : resolveProviders(normalized, profile)) {
            try {
                List<OhlcvBar> bars = provider.getOhlcv(normalized, timeframe, limit);
                if (!bars.isEmpty()) {
                    lastProviderName = provider.getProviderName();
                    log.debug("OHLCV for {} ({}) from {}", normalized, timeframe, lastProviderName);
                    return bars;
                }
            } catch (Exception e) {
                log.warn("{} OHLCV failed for {}: {}", provider.getProviderName(),
                        normalized, e.getMessage());
            }
        }
        return Collections.emptyList();
    }

    public Map<String, PriceQuote> getMultipleQuotes(List<String> symbols) {
        Map<String, PriceQuote> results = new ConcurrentHashMap<>();
        symbols.parallelStream().forEach(symbol ->
                getQuote(symbol).ifPresent(q -> results.put(symbol, q)));
        Map<String, PriceQuote> ordered = new LinkedHashMap<>();
        symbols.forEach(s -> {
            PriceQuote q = results.get(s);
            if (q != null) ordered.put(s, q);
        });
        return ordered;
    }

    private List<PriceService> resolveProviders(String symbol, UserProfile profile) {
        List<PriceService> chain = registry.chainFor(symbol, profile);
        return chain.isEmpty() ? registry.chainFor(symbol, null) : chain;
    }

    private static boolean hasValidPrice(PriceQuote q) {
        return q.getPrice() != null && q.getPrice().compareTo(BigDecimal.ZERO) > 0;
    }

    public void subscribeToLiveTicker(List<String> cryptoSymbols) {
        binanceService.subscribeToMultiTicker(cryptoSymbols);
    }

    public void addLiveListener(java.util.function.Consumer<PriceQuote> listener) {
        binanceService.addLiveListener(listener);
    }
}
