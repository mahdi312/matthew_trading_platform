package com.mst.matt.marketservice.repository;
import com.mst.matt.marketservice.model.MarketDataProvider;
import com.mst.matt.marketservice.model.MarketDataTableRegistry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
@Repository
public interface MarketDataTableRegistryRepository extends JpaRepository<MarketDataTableRegistry, Long> {
    Optional<MarketDataTableRegistry> findBySymbolAndTimeframeAndProvider(String symbol, String timeframe, MarketDataProvider provider);
    List<MarketDataTableRegistry> findAllBySymbolAndTimeframe(String symbol, String timeframe);
    @Query("SELECT r FROM MarketDataTableRegistry r WHERE r.symbol = :symbol AND r.timeframe = :timeframe ORDER BY r.id ASC")
    List<MarketDataTableRegistry> findBySymbolAndTimeframeSafe(@Param("symbol") String symbol, @Param("timeframe") String timeframe);
    default Optional<MarketDataTableRegistry> findBySymbolAndTimeframe(String symbol, String timeframe) {
        List<MarketDataTableRegistry> results = findAllBySymbolAndTimeframe(symbol, timeframe);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
    Optional<MarketDataTableRegistry> findByTableName(String tableName);
    @Query("SELECT r FROM MarketDataTableRegistry r WHERE r.nextSyncAt IS NULL OR r.nextSyncAt <= :now")
    List<MarketDataTableRegistry> findDueForSync(@Param("now") LocalDateTime now);
}
