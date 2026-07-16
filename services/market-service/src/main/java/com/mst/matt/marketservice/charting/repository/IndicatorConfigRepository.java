package com.mst.matt.marketservice.charting.repository;

import com.mst.matt.marketservice.charting.model.IndicatorConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for {@link IndicatorConfig} entities.
 * One config row per user (unique on user_id).
 */
@Repository
public interface IndicatorConfigRepository extends JpaRepository<IndicatorConfig, Long> {

    Optional<IndicatorConfig> findByUserId(Long userId);
}
