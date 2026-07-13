package com.mst.matt.alertservice.controller;

import com.mst.matt.alertservice.dto.AlertResponseDto;
import com.mst.matt.alertservice.dto.CreateAlertRequestDto;
import com.mst.matt.alertservice.dto.UpdateAlertRequestDto;
import com.mst.matt.alertservice.model.PriceAlert;
import com.mst.matt.alertservice.service.AlertService;
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
 * CRUD REST controller for {@code /alerts}.
 *
 * <p>The owning user id is read from the {@code X-User-Id} header injected
 * by {@code gateway-service}'s {@code GatewayJwtAuthFilter} — this service
 * never validates the JWT itself (validated once at the edge, per
 * {@code .cursorrules}).</p>
 */
@Slf4j
@RestController
@RequestMapping("/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @PostMapping
    public ResponseEntity<AlertResponseDto> create(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody CreateAlertRequestDto request) {
        PriceAlert created = alertService.create(userId, request);
        log.info("Alert created: id={} userId={} symbol={} condition={}",
                created.getId(), userId, created.getSymbol(), created.getCondition());
        return ResponseEntity.status(HttpStatus.CREATED).body(AlertResponseDto.from(created));
    }

    @GetMapping
    public ResponseEntity<List<AlertResponseDto>> listForUser(
            @RequestHeader("X-User-Id") Long userId) {
        List<AlertResponseDto> alerts = alertService.listForUser(userId).stream()
                .map(AlertResponseDto::from)
                .toList();
        return ResponseEntity.ok(alerts);
    }

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
