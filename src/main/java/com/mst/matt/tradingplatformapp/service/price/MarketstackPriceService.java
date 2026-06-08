package com.mst.matt.tradingplatformapp.service.price;

import com.mst.matt.tradingplatformapp.config.MarketApiProperties;
import com.mst.matt.tradingplatformapp.model.OhlcvBar;
import com.mst.matt.tradingplatformapp.model.Trade.AssetType;
import com.mst.matt.tradingplatformapp.service.price.api.MarketstackEodResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class MarketstackPriceService implements PriceService {

    /** Package-private and non-final so {@link MarketstackPriceServiceTest} can redirect via ReflectionTestUtils. */
    String baseUrl = "http://api.marketstack.com/v1";

    private final HttpJsonClient http;
    private final MarketApiProperties keys;

    public MarketstackPriceService(HttpJsonClient http, MarketApiProperties keys) {
        this.http = http;
        this.keys = keys;
    }

    @Override
    public boolean isEnabled() { return keys.hasMarketstackKey(); }

    @Override
    public Optional<PriceQuote> getQuote(String symbol) {
        List<OhlcvBar> bars = getOhlcv(symbol, "1d", 2);
        if (bars.isEmpty()) return Optional.empty();
        OhlcvBar last = bars.get(bars.size() - 1);
        OhlcvBar prev = bars.size() > 1 ? bars.get(bars.size() - 2) : last;
        BigDecimal change = last.getClose().subtract(prev.getClose());
        return Optional.of(PriceQuote.builder()
                .symbol(last.getSymbol())
                .assetName(last.getSymbol())
                .assetType(AssetType.STOCK)
                .price(last.getClose())
                .open24h(last.getOpen())
                .high24h(last.getHigh())
                .low24h(last.getLow())
                .change24h(change)
                .volume24h(last.getVolume())
                .timestamp(LocalDateTime.now())
                .isUp(change.compareTo(BigDecimal.ZERO) >= 0)
                .build());
    }

    @Override
    public List<OhlcvBar> getOhlcv(String symbol, String timeframe, int limit) {
        String sym = SymbolNormalizer.normalize(symbol);
        String url = baseUrl + "/eod?access_key=" + keys.getMarketstackKey()
                + "&symbols=" + sym + "&limit=" + Math.min(limit, 1000);
        return http.getJson(url)
                .flatMap(root -> MarketstackEodResponse.fromRoot(root, sym, timeframe, limit))
                .map(MarketstackEodResponse::bars)
                .orElse(List.of());
    }

    @Override
    public boolean supports(String symbol) {
        String s = SymbolNormalizer.normalize(symbol);
        return isEnabled() && !AssetClassDetector.isCrypto(s) && !AssetClassDetector.isForex(s);
    }

    @Override
    public String getProviderName() { return "Marketstack"; }

    @Override
    public MarketDataProvider getProviderId() { return MarketDataProvider.MARKETSTACK; }
}
