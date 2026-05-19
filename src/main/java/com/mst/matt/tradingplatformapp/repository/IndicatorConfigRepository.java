package com.mst.matt.tradingplatformapp.repository;

import com.mst.matt.tradingplatformapp.model.IndicatorConfig;
import com.mst.matt.tradingplatformapp.model.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface IndicatorConfigRepository extends JpaRepository<IndicatorConfig, Long> {
    Optional<IndicatorConfig> findByProfile(UserProfile profile);
}