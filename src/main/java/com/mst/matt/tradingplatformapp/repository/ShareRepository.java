package com.mst.matt.tradingplatformapp.repository;

import com.mst.matt.tradingplatformapp.model.Share;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ShareRepository extends JpaRepository<Share, Long> {
    Optional<Share> findBySymbolAndMarketId(String symbol, Long marketId);
}
