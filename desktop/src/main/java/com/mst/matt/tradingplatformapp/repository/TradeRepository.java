package com.mst.matt.tradingplatformapp.repository;

import com.mst.matt.tradingplatformapp.model.Trade;
import com.mst.matt.tradingplatformapp.model.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TradeRepository extends JpaRepository<Trade, Long> {
    List<Trade> findByProfileOrderByEntryTimeDesc(UserProfile profile);
    List<Trade> findByProfileAndStatus(UserProfile profile, Trade.TradeStatus status);
    List<Trade> findByProfileAndAssetType(UserProfile profile, Trade.AssetType type);

    @Query("SELECT t FROM Trade t WHERE t.profile = :profile AND t.symbol = :symbol ORDER BY t.entryTime DESC")
    List<Trade> findByProfileAndSymbol(UserProfile profile, String symbol);

    @Query("SELECT COUNT(t) FROM Trade t WHERE t.profile = :profile AND t.pnlAmount > 0 AND t.status = 'CLOSED'")
    long countWinningTrades(UserProfile profile);

    @Query("SELECT COUNT(t) FROM Trade t WHERE t.profile = :profile AND t.status = 'CLOSED'")
    long countClosedTrades(UserProfile profile);
}