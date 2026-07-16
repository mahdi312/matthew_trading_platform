package com.mst.matt.tradingservice.repository;

import com.mst.matt.tradingservice.model.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * JPA repository for {@link Trade} records.
 *
 * <p>Ported from the desktop monolith's {@code TradeRepository}.
 * The original used a {@code UserProfile} entity parameter; this version
 * replaces it with a plain {@code Long userId} to avoid cross-service entity
 * dependencies — identity is managed by identity-service.
 */
@Repository
public interface TradeRepository extends JpaRepository<Trade, Long> {

    List<Trade> findByUserIdOrderByEntryTimeDesc(Long userId);

    List<Trade> findByUserIdAndStatus(Long userId, Trade.TradeStatus status);

    List<Trade> findByUserIdAndAssetType(Long userId, Trade.AssetType assetType);

    @Query("SELECT t FROM Trade t WHERE t.userId = :userId AND t.symbol = :symbol ORDER BY t.entryTime DESC")
    List<Trade> findByUserIdAndSymbol(@Param("userId") Long userId, @Param("symbol") String symbol);

    @Query("SELECT COUNT(t) FROM Trade t WHERE t.userId = :userId AND t.pnlAmount > 0 AND t.status = 'CLOSED'")
    long countWinningTrades(@Param("userId") Long userId);

    @Query("SELECT COUNT(t) FROM Trade t WHERE t.userId = :userId AND t.status = 'CLOSED'")
    long countClosedTrades(@Param("userId") Long userId);

    /** Find all trades sourced from broker live order placement. */
    List<Trade> findByUserIdAndSource(Long userId, Trade.TradeSource source);

    /**
     * Look up a specific trade by its broker-assigned order ID (for WS update reconciliation).
     */
    java.util.Optional<Trade> findByBrokerOrderId(String brokerOrderId);

    java.util.Optional<Trade> findByIdempotencyKey(String idempotencyKey);

}
