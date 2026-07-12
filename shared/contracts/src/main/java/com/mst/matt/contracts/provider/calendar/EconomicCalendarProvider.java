package com.mst.matt.contracts.provider.calendar;

import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.dto.EconomicEventDto;

import java.time.Instant;
import java.util.List;

/**
 * Unified contract for macroeconomic event calendar data.
 *
 * <h3>Coverage</h3>
 * <p>Returns normalized {@link EconomicEventDto} objects. Economic events are
 * critical for forex traders, highly relevant to stocks and crypto, and
 * feed the platform's Economic Calendar widget and AI analysis context.</p>
 *
 * <h3>Supported providers (future implementations)</h3>
 * <ul>
 *   <li><b>Finnhub</b> — economic calendar via earnings/events API</li>
 *   <li><b>Twelve Data</b> — economic events endpoint</li>
 *   <li><b>ForexFactory</b> — scraped economic calendar (forex-focused)</li>
 *   <li><b>Investing.com</b> — comprehensive macro event calendar</li>
 * </ul>
 *
 * <h3>Implementation home</h3>
 * <p>Concrete implementations live in {@code reference-data-service}.
 * Only the no-op mock ({@code NoOpEconomicCalendarProvider}) is wired in
 * Step 4.5.</p>
 *
 * <h3>Registry</h3>
 * <p>Implementations are registered in the
 * {@link com.mst.matt.contracts.provider.registry.ProviderRegistry} keyed by
 * {@code (AssetClass, providerName)}.
 * All asset classes map to the same calendar, but filtering by
 * {@link AssetClass} is supported via the query methods.</p>
 */
public interface EconomicCalendarProvider {

    /**
     * Returns the logical provider name (e.g., "FINNHUB", "TWELVE_DATA",
     * "FOREX_FACTORY").
     */
    String providerName();

    /**
     * Returns the asset classes whose events this provider covers.
     * Economic calendar providers typically cover all asset classes since
     * macro events affect every market.
     *
     * @return non-empty list of supported asset classes
     */
    List<AssetClass> supportedAssetClasses();

    // ── Upcoming / scheduled events ───────────────────────────────────────────

    /**
     * Fetch upcoming scheduled economic events within a time range.
     *
     * @param from        range start (inclusive)
     * @param to          range end (inclusive)
     * @param impactLevel optional impact filter: "HIGH", "MEDIUM", "LOW", or
     *                    {@code null} to return all impact levels
     * @return list of {@link EconomicEventDto} ordered by
     *         {@link EconomicEventDto#getScheduledAt()} ascending
     */
    List<EconomicEventDto> getUpcomingEvents(Instant from, Instant to, String impactLevel);

    /**
     * Fetch all high-impact events in the next {@code days} days.
     *
     * @param days number of days ahead to look (e.g., 7)
     * @return list of high-impact {@link EconomicEventDto} ordered chronologically
     */
    List<EconomicEventDto> getHighImpactEvents(int days);

    // ── Historical events ─────────────────────────────────────────────────────

    /**
     * Fetch historical events that have already been released (actual values
     * available) within a time range.
     *
     * @param from range start (inclusive)
     * @param to   range end (inclusive)
     * @return list of released {@link EconomicEventDto} ordered by
     *         {@link EconomicEventDto#getActualReleasedAt()} descending
     */
    List<EconomicEventDto> getHistoricalEvents(Instant from, Instant to);

    // ── Filtered queries ──────────────────────────────────────────────────────

    /**
     * Fetch events for a specific country.
     *
     * @param countryCode ISO-3166 alpha-2 country code (e.g., "US", "EU", "JP")
     * @param from        range start (inclusive)
     * @param to          range end (inclusive)
     * @return list of {@link EconomicEventDto} for the specified country
     */
    List<EconomicEventDto> getEventsByCountry(String countryCode, Instant from, Instant to);

    /**
     * Fetch events that are most relevant to the given asset class
     * (based on {@link EconomicEventDto#getAffectedAssetClasses()}).
     *
     * @param assetClass target asset class
     * @param from       range start (inclusive)
     * @param to         range end (inclusive)
     * @return list of {@link EconomicEventDto} relevant to this asset class
     */
    List<EconomicEventDto> getEventsByAssetClass(AssetClass assetClass, Instant from, Instant to);

    /**
     * Fetch the next scheduled event of a specific category (e.g., next
     * FOMC meeting, next NFP release).
     *
     * @param category event category string (e.g., "CENTRAL_BANK", "EMPLOYMENT")
     * @param country  ISO-3166 alpha-2 country code; {@code null} for global
     * @return list of matching upcoming {@link EconomicEventDto} ordered
     *         chronologically (closest next first); may be empty
     */
    List<EconomicEventDto> getNextEventsByCategory(String category, String country);
}
