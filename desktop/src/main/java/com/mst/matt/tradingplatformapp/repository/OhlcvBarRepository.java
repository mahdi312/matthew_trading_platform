package com.mst.matt.tradingplatformapp.repository;

import com.mst.matt.tradingplatformapp.model.OhlcvBar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OhlcvBarRepository extends JpaRepository<OhlcvBar, Long> {

    @Query("SELECT b FROM OhlcvBar b WHERE b.symbol = :symbol AND b.timeframe = :timeframe ORDER BY b.openTime DESC")
    List<OhlcvBar> findTopBySymbolAndTimeframe(String symbol, String timeframe,
                                               org.springframework.data.domain.Pageable pageable);

    Optional<OhlcvBar> findBySymbolAndTimeframeAndOpenTime(
            String symbol, String timeframe, LocalDateTime openTime);

    void deleteBySymbolAndTimeframe(String symbol, String timeframe);
}