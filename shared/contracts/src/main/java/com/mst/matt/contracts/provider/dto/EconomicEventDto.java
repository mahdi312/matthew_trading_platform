package com.mst.matt.contracts.provider.dto;

import com.mst.matt.contracts.enums.AssetClass;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Singular;

import java.time.Instant;
import java.util.List;

/**
 * Normalized macroeconomic calendar event.
 *
 * <p>Returned by
 * {@link com.mst.matt.contracts.provider.calendar.EconomicCalendarProvider}.
 * No consumer (Economic Calendar widget, AI tab) should ever see a
 * provider-specific response shape.</p>
 *
 * <h3>Examples</h3>
 * <ul>
 *   <li>US Non-Farm Payrolls (critical for USD/FOREX/STOCK)</li>
 *   <li>Federal Reserve interest rate decision</li>
 *   <li>CPI / inflation release</li>
 *   <li>GDP growth report</li>
 *   <li>Central bank press conference</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EconomicEventDto {

    // ── Identity ──────────────────────────────────────────────────────────────

    /**
     * Provider-assigned unique identifier for this event.
     * Used for deduplication when merging from multiple providers.
     */
    private String eventId;

    /** Name of the data provider that sourced this event. */
    private String providerName;

    // ── Event metadata ────────────────────────────────────────────────────────

    /**
     * Short event title (e.g., "US Non-Farm Payrolls",
     * "FOMC Interest Rate Decision", "ECB Press Conference").
     */
    private String title;

    /**
     * Longer description of what the event measures and why it matters.
     */
    private String description;

    /**
     * ISO-3166 alpha-2 country code this event belongs to
     * (e.g., "US", "EU", "JP", "GB").
     */
    private String country;

    /**
     * Event category (e.g., "CENTRAL_BANK", "EMPLOYMENT", "INFLATION",
     * "GDP", "TRADE", "RETAIL_SALES", "HOUSING", "EARNINGS").
     */
    private String category;

    // ── Timing ────────────────────────────────────────────────────────────────

    /**
     * Scheduled release timestamp in UTC.
     * May be approximate (time-of-day unknown) — check {@link #isTimeTentative}.
     */
    private Instant scheduledAt;

    /**
     * Actual release timestamp; {@code null} if the event has not yet occurred.
     */
    private Instant actualReleasedAt;

    /**
     * {@code true} if only the date (not the time) is known — callers
     * should display the date without a specific time.
     */
    private boolean isTimeTentative;

    // ── Impact ────────────────────────────────────────────────────────────────

    /**
     * Expected market impact level (e.g., "HIGH", "MEDIUM", "LOW").
     * "HIGH" events (e.g., NFP, FOMC) typically cause major price moves.
     */
    private String impactLevel;

    /**
     * Asset classes most affected by this event.
     * e.g., NFP → [FOREX, STOCK]; FOMC → [FOREX, STOCK, CRYPTO].
     */
    @Singular
    private List<AssetClass> affectedAssetClasses;

    /**
     * Currency symbols most affected (ISO-4217 codes, e.g., ["USD", "EUR"]).
     */
    @Singular
    private List<String> affectedCurrencies;

    // ── Values ────────────────────────────────────────────────────────────────

    /**
     * Consensus / forecast value for this release, as a string to preserve
     * units (e.g., "200K", "3.1%", "2.5%", "-0.2").
     */
    private String forecast;

    /**
     * Previous release value (the prior period's actual result).
     */
    private String previous;

    /**
     * Actual released value; {@code null} if the event has not yet occurred.
     */
    private String actual;

    /**
     * Direction of the actual vs forecast (e.g., "BETTER_THAN_EXPECTED",
     * "WORSE_THAN_EXPECTED", "IN_LINE"); {@code null} if not yet released
     * or if comparison is not applicable.
     */
    private String surprise;

    // ── Flags ─────────────────────────────────────────────────────────────────

    /** {@code true} if this is a recurring event (e.g., monthly NFP). */
    private boolean isRecurring;

    /** {@code true} if the event has been released (actual value is available). */
    private boolean isReleased;

    /**
     * {@code true} if this event was revised after initial release.
     * The {@link #actual} value will reflect the revision.
     */
    private boolean isRevised;
}
