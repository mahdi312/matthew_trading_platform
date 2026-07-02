package com.mst.matt.tradingplatformapp.service.price;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mst.matt.tradingplatformapp.config.MarketApiProperties;
import com.mst.matt.tradingplatformapp.model.OhlcvBar;
import com.mst.matt.tradingplatformapp.model.Trade.AssetType;
import com.mst.matt.tradingplatformapp.service.price.api.twelvedata.*;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * TwelveData market-data service — free-tier integration.
 *
 * <h3>Free-tier endpoints covered:</h3>
 * <ul>
 *   <li>{@code GET /time_series}         — historical OHLCV bars</li>
 *   <li>{@code GET /quote}               — full real-time quote snapshot</li>
 *   <li>{@code GET /price}               — single latest price (lightest endpoint)</li>
 *   <li>{@code GET /eod}                 — end-of-day closing price</li>
 *   <li>{@code GET /exchange_rate}       — currency-pair exchange rate</li>
 *   <li>{@code GET /currency_conversion} — amount conversion between two currencies</li>
 *   <li>{@code GET /market_movers}       — top gainers / losers</li>
 *   <li>{@code GET /exchange_status}     — which exchanges are open / closed</li>
 *   <li>{@code GET /logo}                — company / crypto / forex logo URL</li>
 * </ul>
 *
 * <h3>Rate limiting:</h3>
 * All HTTP calls pass through {@link TwelveDataRateLimiter} which enforces the
 * 8 credits/min and 800 credits/day free-tier limits.
 *
 * <h3>Authentication:</h3>
 * The API key is appended as {@code &apikey=...} on every request.
 * When the key is {@code "demo"} or blank, the API works only for a small set
 * of trial symbols (AAPL, EUR/USD, BTC/USD).
 */
@Service
public class TwelveDataPriceService implements PriceService {

    private static final Logger log = LoggerFactory.getLogger(TwelveDataPriceService.class);

    // Package-private + non-final so TwelveDataPriceServiceTest can redirect via ReflectionTestUtils.
    String baseUrl = "https://api.twelvedata.com";

    private static final DateTimeFormatter DT_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DT_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final OkHttpClient  httpClient;
    private final MarketApiProperties keys;
    private final TwelveDataRateLimiter rateLimiter;
    private final Gson          gson = new Gson();

    @Autowired
    public TwelveDataPriceService(
            @Qualifier("priceHttpClient") OkHttpClient httpClient,
            MarketApiProperties keys,
            TwelveDataRateLimiter rateLimiter) {
        this.httpClient  = httpClient;
        this.keys        = keys;
        this.rateLimiter = rateLimiter;
    }

    // ─── PriceService implementation ───────────────────────────────────────────

    @Override
    public boolean isEnabled() { return keys.hasTwelvedataKey(); }

    /**
     * Returns a full quote for the given symbol using {@code GET /quote}.
     * Falls back to {@code GET /price} if the quote endpoint returns no data.
     */
    @Override
    public Optional<PriceQuote> getQuote(String symbol) {
        Optional<TwelveDataQuote> quoteOpt = fetchQuote(symbol);
        if (quoteOpt.isPresent()) {
            TwelveDataQuote q = quoteOpt.get();
            BigDecimal price = parseBD(q.close());
            if (price.compareTo(BigDecimal.ZERO) == 0) price = parseBD(q.open());
            if (price.compareTo(BigDecimal.ZERO) == 0) return Optional.empty();

            BigDecimal change = parseBD(q.change());
            BigDecimal pct    = parseBD(q.percentChange());
            return Optional.of(PriceQuote.builder()
                    .symbol(SymbolNormalizer.normalize(symbol))
                    .assetName(q.name() != null ? q.name() : symbol.toUpperCase())
                    .assetType(assetType(symbol))
                    .price(price)
                    .change24h(change)
                    .changePct24h(pct)
                    .timestamp(LocalDateTime.now())
                    .isUp(change.compareTo(BigDecimal.ZERO) >= 0)
                    .build());
        }

        // Fallback: /price endpoint
        return fetchLatestPrice(symbol).map(p -> PriceQuote.builder()
                .symbol(SymbolNormalizer.normalize(symbol))
                .assetName(symbol.toUpperCase())
                .assetType(assetType(symbol))
                .price(p)
                .change24h(BigDecimal.ZERO)
                .changePct24h(BigDecimal.ZERO)
                .timestamp(LocalDateTime.now())
                .isUp(true)
                .build());
    }

