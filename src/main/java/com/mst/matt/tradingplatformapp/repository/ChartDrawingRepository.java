package com.mst.matt.tradingplatformapp.repository;

import com.mst.matt.tradingplatformapp.model.ChartDrawing;
import com.mst.matt.tradingplatformapp.model.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChartDrawingRepository extends JpaRepository<ChartDrawing, Long> {

    List<ChartDrawing> findByProfileAndSymbolAndTimeframeOrderByCreatedAtAsc(
            UserProfile profile, String symbol, String timeframe);

    void deleteByProfileAndSymbolAndTimeframe(UserProfile profile, String symbol, String timeframe);
}
