package com.mst.matt.tradingplatformapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * API keys aligned with {@code Free_Trading_APIs.postman_collection.json} variables.
 * Providers with blank keys are skipped at runtime.
 */
@Component
@ConfigurationProperties(prefix = "api")
public class MarketApiProperties {

    private String alphavantageKey = "";
    private String coingeckoKey = "";
    private String polygonKey = "";
    private String marketstackKey = "";
    private String finnhubKey = "";
    private String fredKey = "";
    private String coinmarketcapKey = "";
    private String openexchangeratesKey = "";
    private String exchangerateapiKey = "";
    private String freecurrencyapiKey = "";
    private String currencylayerKey = "";
    private String fixerioKey = "";
    private String twelvedataKey = "";

    public boolean hasAlphavantageKey() { return hasKey(alphavantageKey); }
    public boolean hasCoingeckoKey()    { return hasKey(coingeckoKey); }
    public boolean hasPolygonKey()      { return hasKey(polygonKey); }
    public boolean hasMarketstackKey()  { return hasKey(marketstackKey); }
    public boolean hasFinnhubKey()      { return hasKey(finnhubKey); }
    public boolean hasFredKey()         { return hasKey(fredKey); }
    public boolean hasCoinmarketcapKey(){ return hasKey(coinmarketcapKey); }
    public boolean hasOpenexchangeratesKey() { return hasKey(openexchangeratesKey); }
    public boolean hasExchangerateapiKey()   { return hasKey(exchangerateapiKey); }
    public boolean hasFreecurrencyapiKey() { return hasKey(freecurrencyapiKey); }
    public boolean hasCurrencylayerKey()     { return hasKey(currencylayerKey); }
    public boolean hasFixerioKey()           { return hasKey(fixerioKey); }
    public boolean hasTwelvedataKey()        { return hasKey(twelvedataKey); }

    private static boolean hasKey(String k) {
        return k != null && !k.isBlank();
    }

    public String getAlphavantageKey() { return alphavantageKey; }
    public void setAlphavantageKey(String alphavantageKey) { this.alphavantageKey = alphavantageKey; }
    public String getCoingeckoKey() { return coingeckoKey; }
    public void setCoingeckoKey(String coingeckoKey) { this.coingeckoKey = coingeckoKey; }
    public String getPolygonKey() { return polygonKey; }
    public void setPolygonKey(String polygonKey) { this.polygonKey = polygonKey; }
    public String getMarketstackKey() { return marketstackKey; }
    public void setMarketstackKey(String marketstackKey) { this.marketstackKey = marketstackKey; }
    public String getFinnhubKey() { return finnhubKey; }
    public void setFinnhubKey(String finnhubKey) { this.finnhubKey = finnhubKey; }
    public String getFredKey() { return fredKey; }
    public void setFredKey(String fredKey) { this.fredKey = fredKey; }
    public String getCoinmarketcapKey() { return coinmarketcapKey; }
    public void setCoinmarketcapKey(String coinmarketcapKey) { this.coinmarketcapKey = coinmarketcapKey; }
    public String getOpenexchangeratesKey() { return openexchangeratesKey; }
    public void setOpenexchangeratesKey(String openexchangeratesKey) { this.openexchangeratesKey = openexchangeratesKey; }
    public String getExchangerateapiKey() { return exchangerateapiKey; }
    public void setExchangerateapiKey(String exchangerateapiKey) { this.exchangerateapiKey = exchangerateapiKey; }
    public String getFreecurrencyapiKey() { return freecurrencyapiKey; }
    public void setFreecurrencyapiKey(String freecurrencyapiKey) { this.freecurrencyapiKey = freecurrencyapiKey; }
    public String getCurrencylayerKey() { return currencylayerKey; }
    public void setCurrencylayerKey(String currencylayerKey) { this.currencylayerKey = currencylayerKey; }
    public String getFixerioKey() { return fixerioKey; }
    public void setFixerioKey(String fixerioKey) { this.fixerioKey = fixerioKey; }
    public String getTwelvedataKey() { return twelvedataKey; }
    public void setTwelvedataKey(String twelvedataKey) { this.twelvedataKey = twelvedataKey; }
}
