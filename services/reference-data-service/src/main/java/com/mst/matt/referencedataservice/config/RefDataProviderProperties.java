package com.mst.matt.referencedataservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * API keys and base-URL configuration for all reference-data providers.
 *
 * <p>All keys default to {@code ""} (empty string) when the environment
 * variable is not set, enabling the service to boot without any keys
 * (providers with missing keys return empty results via their
 * {@code has*()} guard methods).</p>
 *
 * <h3>Environment variables</h3>
 * <pre>
 *   ALPHAVANTAGE_KEY   → api.alphavantage-key
 *   FINNHUB_KEY        → api.finnhub-key
 *   TWELVEDATA_KEY     → api.twelvedata-key
 *   COINMARKETCAP_KEY  → api.coinmarketcap-key
 *   COINGECKO_KEY      → api.coingecko-key
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "api")
public class RefDataProviderProperties {

    private String alphavantageKey   = "";
    private String finnhubKey        = "";
    private String twelvedataKey     = "";
    private String coinmarketcapKey  = "";
    private String coingeckoKey      = "";

    public boolean hasAlphavantageKey()  { return alphavantageKey  != null && !alphavantageKey.isBlank();  }
    public boolean hasFinnhubKey()       { return finnhubKey       != null && !finnhubKey.isBlank();       }
    public boolean hasTwelvedataKey()    { return twelvedataKey    != null && !twelvedataKey.isBlank();    }
    public boolean hasCoinmarketcapKey() { return coinmarketcapKey != null && !coinmarketcapKey.isBlank(); }
    public boolean hasCoinGeckoKey()     { return coingeckoKey     != null && !coingeckoKey.isBlank();     }
}
