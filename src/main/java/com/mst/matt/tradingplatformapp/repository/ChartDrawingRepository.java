package com.mst.matt.tradingplatformapp.repository;

import com.mst.matt.tradingplatformapp.model.ChartDrawing;
import com.mst.matt.tradingplatformapp.model.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChartDrawingRepository extends JpaRepository<ChartDrawing, Long> {

    /** Load the "active" (no named layout) drawings for a symbol/timeframe. */
    List<ChartDrawing> findByProfileAndSymbolAndTimeframeAndLayoutNameIsNullOrderByCreatedAtEpochAsc(
            UserProfile profile, String symbol, String timeframe);

    /** Load drawings that belong to a specific named layout. */
    List<ChartDrawing> findByProfileAndSymbolAndTimeframeAndLayoutNameOrderByCreatedAtEpochAsc(
            UserProfile profile, String symbol, String timeframe, String layoutName);

    /** Legacy query — kept for migration / backwards compat. */
    List<ChartDrawing> findByProfileAndSymbolAndTimeframeOrderByCreatedAtEpochAsc(
            UserProfile profile, String symbol, String timeframe);

    void deleteByProfileAndSymbolAndTimeframe(UserProfile profile, String symbol, String timeframe);

    void deleteByProfileAndSymbolAndTimeframeAndLayoutName(
            UserProfile profile, String symbol, String timeframe, String layoutName);
}
