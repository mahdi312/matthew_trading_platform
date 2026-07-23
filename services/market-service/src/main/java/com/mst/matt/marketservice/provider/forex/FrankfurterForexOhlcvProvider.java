package com.mst.matt.marketservice.provider.forex;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.dto.NormalizedOhlcvBar;
import com.mst.matt.marketservice.client.HttpJsonClient;
import com.mst.matt.marketservice.client.JsonParseUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Frankfurter.app — free, no API key, ECB-sourced forex rates.
 *
 * <p>Supports historical range via {@code /YYYY-MM-DD..YYYY-MM-DD?from=X&to=Y}.
 * Each day's rate is used as open/high/low/close (spot-rate approximation).
 */
@Component
public class FrankfurterForexOhlcvProvider extends AbstractForexOhlcvProvider {

    public static final String PROVIDER_NAME = "FRANKFURTER";

    @Value("${api.frankfurter.base-url:https://api.frankfurter.app}")
    private String baseUrl;

    public FrankfurterForexOhlcvProvider(HttpJsonClient http) {
        super(http);
    }

    @Override public String providerName() { return PROVIDER_NAME; }
    @Override protected boolean hasCredentials() { return true; }   // no key needed
    @Override protected String latestRatesUrl(String from, String to) {
        return baseUrl + "/latest?from=" + from + "&to=" + to;
    }
    @Override protected String ratesNode() { return "rates"; }

    /**
     * Frankfurter supports date-range queries — overrides empty base implementation.
     * Returns daily rates as synthetic OHLCV bars (O=H=L=C = spot rate).
     */
    @Override
    public List<NormalizedOhlcvBar> getHistoricalBars(String symbol,
                                                       AssetClass assetClass,
                                                       String interval,
                                                       int limit) {
        String[] pair = parsePair(symbol);
        if (pair == null) return List.of();
        LocalDate endDate   = LocalDate.now();
        LocalDate startDate = endDate.minusDays(limit + 14); // buffer for weekends
        return fetchRange(pair[0], pair[1], startDate, endDate, symbol, interval, limit);
    }

    @Override
    public List<NormalizedOhlcvBar> getHistoricalBars(String symbol,
                                                       AssetClass assetClass,
                                                       String interval,
                                                       Instant from,
                                                       Instant to) {
        String[] pair = parsePair(symbol);
        if (pair == null) return List.of();
        LocalDate startDate = from.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate endDate   = to.atZone(ZoneOffset.UTC).toLocalDate();
        return fetchRange(pair[0], pair[1], startDate, endDate, symbol, interval, 0);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private List<NormalizedOhlcvBar> fetchRange(String from, String to,
                                                 LocalDate startDate, LocalDate endDate,
                                                 String symbol, String interval, int limit) {
        String url = String.format("%s/%s..%s?from=%s&to=%s",
                baseUrl, startDate, endDate, from, to);
        return http.getJson(url).map(root -> {
            JsonObject rates = root.has("rates") ? root.getAsJsonObject("rates") : null;
            if (rates == null) return List.<NormalizedOhlcvBar>of();
            List<NormalizedOhlcvBar> bars = new ArrayList<>();
            for (java.util.Map.Entry<String, JsonElement> dayEntry : rates.entrySet()) {
                try {
                    LocalDate date = LocalDate.parse(dayEntry.getKey());
                    JsonObject dayRates = dayEntry.getValue().getAsJsonObject();
                    if (!dayRates.has(to)) continue;
                    BigDecimal rate = JsonParseUtil.asBigDecimal(dayRates, to);
                    if (rate.signum() == 0) continue;
                    Instant t = date.atStartOfDay().toInstant(ZoneOffset.UTC);
                    bars.add(NormalizedOhlcvBar.builder()
                            .symbol(symbol).assetClass(AssetClass.FOREX).providerName(PROVIDER_NAME)
                            .interval(interval).openTime(t).closeTime(t)
                            .open(rate).high(rate).low(rate).close(rate)
                            .volume(BigDecimal.ZERO)
                            .isSynthetic(true)
                            .build());
                } catch (Exception ex) {
                    // skip malformed entries
                }
            }
            bars.sort(Comparator.comparing(NormalizedOhlcvBar::getOpenTime));
            if (limit > 0 && bars.size() > limit) {
                return bars.subList(bars.size() - limit, bars.size());
            }
            return bars;
        }).orElse(List.of());
    }

}
