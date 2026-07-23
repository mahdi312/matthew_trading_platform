package com.mst.matt.marketservice.service;

import com.mst.matt.marketservice.model.*;
import com.mst.matt.marketservice.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

/**
 * CRUD for market reference data (Market, Share, Company).
 * Ported from desktop monolith's {@code MarketReferenceDataService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MarketReferenceDataService {
    private final MarketRepository marketRepository;
    private final ShareRepository shareRepository;
    private final CompanyRepository companyRepository;

    public Market saveMarket(Market market) { return marketRepository.save(market); }
    @Transactional(readOnly=true)
    public Optional<Market> findMarketByCode(String code) { return marketRepository.findByCode(code); }
    @Transactional(readOnly=true)
    public List<Market> findAllMarkets() { return marketRepository.findAll(); }

    public Share saveShare(Share share) { return shareRepository.save(share); }
    @Transactional(readOnly=true)
    public Optional<Share> findShare(String symbol, Long marketId) { return shareRepository.findBySymbolAndMarketId(symbol, marketId); }

    public Company saveCompany(Company company) { return companyRepository.save(company); }
    @Transactional(readOnly=true)
    public Optional<Company> findCompanyByTicker(String ticker) { return companyRepository.findByTicker(ticker); }
}
