package com.mst.matt.contracts.provider.mock;

import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.dto.CompanyFundamentalsDto;
import com.mst.matt.contracts.provider.dto.CryptoTokenomicsDto;
import com.mst.matt.contracts.provider.dto.ForexMacroIndicatorsDto;
import com.mst.matt.contracts.provider.fundamentals.FundamentalsProvider;

import java.util.List;
import java.util.Optional;

/**
 * No-op mock implementation of {@link FundamentalsProvider}.
 * Returns empty data so that the registry compiles and is testable.
 * DO NOT use in production.
 */
public class NoOpFundamentalsProvider implements FundamentalsProvider {
    public static final String PROVIDER_NAME = "NOOP";

    @Override public String providerName() { return PROVIDER_NAME; }
    @Override public List<AssetClass> supportedAssetClasses() { return List.of(AssetClass.values()); }
    @Override public Optional<CompanyFundamentalsDto> getCompanyFundamentals(String symbol) { return Optional.empty(); }
    @Override public Optional<CryptoTokenomicsDto> getCryptoTokenomics(String symbol) { return Optional.empty(); }
    @Override public Optional<ForexMacroIndicatorsDto> getForexMacroIndicators(String symbol) { return Optional.empty(); }
}
