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
import java.util.List;
import java.util.Optional;

/**
 * Typed HTTP client for {@code alert-service} exposed through the Gateway at
 * {@code /api/alerts/**}.
 *
 * <p>DTOs mirror {@code alert-service}'s {@code CreateAlertRequestDto},
 * {@code UpdateAlertRequestDto}, and {@code AlertResponseDto}.</p>
 */
@Slf4j
@Component
public class AlertApiClient {

    private final WebClient webClient;

    public AlertApiClient(WebClient gatewayWebClient) {
        this.webClient = gatewayWebClient;
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    /** Request body for {@code POST /api/alerts}. */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CreateAlertRequest {
        private String     symbol;
        private String     assetClass;
        private String     brokerType;
        private String     providerName;
        private String     condition;
        private BigDecimal targetValue;
        private String     message;
        private boolean    repeating;
        private int        cooldownSeconds;

        /**
         * Notification-channel intent captured from the UI at alert-creation time.
         * {@code alert-service} does not persist per-alert channel overrides yet —
         * dispatch is owned solely by {@code notification-service} — so the server
         * currently ignores these fields (Spring Boot's default Jackson config does
         * not fail on unknown properties). Kept here so the value isn't silently
         * dropped once the server-side contract adds support for it.
         */
        private boolean    notifyEmail;
        private boolean    notifyTelegram;
        private boolean    notifyDesktop;
    }

    /** Request body for {@code PUT /api/alerts/{id}} — all fields optional. */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UpdateAlertRequest {
        private String     condition;
        private BigDecimal targetValue;
        private String     status;
        private String     message;
        private Boolean    repeating;
        private Integer    cooldownSeconds;
    }

    /** Response body from all alert endpoints. */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AlertResponse {
        private Long       id;
        private Long       userId;
        private String     symbol;
        private String     assetClass;
        private String     brokerType;
        private String     providerName;
        private String     condition;
        private BigDecimal targetValue;
        private String     status;
        private String     message;
        private boolean    repeating;
        private int        cooldownSeconds;
        private Instant    lastTriggeredAt;
        private Instant    createdAt;
        private Instant    updatedAt;
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    /** {@code GET /api/alerts} */
    public List<AlertResponse> listAlerts() {
        try {
            List<AlertResponse> alerts = webClient.get()
                    .uri("/api/alerts")
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, r ->
                            r.bodyToMono(String.class)
                             .flatMap(body -> Mono.error(
                                     new RuntimeException("Fetch alerts failed: " + body))))
                    .bodyToMono(new ParameterizedTypeReference<List<AlertResponse>>() {})
                    .block();
            return alerts != null ? alerts : List.of();
        } catch (Exception ex) {
            log.warn("listAlerts failed: {}", ex.getMessage());
            return List.of();
        }
    }

    /** {@code GET /api/alerts/{id}} */
    public Optional<AlertResponse> getAlert(Long id) {
        try {
            AlertResponse alert = webClient.get()
                    .uri("/api/alerts/{id}", id)
                    .retrieve()
                    .onStatus(status -> status.value() == 404, r -> Mono.empty())
                    .onStatus(HttpStatusCode::isError, r ->
                            r.bodyToMono(String.class)
                             .flatMap(body -> Mono.error(
                                     new RuntimeException("Fetch alert failed: " + body))))
                    .bodyToMono(AlertResponse.class)
                    .block();
            return Optional.ofNullable(alert);
        } catch (Exception ex) {
            log.warn("getAlert({}) failed: {}", id, ex.getMessage());
            return Optional.empty();
        }
    }

    /** {@code POST /api/alerts} */
    public AlertResponse createAlert(CreateAlertRequest request) {
        try {
            AlertResponse created = webClient.post()
                    .uri("/api/alerts")
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, r ->
                            r.bodyToMono(String.class)
                             .flatMap(body -> Mono.error(
                                     new RuntimeException("Create alert failed: " + body))))
                    .bodyToMono(AlertResponse.class)
                    .block();
            if (created == null) throw new RuntimeException("Server returned empty response.");
            log.debug("Alert created: id={} symbol={}", created.getId(), created.getSymbol());
            return created;
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException("Could not create alert: " + ex.getMessage(), ex);
        }
    }

    /** {@code PUT /api/alerts/{id}} */
    public AlertResponse updateAlert(Long id, UpdateAlertRequest request) {
        try {
            AlertResponse updated = webClient.put()
                    .uri("/api/alerts/{id}", id)
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(status -> status.value() == 404, r ->
                            r.bodyToMono(String.class)
                             .flatMap(body -> Mono.error(
                                     new RuntimeException("Alert not found: " + body))))
                    .onStatus(HttpStatusCode::isError, r ->
                            r.bodyToMono(String.class)
                             .flatMap(body -> Mono.error(
                                     new RuntimeException("Update alert failed: " + body))))
                    .bodyToMono(AlertResponse.class)
                    .block();
            if (updated == null) throw new RuntimeException("Server returned empty response.");
            log.debug("Alert updated: id={}", id);
            return updated;
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException("Could not update alert: " + ex.getMessage(), ex);
        }
    }

    /** {@code DELETE /api/alerts/{id}} */
    public void deleteAlert(Long id) {
        try {
            webClient.delete()
                    .uri("/api/alerts/{id}", id)
                    .retrieve()
                    .onStatus(status -> status.value() == 404, r ->
                            r.bodyToMono(String.class)
                             .flatMap(body -> Mono.error(
                                     new RuntimeException("Alert not found: " + body))))
                    .onStatus(HttpStatusCode::isError, r ->
                            r.bodyToMono(String.class)
                             .flatMap(body -> Mono.error(
                                     new RuntimeException("Delete alert failed: " + body))))
                    .toBodilessEntity()
                    .block();
            log.debug("Alert deleted: id={}", id);
        } catch (Exception ex) {
            throw new RuntimeException("Could not delete alert: " + ex.getMessage(), ex);
        }
    }
}
