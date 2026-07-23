package com.mst.matt.alertservice.repository;

import com.mst.matt.alertservice.model.PriceAlert;
import com.mst.matt.contracts.enums.AlertStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PriceAlertRepository extends JpaRepository<PriceAlert, Long> {

    List<PriceAlert> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<PriceAlert> findByUserIdAndIdIn(Long userId, List<Long> ids);

    /**
     * Candidate set for the evaluation loop — active or repeating/triggered
     * alerts. Final cooldown eligibility (via {@code isEligibleForEvaluation})
     * is checked in memory in {@code AlertEvaluationService}, not via a
     * second query, to keep this a single short read-only round trip.
     */
    List<PriceAlert> findByStatusInOrderBySymbolAsc(List<AlertStatus> statuses);
}
