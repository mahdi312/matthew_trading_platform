package com.mst.matt.marketservice.bitunix;

import com.google.gson.Gson;
import com.mst.matt.contracts.broker.market.MarketDataProvider;
import com.mst.matt.contracts.dto.OhlcvBarDto;
import com.mst.matt.contracts.dto.PriceTickDto;
import com.mst.matt.contracts.dto.TickerSnapshotDto;
import com.mst.matt.contracts.enums.BrokerType;
import com.mst.matt.marketservice.bitunix.dto.BitUnixKlineItem;
import com.mst.matt.marketservice.bitunix.dto.BitUnixKlineResponse;
import com.mst.matt.marketservice.bitunix.dto.BitUnixTickerItem;
import com.mst.matt.marketservice.bitunix.dto.BitUnixTickerResponse;
import com.mst.matt.marketservice.bitunix.support.BitUnixIntervalSupport;
import com.mst.matt.marketservice.exception.MarketDataException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * First concrete {@link MarketDataProvider} implementation — talks to
 * BitUnix's public futures market-data REST endpoints (Step 5.2). Live
 * WebSocket streaming ({@link #streamLivePrice}) and the Caffeine cache
 * layer in front of {@link #getOhlcv}/{@link #getTickerSnapshot} land in
 * Steps 5.4 and 5.3 respectively.
 *
 * <p>Per {@code MarketDataProvider}'s documented design rules, no other
 * class in this codebase may call BitUnix's REST/WebSocket APIs directly
 * — only this provider does, and every caller goes through the
 * {@link com.mst.matt.contracts.broker.registry.BrokerRegistry} (Step 5.5)
 * rather than injecting this class by concrete type.</p>
 */
@Slf4j
@Component
public class BitUnixMarketDataProvider implements MarketDataProvider {

    /** BitUnix's public Kline endpoint: {@code GET /api/v1/futures/market/kline}. */
    private static final String KLINE_PATH = "/api/v1/futures/market/kline";

    /** BitUnix's public Tickers endpoint: {@code GET /api/v1/futures/market/tickers}. */
    private static final String TICKERS_PATH = "/api/v1/futures/market/tickers";

    private final BitUnixProperties properties;
    private final OkHttpClient httpClient;
    private final Gson gson;

    public BitUnixMarketDataProvider(
            BitUnixProperties properties,
            @Qualifier("bitUnixRestHttpClient") OkHttpClient httpClient,
            Gson bitUnixGson) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.gson = bitUnixGson;
    }

    @Override
    public BrokerType brokerType() {
        return BrokerType.BITUNIX;
    }

    // ── Historical OHLCV (Step 5.2 A) ───────────────────────────────────────────

    @Override
    public List<OhlcvBarDto> getOhlcv(String symbol, String interval, int limit) {
        BitUnixIntervalSupport.validate(interval);

        HttpUrl url = HttpUrl.parse(properties.getRestBaseUrl() + KLINE_PATH)
                .newBuilder()
                .addQueryParameter("symbol", symbol)
                .addQueryParameter("interval", interval)
                .addQueryParameter("limit", String.valueOf(limit))
                .build();

        BitUnixKlineResponse response = execute(url, BitUnixKlineResponse.class);
        if (response.getCode() != 0) {
            throw new MarketDataException(
                    "BitUnix Kline error for " + symbol + "/" + interval
                    + " — code=" + response.getCode() + " msg=" + response.getMsg());
        }
        if (response.getData() == null) {
            return List.of();
        }

        Duration barDuration = BitUnixIntervalSupport.durationOf(interval);
        List<OhlcvBarDto> bars = new ArrayList<>(response.getData().size());
        for (BitUnixKlineItem item : response.getData()) {
            Instant openTime = Instant.ofEpochMilli(item.getTime());
            bars.add(OhlcvBarDto.builder()
                    .openTime(openTime)
                    .closeTime(openTime.plus(barDuration))
                    .open(toBigDecimal(item.getOpen()))
                    .high(toBigDecimal(item.getHigh()))
                    .low(toBigDecimal(item.getLow()))
                    .close(toBigDecimal(item.getClose()))
                    .volume(toBigDecimal(item.getBaseVol()))
                    .tradeCount(null) // BitUnix's Kline payload does not report a trade count
                    .build());
        }
        // BitUnix does not document result ordering explicitly; sort ascending
        // by openTime to satisfy MarketDataProvider#getOhlcv's oldest-first contract.
        bars.sort((a, b) -> a.getOpenTime().compareTo(b.getOpenTime()));
        return bars;
    }

    // ── Ticker snapshot (Step 5.2 B) ────────────────────────────────────────────

    @Override
    public TickerSnapshotDto getTickerSnapshot(String symbol) {
        HttpUrl url = HttpUrl.parse(properties.getRestBaseUrl() + TICKERS_PATH)
                .newBuilder()
                .addQueryParameter("symbols", symbol)
                .build();

        BitUnixTickerResponse response = execute(url, BitUnixTickerResponse.class);
        if (response.getCode() != 0) {
            throw new MarketDataException(
                    "BitUnix Tickers error for " + symbol
                    + " — code=" + response.getCode() + " msg=" + response.getMsg());
        }
        if (response.getData() == null || response.getData().isEmpty()) {
            throw new MarketDataException("BitUnix returned no ticker data for symbol " + symbol);
        }

        BitUnixTickerItem item = response.getData().get(0);
        BigDecimal lastPrice = toBigDecimal(item.getLastPrice());
        BigDecimal openPrice = toBigDecimal(item.getOpen());
        BigDecimal priceChange = (lastPrice != null && openPrice != null)
                ? lastPrice.subtract(openPrice)
                : null;
        BigDecimal priceChangePercent = (priceChange != null && openPrice != null
                && openPrice.compareTo(BigDecimal.ZERO) != 0)
                ? priceChange.divide(openPrice, 8, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                : null;

        return TickerSnapshotDto.builder()
                .source(BrokerType.BITUNIX)
                .symbol(item.getSymbol())
                .lastPrice(lastPrice)
                .bidPrice(null) // BitUnix's Tickers endpoint does not report book bid/ask
                .askPrice(null)
                .openPrice24h(openPrice)
                .highPrice24h(toBigDecimal(item.getHigh()))
                .lowPrice24h(toBigDecimal(item.getLow()))
                .volume24h(toBigDecimal(item.getBaseVol()))
                .quoteVolume24h(toBigDecimal(item.getQuoteVol()))
                .priceChange24h(priceChange)
                .priceChangePercent24h(priceChangePercent)
                .snapshotTime(Instant.now())
                .build();
    }

    // ── Live price streaming (Step 5.4) ─────────────────────────────────────────

    /**
     * Opens a blocking {@link Stream} of live price ticks for {@code symbol}.
     *
     * <p><strong>Step 5.2 status:</strong> this method is intentionally not
     * yet wired to BitUnix's WebSocket feed — that is Step 5.4's scope (the
     * shared BitUnix WebSocket client + subscription management does not
     * exist yet). It will delegate to that client once Step 5.4 lands; until
     * then it fails fast rather than silently returning an empty/broken
     * stream.</p>
     *
     * @throws UnsupportedOperationException always, until Step 5.4 wires the
     *         BitUnix WebSocket client in
     */
    @Override
    public Stream<PriceTickDto> streamLivePrice(String symbol) {
        throw new UnsupportedOperationException(
                "BitUnixMarketDataProvider#streamLivePrice pending Step 5.4's "
                + "WebSocket client — REST/caching (Steps 5.2/5.3) only so far.");
    }

    // ── Internal HTTP helper ────────────────────────────────────────────────────

    private <T> T execute(HttpUrl url, Class<T> responseType) {
        Request request = new Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new MarketDataException(
                        "BitUnix HTTP " + response.code() + " for " + url);
            }
            String body = response.body().string();
            return gson.fromJson(body, responseType);
        } catch (IOException e) {
            throw new MarketDataException("BitUnix request failed for " + url, e);
        }
    }

    private static BigDecimal toBigDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            log.warn("Unparseable BitUnix numeric field: '{}'", value);
            return null;
        }
    }
}
