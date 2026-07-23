package com.mst.matt.marketservice.charting.repository;

import com.mst.matt.marketservice.charting.model.ChartDrawing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for {@link ChartDrawing} entities.
 * Scoped by {@code userId} (Long from JWT header) — no UserProfile join.
 */
@Repository
public interface ChartDrawingRepository extends JpaRepository<ChartDrawing, Long> {

    /** Active (no named layout) drawings for a symbol/timeframe, oldest first. */
    List<ChartDrawing> findByUserIdAndSymbolAndTimeframeAndLayoutNameIsNullOrderByCreatedAtEpochAsc(
            Long userId, String symbol, String timeframe);

    /** Named layout drawings, oldest first. */
    List<ChartDrawing> findByUserIdAndSymbolAndTimeframeAndLayoutNameOrderByCreatedAtEpochAsc(
            Long userId, String symbol, String timeframe, String layoutName);

    /** All drawings (active + all layouts) for a user/symbol/timeframe. */
    List<ChartDrawing> findByUserIdAndSymbolAndTimeframe(
            Long userId, String symbol, String timeframe);

    /** Count active drawings to enforce the per-chart limit. */
    long countByUserIdAndSymbolAndTimeframeAndLayoutNameIsNull(
            Long userId, String symbol, String timeframe);

    @Modifying
    @Query("DELETE FROM ChartDrawing d WHERE d.userId = :userId AND d.symbol = :symbol "
            + "AND d.timeframe = :timeframe AND d.layoutName IS NULL")
    void deleteActiveDrawings(
            @Param("userId") Long userId,
            @Param("symbol") String symbol,
            @Param("timeframe") String timeframe);

    @Modifying
    @Query("DELETE FROM ChartDrawing d WHERE d.userId = :userId AND d.symbol = :symbol "
            + "AND d.timeframe = :timeframe AND d.layoutName = :layoutName")
    void deleteByLayoutName(
            @Param("userId") Long userId,
            @Param("symbol") String symbol,
            @Param("timeframe") String timeframe,
            @Param("layoutName") String layoutName);
}