    /**
     * Returns historical OHLCV bars using {@code GET /time_series}.
     * Bars are returned in chronological order (oldest → newest).
     */
    @Override
    public List<OhlcvBar> getOhlcv(String symbol, String timeframe, int limit) {
        String sym      = formatSymbol(symbol);
        String interval = mapInterval(timeframe);
        int    size     = Math.min(Math.max(limit, 1), 5000);
        String url      = buildUrl("/time_series",
                "symbol=" + enc(sym) + "&interval=" + interval + "&outputsize=" + size);

        rateLimiter.acquire();
        try (Response response = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(response);
            if (!response.isSuccessful() || response.body() == null) return List.of();
            String body = response.body().string();
            TwelveDataTimeSeries ts = gson.fromJson(body, TwelveDataTimeSeries.class);
            if (ts == null || !ts.isOk() || ts.values() == null) return List.of();

            String norm = SymbolNormalizer.normalize(symbol);
            List<OhlcvBar> bars = new ArrayList<>(ts.values().size());
            for (TwelveDataTimeSeries.Bar v : ts.values()) {
                LocalDateTime openTime = parseDateTime(v.datetime());
                bars.add(OhlcvBar.builder()
                        .symbol(norm)
                        .timeframe(timeframe)
                        .openTime(openTime)
                        .open(parseBD(v.open()))
                        .high(parseBD(v.high()))
                        .low(parseBD(v.low()))
                        .close(parseBD(v.close()))
                        .volume(parseBD(v.volume()))
                        .assetType(assetType(symbol))
                        .build());
            }
            // TwelveData returns newest-first; reverse to chronological order
            Collections.reverse(bars);
            return bars.size() > limit ? bars.subList(bars.size() - limit, bars.size()) : bars;
        } catch (IOException e) {
            log.error("TwelveData time_series error for {}: {}", symbol, e.getMessage());
            return List.of();
        }
    }

    @Override
    public boolean supports(String symbol) {
        return isEnabled() && !SymbolNormalizer.normalize(symbol).isEmpty();
    }

    @Override
    public String getProviderName() { return "Twelve Data"; }

    @Override
    public MarketDataProvider getProviderId() { return MarketDataProvider.TWELVE_DATA; }

    // ─── Core Market Data Endpoint Wrappers ────────────────────────────────────

