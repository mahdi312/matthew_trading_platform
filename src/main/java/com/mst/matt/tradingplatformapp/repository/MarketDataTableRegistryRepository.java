package com.mst.matt.tradingplatformapp.repository;

import com.mst.matt.tradingplatformapp.model.MarketDataTableRegistry;
import com.mst.matt.tradingplatformapp.service.price.MarketDataProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MarketDataTableRegistryRepository extends JpaRepository<MarketDataTableRegistry, Long> {

    Optional<MarketDataTableRegistry> findBySymbolAndTimeframeAndProvider(
            String symbol, String timeframe, MarketDataProvider provider);

    Optional<MarketDataTableRegistry> findBySymbolAndTimeframe(String symbol, String timeframe);

    Optional<MarketDataTableRegistry> findByTableName(String tableName);

    @Query("SELECT r FROM MarketDataTableRegistry r WHERE r.nextSyncAt IS NULL OR r.nextSyncAt <= :now")
    List<MarketDataTableRegistry> findDueForSync(LocalDateTime now);
}
