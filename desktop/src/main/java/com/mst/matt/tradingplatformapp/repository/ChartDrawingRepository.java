package com.mst.matt.tradingplatformapp.repository;

import com.mst.matt.tradingplatformapp.model.ChartDrawing;
import com.mst.matt.tradingplatformapp.model.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChartDrawingRepository extends JpaRepository<ChartDrawing, Long> {

    /**
     * Load the "active" (no named layout) drawings for a symbol/timeframe.
     *
     * <p>Uses {@code JOIN FETCH d.profile} so Hibernate eagerly hydrates the
     * {@code UserProfile} proxy in a single SQL query, preventing
     * {@code LazyInitializationException} after the session is closed.
     */
    @Query("SELECT d FROM ChartDrawing d JOIN FETCH d.profile " +
           "WHERE d.profile = :profile AND d.symbol = :symbol " +
           "AND d.timeframe = :timeframe AND d.layoutName IS NULL " +
           "ORDER BY d.createdAtEpoch ASC")
    List<ChartDrawing> findByProfileAndSymbolAndTimeframeAndLayoutNameIsNullOrderByCreatedAtEpochAsc(
            @Param("profile") UserProfile profile,
            @Param("symbol")  String symbol,
            @Param("timeframe") String timeframe);

    /**
     * Load drawings that belong to a specific named layout.
     *
     * <p>Same JOIN FETCH pattern — eagerly loads {@code profile} proxy.
     */
    @Query("SELECT d FROM ChartDrawing d JOIN FETCH d.profile " +
           "WHERE d.profile = :profile AND d.symbol = :symbol " +
           "AND d.timeframe = :timeframe AND d.layoutName = :layoutName " +
           "ORDER BY d.createdAtEpoch ASC")
    List<ChartDrawing> findByProfileAndSymbolAndTimeframeAndLayoutNameOrderByCreatedAtEpochAsc(
            @Param("profile")    UserProfile profile,
            @Param("symbol")     String symbol,
            @Param("timeframe")  String timeframe,
            @Param("layoutName") String layoutName);

    /** Legacy query — kept for migration / backwards compat. */
    List<ChartDrawing> findByProfileAndSymbolAndTimeframeOrderByCreatedAtEpochAsc(
            UserProfile profile, String symbol, String timeframe);

    void deleteByProfileAndSymbolAndTimeframe(UserProfile profile, String symbol, String timeframe);

    void deleteByProfileAndSymbolAndTimeframeAndLayoutName(
            UserProfile profile, String symbol, String timeframe, String layoutName);
}
