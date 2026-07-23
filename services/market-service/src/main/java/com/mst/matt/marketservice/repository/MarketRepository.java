package com.mst.matt.marketservice.repository;
import com.mst.matt.marketservice.model.AssetType;
import com.mst.matt.marketservice.model.Market;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
@Repository
public interface MarketRepository extends JpaRepository<Market, Long> {
    Optional<Market> findByCode(String code);
    Optional<Market> findByAssetTypeAndExchangeName(AssetType assetType, String exchangeName);
}
