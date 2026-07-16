package com.mst.matt.contracts.provider.mock;

import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.calendar.EconomicCalendarProvider;
import com.mst.matt.contracts.provider.dto.EconomicEventDto;

import java.time.Instant;
import java.util.List;

/**
 * No-op mock implementation of {@link EconomicCalendarProvider}.
 * Returns empty data so the registry compiles and is testable.
 * DO NOT use in production.
 */
public class NoOpEconomicCalendarProvider implements EconomicCalendarProvider {
    public static final String PROVIDER_NAME = "NOOP";

    @Override public String providerName() { return PROVIDER_NAME; }
    @Override public List<AssetClass> supportedAssetClasses() { return List.of(AssetClass.values()); }
    @Override public List<EconomicEventDto> getUpcomingEvents(Instant from, Instant to, String impactLevel) { return List.of(); }
    @Override public List<EconomicEventDto> getHighImpactEvents(int days) { return List.of(); }
    @Override public List<EconomicEventDto> getHistoricalEvents(Instant from, Instant to) { return List.of(); }
    @Override public List<EconomicEventDto> getEventsByCountry(String countryCode, Instant from, Instant to) { return List.of(); }
    @Override public List<EconomicEventDto> getEventsByAssetClass(AssetClass assetClass, Instant from, Instant to) { return List.of(); }
    @Override public List<EconomicEventDto> getNextEventsByCategory(String category, String country) { return List.of(); }
}
