package com.mst.matt.tradingplatformapp.repository;

import com.mst.matt.tradingplatformapp.model.SymbolEntry;
import com.mst.matt.tradingplatformapp.model.SymbolEntry.AssetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SymbolEntryRepository extends JpaRepository<SymbolEntry, Long> {

    List<SymbolEntry> findByAssetTypeOrderBySymbolAsc(AssetType type);

    @Query("SELECT s FROM SymbolEntry s WHERE s.assetType = :type " +
           "AND (UPPER(s.symbol) LIKE UPPER(CONCAT('%',:q,'%')) " +
           "OR UPPER(s.name) LIKE UPPER(CONCAT('%',:q,'%'))) " +
           "ORDER BY s.symbol")
    List<SymbolEntry> search(@Param("type") AssetType type, @Param("q") String query);

    @Query("SELECT s FROM SymbolEntry s WHERE " +
           "(UPPER(s.symbol) LIKE UPPER(CONCAT('%',:q,'%')) " +
           "OR UPPER(s.name) LIKE UPPER(CONCAT('%',:q,'%'))) " +
           "ORDER BY s.assetType, s.symbol")
    List<SymbolEntry> searchAll(@Param("q") String query);

    long countByAssetType(AssetType type);

    boolean existsByAssetTypeAndSymbol(AssetType type, String symbol);
}
