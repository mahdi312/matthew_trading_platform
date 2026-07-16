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
     * {@code GET /api/trades?profileId={profileId}} — lists all trades for a profile.
     */
    public List<TradeResponse> getTradesByProfile(Long profileId) {
        try {
            List<TradeResponse> list = webClient.get()
                    .uri(u -> u.path("/api/trades").queryParam("profileId", profileId).build())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, r ->
                            r.bodyToMono(String.class)
                             .flatMap(body -> Mono.error(
                                     new RuntimeException("Fetch trades failed: " + body))))
                    .bodyToMono(new ParameterizedTypeReference<List<TradeResponse>>() {})
                    .block();
            return list != null ? list : List.of();
        } catch (Exception ex) {
            log.warn("getTradesByProfile failed: {}", ex.getMessage());
            return List.of();
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
