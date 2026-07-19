package com.mst.matt.alertservice.controller;

import com.mst.matt.alertservice.dto.AlertResponseDto;
import com.mst.matt.alertservice.dto.CreateAlertRequestDto;
import com.mst.matt.alertservice.dto.UpdateAlertRequestDto;
import com.mst.matt.alertservice.model.PriceAlert;
import com.mst.matt.alertservice.service.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * CRUD REST controller for {@code /api/alerts}.
 *
 * <p>The owning user id is read from the {@code X-User-Id} header injected
 * by {@code gateway-service}'s {@code GatewayJwtAuthFilter} — this service
 * never validates the JWT itself (validated once at the edge, per
 * {@code .cursorrules}).</p>
 *
 * <p>Base path matches the Gateway route ({@code Path=/api/alerts/**} in
 * {@code gateway-service}'s {@code application.yml}) — the Angular frontend
 * and the desktop {@code AlertApiClient} both call {@code /api/alerts/**}.</p>
 */
@Tag(name = "Alerts", description = "Price alert CRUD for authenticated users")
@SecurityRequirement(name = "bearer-jwt")
@Slf4j
@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @Operation(summary = "Create a new price alert")
    @PostMapping
    public ResponseEntity<AlertResponseDto> create(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody CreateAlertRequestDto request) {
        PriceAlert created = alertService.create(userId, request);
        log.info("Alert created: id={} userId={} symbol={} condition={}",
                created.getId(), userId, created.getSymbol(), created.getCondition());
        return ResponseEntity.status(HttpStatus.CREATED).body(AlertResponseDto.from(created));
    }

    @Operation(summary = "List all alerts for the authenticated user")
    @GetMapping
    public ResponseEntity<List<AlertResponseDto>> listForUser(
            @RequestHeader("X-User-Id") Long userId) {
        List<AlertResponseDto> alerts = alertService.listForUser(userId).stream()
                .map(AlertResponseDto::from)
                .toList();
        return ResponseEntity.ok(alerts);
    }

    @Operation(summary = "Get a single alert by ID")
    @GetMapping("/{id}")
    public ResponseEntity<?> getOne(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id) {
        try {
            return ResponseEntity.ok(AlertResponseDto.from(alertService.getForUser(userId, id)));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    @Operation(summary = "Update an alert by ID")
    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id,
            @RequestBody UpdateAlertRequestDto request) {
        try {
            PriceAlert updated = alertService.update(userId, id, request);
            return ResponseEntity.ok(AlertResponseDto.from(updated));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    @Operation(summary = "Delete an alert by ID")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id) {
        try {
            alertService.delete(userId, id);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", ex.getMessage()));
        }
    }
}