    /**
     * {@code GET /quote} — full real-time quote snapshot.
     *
     * @param symbol instrument ticker (e.g. "AAPL", "EUR/USD", "BTC/USD")
     * @return typed quote, or empty on error / missing key
     */
    public Optional<TwelveDataQuote> fetchQuote(String symbol) {
        String sym = formatSymbol(symbol);
        String url = buildUrl("/quote", "symbol=" + enc(sym));
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            TwelveDataQuote q = gson.fromJson(r.body().string(), TwelveDataQuote.class);
            if (q == null || q.symbol() == null) return Optional.empty();
            return Optional.of(q);
        } catch (IOException e) {
            log.warn("TwelveData /quote error for {}: {}", symbol, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * {@code GET /price} — returns only the latest traded price.
     * Lightest-weight endpoint; ideal for ticker polling.
     *
     * @param symbol instrument ticker
     * @return latest price as BigDecimal, or empty on error
     */
    public Optional<BigDecimal> fetchLatestPrice(String symbol) {
        String sym = formatSymbol(symbol);
        String url = buildUrl("/price", "symbol=" + enc(sym));
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            TwelveDataPrice p = gson.fromJson(r.body().string(), TwelveDataPrice.class);
            if (p == null || p.price() == null || p.price().isBlank()) return Optional.empty();
            return Optional.of(parseBD(p.price()));
        } catch (IOException e) {
            log.warn("TwelveData /price error for {}: {}", symbol, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * {@code GET /price} — batch: fetch prices for multiple symbols in one request.
     * Symbols are comma-separated (up to ~120 per request).
     *
     * @param symbolsCsv comma-separated tickers (e.g. "AAPL,TSLA,MSFT")
     * @return raw JSON object with symbol→price map, or empty on error
     */
    public Optional<JsonObject> fetchBatchPrices(String symbolsCsv) {
        String url = buildUrl("/price", "symbol=" + enc(symbolsCsv));
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject root = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(root);
        } catch (IOException e) {
            log.warn("TwelveData /price (batch) error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * {@code GET /eod} — most recent end-of-day closing price.
     *
     * @param symbol instrument ticker
     * @return typed EOD response, or empty on error
     */
    public Optional<TwelveDataEod> fetchEod(String symbol) {
        String sym = formatSymbol(symbol);
        String url = buildUrl("/eod", "symbol=" + enc(sym));
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            TwelveDataEod eod = gson.fromJson(r.body().string(), TwelveDataEod.class);
            if (eod == null || eod.close() == null) return Optional.empty();
            return Optional.of(eod);
        } catch (IOException e) {
            log.warn("TwelveData /eod error for {}: {}", symbol, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * {@code GET /exchange_rate} — current exchange rate for a currency pair.
     *
     * @param symbol currency pair in Twelve Data format, e.g. "USD/JPY" or "EUR/USD"
     * @return typed exchange rate, or empty on error
     */
    public Optional<TwelveDataExchangeRate> fetchExchangeRate(String symbol) {
        String url = buildUrl("/exchange_rate", "symbol=" + enc(symbol));
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            TwelveDataExchangeRate rate = gson.fromJson(r.body().string(), TwelveDataExchangeRate.class);
            if (rate == null || rate.rate() == null) return Optional.empty();
            return Optional.of(rate);
        } catch (IOException e) {
            log.warn("TwelveData /exchange_rate error for {}: {}", symbol, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * {@code GET /currency_conversion} — converts an amount from one currency to another.
     *
     * @param symbol currency pair, e.g. "EUR/USD"
     * @param amount amount to convert
     * @return typed conversion result, or empty on error
     */
    public Optional<TwelveDataCurrencyConversion> fetchCurrencyConversion(String symbol, double amount) {
        String url = buildUrl("/currency_conversion",
                "symbol=" + enc(symbol) + "&amount=" + amount);
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            TwelveDataCurrencyConversion conv = gson.fromJson(r.body().string(), TwelveDataCurrencyConversion.class);
            if (conv == null || conv.amount() == null) return Optional.empty();
            return Optional.of(conv);
        } catch (IOException e) {
            log.warn("TwelveData /currency_conversion error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * {@code GET /market_movers} — top gainers and losers for a given exchange.
     * Optionally filter by exchange and country.
     *
     * @param exchange exchange code (e.g. "NASDAQ"), or null for all
     * @param country  country name or code, or null for all
     * @return raw JSON object containing gainers/losers arrays, or empty on error
     */
    public Optional<JsonObject> fetchMarketMovers(String exchange, String country) {
        StringBuilder params = new StringBuilder();
        if (exchange != null && !exchange.isBlank())
            params.append("exchange=").append(enc(exchange));
        if (country != null && !country.isBlank()) {
            if (!params.isEmpty()) params.append("&");
            params.append("country=").append(enc(country));
        }
        String url = buildUrl("/market_movers", params.toString());
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject root = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(root);
        } catch (IOException e) {
            log.warn("TwelveData /market_movers error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * {@code GET /market_movers} — typed: returns parsed gainers + losers.
     */
    public Optional<TwelveDataMarketMover> fetchMarketMoversTyped(String exchange, String country) {
        return fetchMarketMovers(exchange, country)
                .map(root -> gson.fromJson(root, TwelveDataMarketMover.class));
    }

    /**
     * {@code GET /exchange_status} — check which exchanges are open or closed right now.
     *
     * @return raw JSON with exchange status data, or empty on error
     */
    public Optional<JsonObject> fetchExchangeStatus() {
        String url = buildUrl("/exchange_status", "");
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject root = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(root);
        } catch (IOException e) {
            log.warn("TwelveData /exchange_status error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * {@code GET /exchange_status} — typed variant.
     */
    public Optional<TwelveDataExchangeStatus> fetchExchangeStatusTyped() {
        return fetchExchangeStatus()
                .map(root -> gson.fromJson(root, TwelveDataExchangeStatus.class));
    }

    /**
     * {@code GET /logo} — returns the logo URL for a company, crypto, or forex pair.
     *
     * @param symbol instrument ticker
     * @return logo URL string, or empty on error
     */
    public Optional<String> fetchLogoUrl(String symbol) {
        String url = buildUrl("/logo", "symbol=" + enc(formatSymbol(symbol)));
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            TwelveDataLogo logo = gson.fromJson(r.body().string(), TwelveDataLogo.class);
            if (logo == null || logo.url() == null || logo.url().isBlank()) return Optional.empty();
            return Optional.of(logo.url());
        } catch (IOException e) {
            log.warn("TwelveData /logo error for {}: {}", symbol, e.getMessage());
            return Optional.empty();
        }
    }

    // ─── Technical Indicator Endpoints ─────────────────────────────────────────

    /**
     * {@code GET /rsi} — Relative Strength Index.
     *
     * @param symbol     instrument ticker
     * @param interval   timeframe (e.g. "1day", "1h")
     * @param timePeriod RSI lookback period (default 14)
     * @param outputSize number of values to return
     * @return typed indicator response, or empty on error
     */
    public Optional<TwelveDataIndicator> fetchRsi(String symbol, String interval,
                                                   int timePeriod, int outputSize) {
        return fetchIndicator("rsi", symbol, interval, timePeriod, outputSize, null);
    }

    /**
     * {@code GET /macd} — Moving Average Convergence/Divergence.
     *
     * @param symbol      instrument ticker
     * @param interval    timeframe
     * @param fastPeriod  fast EMA period (default 12)
     * @param slowPeriod  slow EMA period (default 26)
     * @param signalPeriod signal period (default 9)
     * @param outputSize  number of values to return
     * @return typed MACD response, or empty on error
     */
    public Optional<TwelveDataMacd> fetchMacd(String symbol, String interval,
                                               int fastPeriod, int slowPeriod,
                                               int signalPeriod, int outputSize) {
        String sym = formatSymbol(symbol);
        String url = buildUrl("/macd",
                "symbol=" + enc(sym) +
                "&interval=" + interval +
                "&fast_period=" + fastPeriod +
                "&slow_period=" + slowPeriod +
                "&signal_period=" + signalPeriod +
                "&outputsize=" + Math.min(outputSize, 5000));
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            TwelveDataMacd macd = gson.fromJson(r.body().string(), TwelveDataMacd.class);
            if (macd == null || !macd.isOk()) return Optional.empty();
            return Optional.of(macd);
        } catch (IOException e) {
            log.warn("TwelveData /macd error for {}: {}", symbol, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * {@code GET /bbands} — Bollinger Bands.
     *
     * @param symbol     instrument ticker
     * @param interval   timeframe
     * @param timePeriod lookback period (default 20)
     * @param nbdevup    multiplier for upper band (default 2)
     * @param nbdevdn    multiplier for lower band (default 2)
     * @param outputSize number of values to return
     * @return typed indicator response, or empty on error
     */
    public Optional<TwelveDataIndicator> fetchBbands(String symbol, String interval,
                                                       int timePeriod, int nbdevup,
                                                       int nbdevdn, int outputSize) {
        String sym = formatSymbol(symbol);
        String url = buildUrl("/bbands",
                "symbol=" + enc(sym) +
                "&interval=" + interval +
                "&time_period=" + timePeriod +
                "&sd=" + nbdevup +
                "&outputsize=" + Math.min(outputSize, 5000));
        return fetchIndicatorFromUrl(url, "/bbands", symbol);
    }

    /**
     * Generic single-value indicator fetcher (RSI, SMA, EMA, WMA, ADX, ATR, CCI, etc.).
     *
     * @param indicatorName endpoint name without leading slash (e.g. "sma", "ema", "adx")
     * @param symbol        instrument ticker
     * @param interval      timeframe
     * @param timePeriod    lookback window (use 0 to omit)
     * @param outputSize    number of values
     * @param seriesType    "close", "open", "high", "low" (null → default "close")
     * @return typed indicator response, or empty on error
     */
    public Optional<TwelveDataIndicator> fetchIndicator(String indicatorName, String symbol,
                                                          String interval, int timePeriod,
                                                          int outputSize, String seriesType) {
        String sym = formatSymbol(symbol);
        StringBuilder params = new StringBuilder();
        params.append("symbol=").append(enc(sym));
        params.append("&interval=").append(interval);
        if (timePeriod > 0) params.append("&time_period=").append(timePeriod);
        if (seriesType != null && !seriesType.isBlank())
            params.append("&series_type=").append(enc(seriesType));
        params.append("&outputsize=").append(Math.min(outputSize, 5000));
        String url = buildUrl("/" + indicatorName, params.toString());
        return fetchIndicatorFromUrl(url, "/" + indicatorName, symbol);
    }

    // ─── URL helpers ───────────────────────────────────────────────────────────

    /**
     * Builds a full TwelveData API URL, appending the API key.
     *
     * @param endpoint  e.g. "/time_series"
     * @param params    query-string without leading "?" (may be empty)
     * @return full URL string
     */
    public String buildUrl(String endpoint, String params) {
        String key = keys.getTwelvedataKey();
        if (key == null || key.isBlank()) key = "demo";
        StringBuilder sb = new StringBuilder(baseUrl).append(endpoint);
        if (params != null && !params.isBlank()) {
            sb.append("?").append(params).append("&apikey=").append(key);
        } else {
            sb.append("?apikey=").append(key);
        }
        return sb.toString();
    }

    // ─── Private helpers ───────────────────────────────────────────────────────

    private Optional<TwelveDataIndicator> fetchIndicatorFromUrl(String url, String endpointName, String symbol) {
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            TwelveDataIndicator ind = gson.fromJson(r.body().string(), TwelveDataIndicator.class);
            if (ind == null || !ind.isOk()) return Optional.empty();
            return Optional.of(ind);
        } catch (IOException e) {
            log.warn("TwelveData {} error for {}: {}", endpointName, symbol, e.getMessage());
            return Optional.empty();
        }
    }

    private void handleRateLimitHeaders(Response response) {
        // Honour Retry-After on 429
        if (response.code() == 429) {
            String retryAfter = response.header("Retry-After");
            long seconds = 60L; // default
            if (retryAfter != null) {
                try { seconds = Long.parseLong(retryAfter.trim()); }
                catch (NumberFormatException ignored) {}
            }
            rateLimiter.recordRetryAfter(seconds);
        }
        // Track credits-used header (api-credits-used)
        String creditsUsedHeader = response.header("api-credits-used");
        if (creditsUsedHeader != null) {
            try {
                rateLimiter.recordCreditsUsed(Integer.parseInt(creditsUsedHeader.trim()));
            } catch (NumberFormatException ignored) {}
        }
    }

    private Request buildRequest(String url) {
        Request.Builder b = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "TradingPlatform/1.0")
                .addHeader("Accept",     "application/json");
        return b.build();
    }

    // ─── Symbol & interval helpers ─────────────────────────────────────────────

    /**
     * Formats a symbol into TwelveData's expected notation.
     * Forex:  "EURUSD"  → "EUR/USD"
     * Crypto: "BTCUSDT" → "BTC/USDT"
     * Stock:  "AAPL"    → "AAPL"
     */
    static String formatSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) return symbol;
        String s = SymbolNormalizer.normalize(symbol);
        // Forex: 6-char all-alpha (e.g. EURUSD → EUR/USD)
        if (AssetClassDetector.isForex(s) && s.length() == 6 && s.matches("[A-Z]{6}")) {
            return s.substring(0, 3) + "/" + s.substring(3);
        }
        // Crypto: ends with USDT (e.g. BTCUSDT → BTC/USDT)
        if (AssetClassDetector.isCrypto(s) && s.endsWith("USDT") && s.length() > 4) {
            return s.substring(0, s.length() - 4) + "/USDT";
        }
        // Crypto: ends with USD (e.g. BTCUSD → BTC/USD)
        if (AssetClassDetector.isCrypto(s) && s.endsWith("USD") && s.length() > 3) {
            return s.substring(0, s.length() - 3) + "/USD";
        }
        return s;
    }

    /**
     * Maps internal timeframe codes to TwelveData interval strings.
     */
    static String mapInterval(String tf) {
        if (tf == null) return "1day";
        return switch (tf.toLowerCase()) {
            case "1m"  -> "1min";
            case "5m"  -> "5min";
            case "15m" -> "15min";
            case "30m" -> "30min";
            case "45m" -> "45min";
            case "1h"  -> "1h";
            case "2h"  -> "2h";
            case "4h"  -> "4h";
            case "8h"  -> "8h";
            case "1w"  -> "1week";
            case "1mo" -> "1month";
            default    -> "1day";
        };
    }

    private static AssetType assetType(String symbol) {
        String s = SymbolNormalizer.normalize(symbol);
        if (AssetClassDetector.isCrypto(s)) return AssetType.CRYPTO;
        if (AssetClassDetector.isForex(s))  return AssetType.FOREX;
        return AssetType.STOCK;
    }

    private static BigDecimal parseBD(String s) {
        if (s == null || s.isBlank()) return BigDecimal.ZERO;
        try { return new BigDecimal(s); }
        catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }

    private static LocalDateTime parseDateTime(String dt) {
        if (dt == null) return LocalDateTime.now();
        try {
            if (dt.length() > 10) return LocalDateTime.parse(dt, DT_DATE_TIME);
            return LocalDateTime.parse(dt + " 00:00:00", DT_DATE_TIME);
        } catch (DateTimeParseException e) {
            return LocalDateTime.now();
        }
    }

    private static String enc(String s) {
        if (s == null) return "";
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    // ─── Package-private accessors (used by sub-services and tests) ────────────

    String getBaseUrl()                     { return baseUrl; }
    TwelveDataRateLimiter getRateLimiter()  { return rateLimiter; }
    Gson getGson()                          { return gson; }
    OkHttpClient getHttpClient()            { return httpClient; }
}
