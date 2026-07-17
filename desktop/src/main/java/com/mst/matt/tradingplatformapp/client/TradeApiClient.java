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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Typed HTTP client for the {@code trading-service} exposed through the Gateway.
 *
 * <h3>Phase 2, Step 12 — trading domain</h3>
 * <p>Covers Trade CRUD ({@code /api/trades/**}), portfolio summary
 * ({@code /api/portfolio/**}), and yearly reports ({@code /api/reports/**}).
 * All calls are blocking from the caller's perspective (use
 * {@code Thread.ofVirtual()} in JavaFX controllers to keep the UI responsive).</p>
 *
 * <p>DTOs intentionally mirror the request/response shapes of
 * {@code trading-service}'s REST controllers so no extra mapping is needed.
 * Mark all fields as {@code @JsonIgnoreProperties(ignoreUnknown = true)} to be
 * forward-compatible when the server adds new fields.</p>
 */
@Slf4j
@Component
public class TradeApiClient {

    private final WebClient webClient;

    public TradeApiClient(WebClient gatewayWebClient) {
        this.webClient = gatewayWebClient;
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    /** Request body for create/update trade. */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TradeRequest {
        private Long        profileId;
        private String      symbol;
        private String      assetName;
        private String      assetType;         // "CRYPTO", "STOCK", "FOREX", etc.
        private String      direction;          // "LONG" | "SHORT"
        private BigDecimal  entryPrice;
        private BigDecimal  exitPrice;
        private BigDecimal  quantity;
        private BigDecimal  stopLoss;
        private BigDecimal  takeProfit;
        private BigDecimal  fee;
        private BigDecimal  leverage;
        private String      exchange;
        private String      strategy;
        private String      notes;
        private String      screenshotPath;
        private String      status;            // "OPEN" | "CLOSED"
        private LocalDateTime entryTime;
        private LocalDateTime exitTime;
    }

    /** Response body from trade endpoints. */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TradeResponse {
        private Long        id;
        private Long        profileId;
        private String      symbol;
        private String      assetName;
        private String      assetType;
        private String      direction;
        private BigDecimal  entryPrice;
        private BigDecimal  exitPrice;
        private BigDecimal  quantity;
        private BigDecimal  stopLoss;
        private BigDecimal  takeProfit;
        private BigDecimal  fee;
        private BigDecimal  leverage;
        private BigDecimal  pnlAmount;
        private BigDecimal  pnlPercent;
        private String      exchange;
        private String      strategy;
        private String      notes;
        private String      screenshotPath;
        private String      status;
        private LocalDateTime entryTime;
        private LocalDateTime exitTime;
    }

    /** Request body for {@code POST /api/trades/{id}/close}. */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CloseTradeRequest {
        private BigDecimal exitPrice;
    }

    /**
     * Response body for {@code GET /api/portfolio/stats} — mirrors
     * {@code trading-service}'s {@code PortfolioStatsResponse}.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PortfolioStatsResponse {
        private int totalTrades;
        private int openTrades;
        private int wins;
        private int losses;
        private BigDecimal winRate;
        private BigDecimal totalPnl;
        private BigDecimal totalPnlPercent;
        private BigDecimal totalInvested;
        private BigDecimal totalFees;
        private BigDecimal bestTrade;
        private BigDecimal worstTrade;
        private BigDecimal avgWin;
        private BigDecimal avgLoss;
        private BigDecimal profitFactor;
        private List<BigDecimal> equityCurve;

        /** Safe zero-value fallback used when the request fails or the user has no trades. */
        public static PortfolioStatsResponse empty() {
            PortfolioStatsResponse s = new PortfolioStatsResponse();
            s.totalPnl = BigDecimal.ZERO;
            s.totalPnlPercent = BigDecimal.ZERO;
            s.totalInvested = BigDecimal.ZERO;
            s.totalFees = BigDecimal.ZERO;
            s.bestTrade = BigDecimal.ZERO;
            s.worstTrade = BigDecimal.ZERO;
            s.avgWin = BigDecimal.ZERO;
            s.avgLoss = BigDecimal.ZERO;
            s.profitFactor = BigDecimal.ZERO;
            s.winRate = BigDecimal.ZERO;
            s.equityCurve = new ArrayList<>();
            return s;
        }
    }

    // ── Trade CRUD ────────────────────────────────────────────────────────────

    /**
     * {@code POST /api/trades} — saves a new trade.
     *
     * @return the persisted trade with its server-assigned id
     * @throws RuntimeException if the server rejects the request
     */
    public TradeResponse saveTrade(TradeRequest request) {
        try {
            TradeResponse resp = webClient.post()
                    .uri("/api/trades")
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, r ->
                            r.bodyToMono(String.class)
                             .flatMap(body -> Mono.error(
                                     new RuntimeException("Save trade failed: " + body))))
                    .bodyToMono(TradeResponse.class)
                    .block();
            if (resp == null) throw new RuntimeException("Server returned empty response.");
            log.debug("Trade saved: id={} symbol={}", resp.getId(), resp.getSymbol());
            return resp;
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException("Could not save trade: " + ex.getMessage(), ex);
        }
    }

    /**
     * {@code PUT /api/trades/{id}} — updates an existing trade.
     *
     * @return the updated trade
     */
    public TradeResponse updateTrade(Long id, TradeRequest request) {
        try {
            TradeResponse resp = webClient.put()
                    .uri("/api/trades/{id}", id)
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, r ->
                            r.bodyToMono(String.class)
                             .flatMap(body -> Mono.error(
                                     new RuntimeException("Update trade failed: " + body))))
                    .bodyToMono(TradeResponse.class)
                    .block();
            if (resp == null) throw new RuntimeException("Server returned empty response.");
            log.debug("Trade updated: id={}", id);
            return resp;
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException("Could not update trade: " + ex.getMessage(), ex);
        }
    }

    /**
     * {@code GET /api/trades?userId={userId}} — lists all trades for a user.
     */
    public List<TradeResponse> getTradesByUserId(Long userId) {
        return getTradesByUserId(userId, null);
    }

    /**
     * {@code GET /api/trades?userId={userId}&status={status}} — lists trades for a user,
     * optionally filtered by status ({@code OPEN} | {@code CLOSED} | {@code CANCELLED}).
     */
    public List<TradeResponse> getTradesByUserId(Long userId, String status) {
        try {
            List<TradeResponse> list = webClient.get()
                    .uri(u -> {
                        var b = u.path("/api/trades").queryParam("userId", userId);
                        if (status != null && !status.isBlank()) b.queryParam("status", status);
                        return b.build();
                    })
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, r ->
                            r.bodyToMono(String.class)
                             .flatMap(body -> Mono.error(
                                     new RuntimeException("Fetch trades failed: " + body))))
                    .bodyToMono(new ParameterizedTypeReference<List<TradeResponse>>() {})
                    .block();
            return list != null ? list : List.of();
        } catch (Exception ex) {
            log.warn("getTradesByUserId({}) failed: {}", userId, ex.getMessage());
            return List.of();
        }
    }

    /**
     * {@code POST /api/trades/{id}/close} — closes an open trade with a given exit price.
     */
    public TradeResponse closeTrade(Long id, BigDecimal exitPrice) {
        try {
            CloseTradeRequest body = new CloseTradeRequest();
            body.setExitPrice(exitPrice);
            TradeResponse resp = webClient.post()
                    .uri("/api/trades/{id}/close", id)
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, r ->
                            r.bodyToMono(String.class)
                             .flatMap(b -> Mono.error(
                                     new RuntimeException("Close trade failed: " + b))))
                    .bodyToMono(TradeResponse.class)
                    .block();
            if (resp == null) throw new RuntimeException("Server returned empty response.");
            log.debug("Trade closed: id={}", id);
            return resp;
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException("Could not close trade: " + ex.getMessage(), ex);
        }
    }

    /**
     * {@code GET /api/portfolio/stats?userId={userId}} — portfolio-level statistics for a user.
     */
    public PortfolioStatsResponse getPortfolioStats(Long userId) {
        try {
            PortfolioStatsResponse resp = webClient.get()
                    .uri(u -> u.path("/api/portfolio/stats").queryParam("userId", userId).build())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, r ->
                            r.bodyToMono(String.class)
                             .flatMap(body -> Mono.error(
                                     new RuntimeException("Fetch portfolio stats failed: " + body))))
                    .bodyToMono(PortfolioStatsResponse.class)
                    .block();
            return resp != null ? resp : PortfolioStatsResponse.empty();
        } catch (Exception ex) {
            log.warn("getPortfolioStats({}) failed: {}", userId, ex.getMessage());
            return PortfolioStatsResponse.empty();
        }
    }

    /**
     * {@code DELETE /api/trades/{id}} — deletes a trade.
     */
    public void deleteTrade(Long id) {
        try {
            webClient.delete()
                    .uri("/api/trades/{id}", id)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, r ->
                            r.bodyToMono(String.class)
                             .flatMap(body -> Mono.error(
                                     new RuntimeException("Delete trade failed: " + body))))
                    .toBodilessEntity()
                    .block();
            log.debug("Trade deleted: id={}", id);
        } catch (Exception ex) {
            throw new RuntimeException("Could not delete trade: " + ex.getMessage(), ex);
        }
    }

    /**
     * {@code GET /api/trades/{id}} — fetches a single trade by id.
     */
    public Optional<TradeResponse> getTradeById(Long id) {
        try {
            TradeResponse resp = webClient.get()
                    .uri("/api/trades/{id}", id)
                    .retrieve()
                    .onStatus(status -> status.value() == 404, r -> Mono.empty())
                    .onStatus(HttpStatusCode::isError, r ->
                            r.bodyToMono(String.class)
                             .flatMap(body -> Mono.error(
                                     new RuntimeException("Fetch trade failed: " + body))))
                    .bodyToMono(TradeResponse.class)
                    .block();
            return Optional.ofNullable(resp);
        } catch (Exception ex) {
            log.warn("getTradeById({}) failed: {}", id, ex.getMessage());
            return Optional.empty();
        }
    }
}
