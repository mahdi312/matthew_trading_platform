package com.mst.matt.marketservice.charting.repository;

import com.mst.matt.marketservice.charting.model.DrawingLayout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link DrawingLayout} entities.
 * Scoped by {@code userId} — no UserProfile join.
 */
@Repository
public interface DrawingLayoutRepository extends JpaRepository<DrawingLayout, Long> {

    List<DrawingLayout> findByUserIdAndSymbolAndTimeframeOrderBySavedAtEpochDesc(
            Long userId, String symbol, String timeframe);

    Optional<DrawingLayout> findByUserIdAndSymbolAndTimeframeAndName(
            Long userId, String symbol, String timeframe, String name);

    @Modifying
    @Query("DELETE FROM DrawingLayout dl WHERE dl.userId = :userId AND dl.symbol = :symbol "
            + "AND dl.timeframe = :timeframe AND dl.name = :name")
    void deleteByUserIdAndSymbolAndTimeframeAndName(
            @Param("userId") Long userId,
            @Param("symbol") String symbol,
            @Param("timeframe") String timeframe,
            @Param("name") String name);
}
