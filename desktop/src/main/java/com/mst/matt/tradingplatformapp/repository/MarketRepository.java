package com.mst.matt.tradingplatformapp.repository;

import com.mst.matt.tradingplatformapp.model.Market;
import com.mst.matt.tradingplatformapp.model.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MarketRepository extends JpaRepository<Market, Long> {
    Optional<Market> findByCode(String code);
    Optional<Market> findByAssetTypeAndExchangeName(Trade.AssetType assetType, String exchangeName);
}
