package com.mst.matt.tradingplatformapp.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Typed HTTP client for {@code market-service} endpoints exposed through the Gateway.
 *
 * <p>Covers OHLCV history, symbol search, chart drawings/layouts, indicator config/compute,
 * and per-user watchlists. All calls block on the calling thread — use
 * {@code Thread.ofVirtual()} from JavaFX controllers.</p>
 */
@Slf4j
@Component
public class MarketApiClient {

    private final WebClient webClient;

    public MarketApiClient(WebClient gatewayWebClient) {
        this.webClient = gatewayWebClient;
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OhlcvBar {
        private Long          id;
        private String        symbol;
        private String        timeframe;
        private LocalDateTime openTime;
        private BigDecimal    open;
        private BigDecimal    high;
        private BigDecimal    low;
        private BigDecimal    close;
        private BigDecimal    volume;
        private String        assetType;
        private String        provider;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SymbolEntry {
        private Long   id;
        private String symbol;
        private String name;
        private String assetType;
        private String exchange;
        private String source;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChartPoint {
        private long   timeEpoch;
        private double price;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChartDrawingProperties {
        private String       color;
        private Double       lineWidth;
        private String       lineStyle;
        private Double       fillOpacity;
        private Boolean      extendLeft;
        private Boolean      extendRight;
        private Double       entryPrice;
        private Double       stopLoss;
        private Double       takeProfit;
        private Double       channelWidth;
        private String       backgroundColor;
        private Double       backgroundOpacity;
        private String       text;
        private Double       fontSize;
        private Double       textBoxWidth;
        private Double       textBoxHeight;
        private String       arrowDirection;
        private String       mirrorAxis;
        private Double       parallelOffset;
        private List<Double> customFibLevels;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChartDrawing {
        private Long                    id;
        private Long                    userId;
        private String                  symbol;
        private String                  timeframe;
        private String                  toolType;
        private String                  pointsJson;
        private String                  propertiesJson;
        private boolean                 locked;
        private long                    createdAtEpoch;
        private String                  layoutName;
        private List<ChartPoint>        points;
        private ChartDrawingProperties  properties;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DrawingLayout {
        private Long   id;
        private Long   userId;
        private String symbol;
        private String timeframe;
        private String name;
        private long   savedAtEpoch;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SaveLayoutRequest {
        private String             symbol;
        private String             timeframe;
        private String             layoutName;
        private List<ChartDrawing> drawings;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IndicatorConfig {
        private Long    id;
        private Long    userId;
        private String  activeProfile;
        private boolean macdEnabled;
        private int     macdWeight;
        private boolean rsiEnabled;
        private int     rsiWeight;
        private int     rsiPeriod;
        private int     rsiOverbought;
        private int     rsiOversold;
        private boolean ichimokuEnabled;
        private int     ichimokuWeight;
        private int     ichimokuTenkanPeriod;
        private int     ichimokuKijunPeriod;
        private int     ichimokuSenkouPeriod;
        private boolean emaEnabled;
        private int     emaWeight;
        private int     emaFastPeriod;
        private int     emaSlowPeriod;
        private int     goldCrossShortPeriod;
        private int     goldCrossLongPeriod;
        private boolean bollingerEnabled;
        private int     bollingerWeight;
        private int     bollingerPeriod;
        private double  bollingerDeviation;
        private boolean fibonacciEnabled;
        private int     fibonacciWeight;
        private int     fibonacciLookback;
        private boolean stochasticEnabled;
        private int     stochasticWeight;
        private int     stochasticKPeriod;
        private int     stochasticDPeriod;
        private boolean atrEnabled;
        private int     atrPeriod;
        private boolean vwapEnabled;
        private int     vwapWeight;
        private boolean cciEnabled;
        private int     cciWeight;
        private int     cciPeriod;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IchimokuResult {
        private double       tenkanSen;
        private double       kijunSen;
        private double       senkouSpanA;
        private double       senkouSpanB;
        private double       chikouSpan;
        private double       cloudTop;
        private double       cloudBottom;
        private boolean      aboveCloud;
        private boolean      belowCloud;
        private int          signal;
        private List<Double> tenkanSeries;
        private List<Double> kijunSeries;
        private List<Double> spanASeries;
        private List<Double> spanBSeries;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IndicatorResult {
        private double       emaFast;
        private double       emaSlow;
        private double       ema50;
        private double       ema200;
        private int          emaCrossSignal;
        private int          goldenDeathCrossSignal;
        private List<Double> emaFastSeries;
        private List<Double> emaSlowSeries;
        private List<Double> ema50Series;
        private List<Double> ema200Series;
        private double       macdLine;
        private double       macdSignal;
        private double       macdHistogram;
        private int          macdCrossSignal;
        private List<Double> macdLineSeries;
        private List<Double> macdSignalSeries;
        private List<Double> macdHistogramSeries;
        private double       rsi;
        private int          rsiSignal;
        private List<Double> rsiSeries;
        private double       bbUpper;
        private double       bbMiddle;
        private double       bbLower;
        private double       bbBandwidth;
        private int          bollingerSignal;
        private List<Double> bbUpperSeries;
        private List<Double> bbMiddleSeries;
        private List<Double> bbLowerSeries;
        private double       stochasticK;
        private double       stochasticD;
        private int          stochasticSignal;
        private List<Double> stochasticKSeries;
        private List<Double> stochasticDSeries;
        private double       atr;
        private List<Double> atrSeries;
        private double       cci;
        private int          cciSignal;
        private List<Double> cciSeries;
        private double       vwap;
        private int          vwapSignal;
        private List<Double> vwapSeries;
        private IchimokuResult ichimoku;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WatchlistItem {
        private Long    id;
        private String  symbol;
        private String  assetClass;
        private Instant addedAt;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AddWatchlistRequest {
        private String symbol;
        private String assetClass;
    }

    // ── OHLCV ────────────────────────────────────────────────────────────────

    /** {@code GET /api/market/ohlcv/{symbol}?timeframe=&limit=} */
    public List<OhlcvBar> getOhlcv(String symbol, String timeframe, int limit) {
        try {
            List<OhlcvBar> bars = webClient.get()
                    .uri(u -> u.path("/api/market/ohlcv/{symbol}")
                            .queryParam("timeframe", timeframe)
                            .queryParam("limit", limit)
                            .build(symbol))
                    .retrieve()
                    .onStatus(status -> status.value() == 204, r -> Mono.empty())
                    .onStatus(HttpStatusCode::isError, r ->
                            r.bodyToMono(String.class)
                             .flatMap(body -> Mono.error(
                                     new RuntimeException("Fetch OHLCV failed: " + body))))
                    .bodyToMono(new ParameterizedTypeReference<List<OhlcvBar>>() {})
                    .block();
            return bars != null ? bars : List.of();
        } catch (Exception ex) {
            log.warn("getOhlcv({}) failed: {}", symbol, ex.getMessage());
            return List.of();
        }
    }

    // ── Symbol search ───────────────────────────────────────────────────────

    /** {@code GET /api/market/symbols/search?q=} */
    public List<SymbolEntry> searchSymbols(String query, String assetType) {
        try {
            List<SymbolEntry> results = webClient.get()
                    .uri(u -> {
                        var builder = u.path("/api/market/symbols/search")
                                .queryParam("q", query);
                        if (assetType != null && !assetType.isBlank()) {
                            builder.queryParam("type", assetType);
                        }
                        return builder.build();
                    })
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, r ->
                            r.bodyToMono(String.class)
                             .flatMap(body -> Mono.error(
                                     new RuntimeException("Symbol search failed: " + body))))
                    .bodyToMono(new ParameterizedTypeReference<List<SymbolEntry>>() {})
                    .block();
            return results != null ? results : List.of();
        } catch (Exception ex) {
            log.warn("searchSymbols('{}') failed: {}", query, ex.getMessage());
            return List.of();
        }
    }

    // ── Chart drawings ──────────────────────────────────────────────────────

    /** {@code GET /api/charts/drawings?symbol=&timeframe=} */
    public List<ChartDrawing> getDrawings(String symbol, String timeframe) {
        try {
            List<ChartDrawing> drawings = webClient.get()
                    .uri(u -> u.path("/api/charts/drawings")
                            .queryParam("symbol", symbol)
                            .queryParam("timeframe", timeframe)
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, r ->
                            r.bodyToMono(String.class)
                             .flatMap(body -> Mono.error(
                                     new RuntimeException("Fetch drawings failed: " + body))))
                    .bodyToMono(new ParameterizedTypeReference<List<ChartDrawing>>() {})
                    .block();
            return drawings != null ? drawings : List.of();
        } catch (Exception ex) {
            log.warn("getDrawings({}/{}) failed: {}", symbol, timeframe, ex.getMessage());
            return List.of();
        }
    }

    /** {@code POST /api/charts/drawings} */
    public ChartDrawing createDrawing(ChartDrawing drawing) {
        try {
            ChartDrawing saved = webClient.post()
                    .uri("/api/charts/drawings")
                    .bodyValue(drawing)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, r ->
                            r.bodyToMono(String.class)
                             .flatMap(body -> Mono.error(
                                     new RuntimeException("Create drawing failed: " + body))))
                    .bodyToMono(ChartDrawing.class)
                    .block();
            if (saved == null) throw new RuntimeException("Server returned empty response.");
            log.debug("Drawing saved: id={} toolType={}", saved.getId(), saved.getToolType());
            return saved;
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException("Could not create drawing: " + ex.getMessage(), ex);
        }
    }

    /** {@code DELETE /api/charts/drawings/{id}} */
    public void deleteDrawing(Long id) {
        try {
            webClient.delete()
                    .uri("/api/charts/drawings/{id}", id)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, r ->
                            r.bodyToMono(String.class)
                             .flatMap(body -> Mono.error(
                                     new RuntimeException("Delete drawing failed: " + body))))
                    .toBodilessEntity()
                    .block();
            log.debug("Drawing deleted: id={}", id);
        } catch (Exception ex) {
            throw new RuntimeException("Could not delete drawing: " + ex.getMessage(), ex);
        }
    }

    // ── Chart layouts ─────────────────────────────────────────────────────────

    /** {@code GET /api/charts/layouts?symbol=&timeframe=} */
    public List<DrawingLayout> getLayouts(String symbol, String timeframe) {
        try {
            List<DrawingLayout> layouts = webClient.get()
                    .uri(u -> u.path("/api/charts/layouts")
                            .queryParam("symbol", symbol)
                            .queryParam("timeframe", timeframe)
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, r ->
                            r.bodyToMono(String.class)
                             .flatMap(body -> Mono.error(
                                     new RuntimeException("Fetch layouts failed: " + body))))
                    .bodyToMono(new ParameterizedTypeReference<List<DrawingLayout>>() {})
                    .block();
            return layouts != null ? layouts : List.of();
        } catch (Exception ex) {
            log.warn("getLayouts({}/{}) failed: {}", symbol, timeframe, ex.getMessage());
            return List.of();
        }
    }

    /** {@code POST /api/charts/layouts} */
    public void saveLayout(SaveLayoutRequest request) {
        try {
            webClient.post()
                    .uri("/api/charts/layouts")
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, r ->
                            r.bodyToMono(String.class)
                             .flatMap(body -> Mono.error(
                                     new RuntimeException("Save layout failed: " + body))))
                    .toBodilessEntity()
                    .block();
            log.debug("Layout saved: name={} symbol={}", request.getLayoutName(), request.getSymbol());
        } catch (Exception ex) {
            throw new RuntimeException("Could not save layout: " + ex.getMessage(), ex);
        }
    }

    // ── Indicators ──────────────────────────────────────────────────────────

    /** {@code GET /api/indicators/configs} */
    public Optional<IndicatorConfig> getIndicatorConfig() {
        try {
            IndicatorConfig config = webClient.get()
                    .uri("/api/indicators/configs")
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, r ->
                            r.bodyToMono(String.class)
                             .flatMap(body -> Mono.error(
                                     new RuntimeException("Fetch indicator config failed: " + body))))
                    .bodyToMono(IndicatorConfig.class)
                    .block();
            return Optional.ofNullable(config);
        } catch (Exception ex) {
            log.warn("getIndicatorConfig failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    /** {@code POST /api/indicators/configs} */
    public IndicatorConfig saveIndicatorConfig(IndicatorConfig config) {
        try {
            IndicatorConfig saved = webClient.post()
                    .uri("/api/indicators/configs")
                    .bodyValue(config)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, r ->
                            r.bodyToMono(String.class)
                             .flatMap(body -> Mono.error(
                                     new RuntimeException("Save indicator config failed: " + body))))
                    .bodyToMono(IndicatorConfig.class)
                    .block();
            if (saved == null) throw new RuntimeException("Server returned empty response.");
            log.debug("Indicator config saved for userId={}", saved.getUserId());
            return saved;
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException("Could not save indicator config: " + ex.getMessage(), ex);
        }
    }

    /**
     * {@code GET /api/indicators/{symbol}/compute?timeframe=&limit=}
     *
     * <p>Note: the server does not accept a {@code types} query parameter — it computes
     * all enabled indicators from the user's saved config.</p>
     */
    public Optional<IndicatorResult> computeIndicators(String symbol, String timeframe, int limit) {
        try {
            IndicatorResult result = webClient.get()
                    .uri(u -> u.path("/api/indicators/{symbol}/compute")
                            .queryParam("timeframe", timeframe)
                            .queryParam("limit", limit)
                            .build(symbol))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, r ->
                            r.bodyToMono(String.class)
                             .flatMap(body -> Mono.error(
                                     new RuntimeException("Compute indicators failed: " + body))))
                    .bodyToMono(IndicatorResult.class)
                    .block();
            return Optional.ofNullable(result);
        } catch (Exception ex) {
            log.warn("computeIndicators({}) failed: {}", symbol, ex.getMessage());
            return Optional.empty();
        }
    }

    // ── Watchlist ─────────────────────────────────────────────────────────────

    /** {@code GET /api/market/watchlist} */
    public List<WatchlistItem> getWatchlist(String assetClass) {
        try {
            List<WatchlistItem> items = webClient.get()
                    .uri(u -> {
                        var builder = u.path("/api/market/watchlist");
                        if (assetClass != null && !assetClass.isBlank()) {
                            builder.queryParam("assetClass", assetClass);
                        }
                        return builder.build();
                    })
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, r ->
                            r.bodyToMono(String.class)
                             .flatMap(body -> Mono.error(
                                     new RuntimeException("Fetch watchlist failed: " + body))))
                    .bodyToMono(new ParameterizedTypeReference<List<WatchlistItem>>() {})
                    .block();
            return items != null ? items : List.of();
        } catch (Exception ex) {
            log.warn("getWatchlist failed: {}", ex.getMessage());
            return List.of();
        }
    }

    /** {@code POST /api/market/watchlist} */
    public WatchlistItem addToWatchlist(AddWatchlistRequest request) {
        try {
            WatchlistItem saved = webClient.post()
                    .uri("/api/market/watchlist")
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, r ->
                            r.bodyToMono(String.class)
                             .flatMap(body -> Mono.error(
                                     new RuntimeException("Add to watchlist failed: " + body))))
                    .bodyToMono(WatchlistItem.class)
                    .block();
            if (saved == null) throw new RuntimeException("Server returned empty response.");
            log.debug("Watchlist entry added: symbol={}", saved.getSymbol());
            return saved;
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException("Could not add to watchlist: " + ex.getMessage(), ex);
        }
    }

    /** {@code DELETE /api/market/watchlist/{symbol}} */
    public void removeFromWatchlist(String symbol) {
        try {
            webClient.delete()
                    .uri("/api/market/watchlist/{symbol}", symbol)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, r ->
                            r.bodyToMono(String.class)
                             .flatMap(body -> Mono.error(
                                     new RuntimeException("Remove from watchlist failed: " + body))))
                    .toBodilessEntity()
                    .block();
            log.debug("Watchlist entry removed: symbol={}", symbol);
        } catch (Exception ex) {
            throw new RuntimeException("Could not remove from watchlist: " + ex.getMessage(), ex);
        }
    }
}
