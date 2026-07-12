package com.mst.matt.tradingplatformapp.model;

/**
 * Controls whether the application fetches live data from external APIs
 * or relies on cached data stored in the database.
 *
 * <p>Saved per-profile in {@code AppSettingsService} (key: {@code data.fetch.mode}).
 *
 * <ul>
 *   <li>{@link #FULL_ONLINE}       – Always call the API; DB is only a write-through cache.</li>
 *   <li>{@link #OFFLINE_ON_FAIL}   – Try the API first (10-second timeout); fall back to DB
 *                                    cache when the API is unreachable. <strong>Default.</strong></li>
 *   <li>{@link #OFFLINE_ONLY}      – Never call any API; read exclusively from the DB cache.</li>
 * </ul>
 */
public enum DataFetchMode {

    /**
     * Always fetch from the API.
     * The database is used only as a write-through cache (data is stored after
     * every successful API call, but never read back as a fallback).
     */
    FULL_ONLINE("Full Online", "Always fetch from API. Never use cached data as fallback."),

    /**
     * Try the API first; fall back to the database cache when the API is
     * unreachable or times out after 10 seconds.
     * This is the <strong>recommended default</strong> setting.
     */
    OFFLINE_ON_FAIL("Offline when API unreachable (Recommended)",
            "Use API if available; fall back to database only on timeout/failure. (Default)"),

    /**
     * Never call any external API.
     * All data is read exclusively from the provider-specific database table.
     * If no cached data exists a descriptive message is shown.
     */
    OFFLINE_ONLY("Offline Only",
            "Never call any API. Only use cached data from the database.");

    /** Human-readable label shown in the Settings UI. */
    public final String label;

    /** Short description shown as a tooltip/hint in the Settings UI. */
    public final String description;

    DataFetchMode(String label, String description) {
        this.label       = label;
        this.description = description;
    }

    /**
     * Safe parse: returns the enum constant matching {@code value} (case-insensitive),
     * or {@link #OFFLINE_ON_FAIL} as the default when the value is unrecognised.
     */
    public static DataFetchMode fromString(String value) {
        if (value == null || value.isBlank()) return OFFLINE_ON_FAIL;
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return OFFLINE_ON_FAIL;
        }
    }

    @Override
    public String toString() { return label; }
}
