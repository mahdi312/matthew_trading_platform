package com.mst.matt.marketservice.repository;

import com.mst.matt.marketservice.model.OhlcvBar;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OhlcvBarRepository extends JpaRepository<OhlcvBar, Long> {
    @Query("SELECT b FROM OhlcvBar b WHERE b.symbol = :symbol AND b.timeframe = :timeframe ORDER BY b.openTime DESC")
    List<OhlcvBar> findTopBySymbolAndTimeframe(@Param("symbol") String symbol, @Param("timeframe") String timeframe, Pageable pageable);
    Optional<OhlcvBar> findBySymbolAndTimeframeAndOpenTime(String symbol, String timeframe, LocalDateTime openTime);
    void deleteBySymbolAndTimeframe(String symbol, String timeframe);
    @Query("SELECT b FROM OhlcvBar b WHERE b.symbol = :symbol AND b.timeframe = :timeframe AND b.openTime BETWEEN :from AND :to ORDER BY b.openTime ASC")
    List<OhlcvBar> findBySymbolAndTimeframeAndOpenTimeBetween(@Param("symbol") String symbol, @Param("timeframe") String timeframe, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
