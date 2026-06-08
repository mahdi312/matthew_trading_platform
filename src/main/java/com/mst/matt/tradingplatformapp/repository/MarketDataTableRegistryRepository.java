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

    /**
     * Returns ALL rows for a symbol+timeframe pair (across all providers).
     * Use this instead of findBySymbolAndTimeframe to avoid IncorrectResultSizeDataAccessException
     * when multiple providers have stored data for the same symbol/timeframe.
     */
    List<MarketDataTableRegistry> findAllBySymbolAndTimeframe(String symbol, String timeframe);

    /**
     * Returns the first matching row for symbol+timeframe — safe when duplicates may exist.
     */
    @Query("SELECT r FROM MarketDataTableRegistry r WHERE r.symbol = :symbol AND r.timeframe = :timeframe ORDER BY r.id ASC")
    List<MarketDataTableRegistry> findBySymbolAndTimeframeSafe(String symbol, String timeframe);

    default Optional<MarketDataTableRegistry> findBySymbolAndTimeframe(String symbol, String timeframe) {
        List<MarketDataTableRegistry> results = findAllBySymbolAndTimeframe(symbol, timeframe);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    Optional<MarketDataTableRegistry> findByTableName(String tableName);

    @Query("SELECT r FROM MarketDataTableRegistry r WHERE r.nextSyncAt IS NULL OR r.nextSyncAt <= :now")
    List<MarketDataTableRegistry> findDueForSync(LocalDateTime now);
}
