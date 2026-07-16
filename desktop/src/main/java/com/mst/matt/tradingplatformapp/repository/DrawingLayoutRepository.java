package com.mst.matt.tradingplatformapp.repository;

import com.mst.matt.tradingplatformapp.model.DrawingLayout;
import com.mst.matt.tradingplatformapp.model.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DrawingLayoutRepository extends JpaRepository<DrawingLayout, Long> {

    List<DrawingLayout> findByProfileAndSymbolAndTimeframeOrderBySavedAtEpochDesc(
            UserProfile profile, String symbol, String timeframe);

    Optional<DrawingLayout> findByProfileAndSymbolAndTimeframeAndName(
            UserProfile profile, String symbol, String timeframe, String name);

    void deleteByProfileAndSymbolAndTimeframeAndName(
            UserProfile profile, String symbol, String timeframe, String name);
}
