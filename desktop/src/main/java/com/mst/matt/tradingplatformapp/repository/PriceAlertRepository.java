package com.mst.matt.tradingplatformapp.repository;

import com.mst.matt.tradingplatformapp.model.PriceAlert;
import com.mst.matt.tradingplatformapp.model.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PriceAlertRepository extends JpaRepository<PriceAlert, Long> {
    List<PriceAlert> findByActiveTrue();
    List<PriceAlert> findByProfileAndActiveTrue(UserProfile profile);
    List<PriceAlert> findByProfileOrderByCreatedAtDesc(UserProfile profile);
}