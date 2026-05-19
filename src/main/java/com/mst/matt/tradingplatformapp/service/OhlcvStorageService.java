package com.mst.matt.tradingplatformapp.service;

import com.mst.matt.tradingplatformapp.model.OhlcvBar;
import com.mst.matt.tradingplatformapp.model.UserProfile;
import com.mst.matt.tradingplatformapp.repository.OhlcvBarRepository;
import com.mst.matt.tradingplatformapp.service.price.PriceRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class OhlcvStorageService {

    private static final Logger log = LoggerFactory.getLogger(OhlcvStorageService.class);

    @Autowired private OhlcvBarRepository barRepository;
    @Autowired private PriceRouter        priceRouter;

    @Transactional
    public List<OhlcvBar> getBars(String symbol, String timeframe, int limit) {
        return getBars(symbol, timeframe, limit, null);
    }

    @Transactional
    public List<OhlcvBar> getBars(String symbol, String timeframe, int limit, UserProfile profile) {
        String sym = symbol.toUpperCase();

        List<OhlcvBar> cached = chronological(barRepository
                .findTopBySymbolAndTimeframe(sym, timeframe, PageRequest.of(0, limit)));

        if (cached.size() >= limit) {
            log.debug("OHLCV cache hit: {} {} ({} bars)", sym, timeframe, cached.size());
            return cached;
        }

        log.info("Fetching OHLCV from API: {} {} {} bars", sym, timeframe, limit);
        List<OhlcvBar> fresh = chronological(
                priceRouter.getOhlcv(sym, timeframe, limit, profile));

        if (!fresh.isEmpty()) {
            barRepository.deleteBySymbolAndTimeframe(sym, timeframe);
            barRepository.saveAll(fresh);
            return fresh;
        }

        return cached;
    }

    @Transactional
    public List<OhlcvBar> refreshBars(String symbol, String timeframe, int limit) {
        return refreshBars(symbol, timeframe, limit, null);
    }

    @Transactional
    public List<OhlcvBar> refreshBars(String symbol, String timeframe, int limit, UserProfile profile) {
        String sym = symbol.toUpperCase();
        barRepository.deleteBySymbolAndTimeframe(sym, timeframe);
        List<OhlcvBar> fresh = chronological(
                priceRouter.getOhlcv(sym, timeframe, limit, profile));
        if (!fresh.isEmpty()) {
            barRepository.saveAll(fresh);
        }
        return fresh;
    }

    static List<OhlcvBar> chronological(List<OhlcvBar> bars) {
        if (bars == null || bars.size() < 2) {
            return bars == null ? List.of() : new ArrayList<>(bars);
        }
        List<OhlcvBar> ordered = new ArrayList<>(bars);
        ordered.sort(java.util.Comparator.comparing(OhlcvBar::getOpenTime));
        return ordered;
    }
}
