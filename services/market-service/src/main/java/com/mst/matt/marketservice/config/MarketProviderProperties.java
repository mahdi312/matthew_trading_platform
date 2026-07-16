package com.mst.matt.marketservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * API key configuration for all external market-data providers.
 *
 * <p>Bound to the {@code api.*} prefix via Spring's relaxed binding — all
 * keys default to {@code ""} so that missing keys result in the provider's
 * {@code isEnabled()} returning {@code false} (and being skipped by the
 * {@link com.mst.matt.marketservice.registry.MarketOhlcvProviderRegistry})
 * rather than throwing at startup.</p>
 *
 * <p>Do NOT annotate with {@code @Component} — registration is done via
 * {@code @EnableConfigurationProperties(MarketProviderProperties.class)} on
 * {@link com.mst.matt.marketservice.MarketServiceApplication} to avoid
 * double-bean registration.</p>
 *
 * <p>Property pattern: {@code ${ENV_VAR:}} — Spring reads the env-var if set,
 * falls back to the empty string so no API key is ever hard-coded.</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "api")
public class MarketProviderProperties {

    // ── Crypto ────────────────────────────────────────────────────────────────
    private String alphavantageKey   = "";
    private String coingeckoKey      = "";
    private String coinmarketcapKey  = "";

    // ── Stock ─────────────────────────────────────────────────────────────────
    private String polygonKey        = "";
    private String marketstackKey    = "";
    private String finnhubKey        = "";
    private String twelvedataKey     = "";
    // Yahoo Finance — no key required for public endpoints
    // (included in application.yml as api.yahoo.base-url only)

    // ── Forex ─────────────────────────────────────────────────────────────────
    private String fixerioKey           = "";
    private String freecurrencyapiKey   = "";
    private String currencylayerKey     = "";
    private String exchangerateapiKey   = "";
    private String openexchangeratesKey = "";
    // Frankfurter — no key required (free ECB-sourced endpoint)

    // ── Presence checks (used by each provider's isEnabled()) ────────────────

    public boolean hasAlphavantageKey()      { return hasKey(alphavantageKey); }
    public boolean hasCoingeckoKey()         { return hasKey(coingeckoKey); }
    public boolean hasCoinmarketcapKey()     { return hasKey(coinmarketcapKey); }
    public boolean hasPolygonKey()           { return hasKey(polygonKey); }
    public boolean hasMarketstackKey()       { return hasKey(marketstackKey); }
    public boolean hasFinnhubKey()           { return hasKey(finnhubKey); }
    public boolean hasTwelvedataKey()        { return hasKey(twelvedataKey); }
    public boolean hasFixerioKey()           { return hasKey(fixerioKey); }
    public boolean hasFreecurrencyapiKey()   { return hasKey(freecurrencyapiKey); }
    public boolean hasCurrencylayerKey()     { return hasKey(currencylayerKey); }
    public boolean hasExchangerateapiKey()   { return hasKey(exchangerateapiKey); }
    public boolean hasOpenexchangeratesKey() { return hasKey(openexchangeratesKey); }

    private static boolean hasKey(String k) {
        return k != null && !k.isBlank();
    }
}
