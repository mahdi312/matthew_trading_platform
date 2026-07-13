package com.mst.matt.alertservice.service;

import com.mst.matt.alertservice.dto.CreateAlertRequestDto;
import com.mst.matt.alertservice.dto.UpdateAlertRequestDto;
import com.mst.matt.alertservice.model.PriceAlert;
import com.mst.matt.alertservice.repository.PriceAlertRepository;
import com.mst.matt.contracts.enums.AlertStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * CRUD service for {@link PriceAlert}, backing the
 * {@code POST/GET/PUT/DELETE /alerts} endpoints.
 *
 * <p>Evaluation-loop logic (reading live prices, firing notifications) lives
 * separately in {@link AlertEvaluationService} — this class is
 * persistence-only, per the narrow-transaction-boundary rule in
 * {@code .cursorrules} (never mix a DB transaction with an external call).</p>
 */
@Service
@RequiredArgsConstructor
public class AlertService {

    private final PriceAlertRepository repository;

    @Transactional
    public PriceAlert create(Long userId, CreateAlertRequestDto request) {
        PriceAlert alert = PriceAlert.builder()
                .userId(userId)
                .symbol(request.getSymbol().toUpperCase())
                .assetClass(request.getAssetClass())
                .brokerType(request.getBrokerType())
                .providerName(request.getProviderName())
                .condition(request.getCondition())
                .targetValue(request.getTargetValue())
                .message(request.getMessage())
                .repeating(request.isRepeating())
                .cooldownSeconds(request.getCooldownSeconds())
                .status(AlertStatus.ACTIVE)
                .build();
        return repository.save(alert);
    }

    @Transactional(readOnly = true)
    public List<PriceAlert> listForUser(Long userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public PriceAlert getForUser(Long userId, Long alertId) {
        PriceAlert alert = repository.findById(alertId)
                .orElseThrow(() -> new NoSuchElementException("Alert not found: " + alertId));
        requireOwnership(alert, userId);
        return alert;
    }

    @Transactional
    public PriceAlert update(Long userId, Long alertId, UpdateAlertRequestDto request) {
        PriceAlert alert = repository.findById(alertId)
                .orElseThrow(() -> new NoSuchElementException("Alert not found: " + alertId));
        requireOwnership(alert, userId);

        if (request.getCondition() != null) alert.setCondition(request.getCondition());
        if (request.getTargetValue() != null) alert.setTargetValue(request.getTargetValue());
        if (request.getMessage() != null) alert.setMessage(request.getMessage());
        if (request.getRepeating() != null) alert.setRepeating(request.getRepeating());
        if (request.getCooldownSeconds() != null) alert.setCooldownSeconds(request.getCooldownSeconds());

        if (request.getStatus() != null) {
            alert.setStatus(request.getStatus());
            // Re-arming a one-shot alert (ACTIVE) should clear the stale trigger stamp.
            if (request.getStatus() == AlertStatus.ACTIVE) {
                alert.setLastTriggeredAt(null);
            }
        }
        return repository.save(alert);
    }

    @Transactional
    public void delete(Long userId, Long alertId) {
        PriceAlert alert = repository.findById(alertId)
                .orElseThrow(() -> new NoSuchElementException("Alert not found: " + alertId));
        requireOwnership(alert, userId);
        repository.delete(alert);
    }

    private void requireOwnership(PriceAlert alert, Long userId) {
        if (!alert.getUserId().equals(userId)) {
            throw new NoSuchElementException("Alert not found: " + alert.getId());
        }
    }
}
