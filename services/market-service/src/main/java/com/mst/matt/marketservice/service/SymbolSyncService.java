package com.mst.matt.marketservice.service;

import com.mst.matt.marketservice.model.SymbolEntry;
import com.mst.matt.marketservice.repository.SymbolEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/**
 * Symbol catalogue sync service.
 * Ported from desktop monolith's {@code SymbolSyncService}.
 * Phase 2: only the storage/query half is ported — actual provider calls
 * (Binance /exchangeInfo, CoinGecko /coins/list, Finnhub /stock/symbol) are
 * NOT implemented in this step. They will be wired in Phase 3 provider implementations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SymbolSyncService {

    private final SymbolEntryRepository symbolEntryRepository;

    @Transactional(readOnly = true)
    public List<SymbolEntry> search(String query) {
        if (query == null || query.isBlank()) return List.of();
        return symbolEntryRepository.searchAll(query.trim());
    }

    @Transactional(readOnly = true)
    public List<SymbolEntry> searchByType(SymbolEntry.AssetType type, String query) {
        if (query == null || query.isBlank()) return symbolEntryRepository.findByAssetTypeOrderBySymbolAsc(type);
        return symbolEntryRepository.search(type, query.trim());
    }

    @Transactional(readOnly = true)
    public List<SymbolEntry> findAll() { return symbolEntryRepository.findAll(); }

    public SymbolEntry save(SymbolEntry entry) { return symbolEntryRepository.save(entry); }

    public void saveAll(List<SymbolEntry> entries) { symbolEntryRepository.saveAll(entries); }

    /** Returns count per asset type for dashboard. */
    @Transactional(readOnly = true)
    public long count(SymbolEntry.AssetType type) { return symbolEntryRepository.countByAssetType(type); }
}
