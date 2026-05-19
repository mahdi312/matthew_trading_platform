package com.mst.matt.tradingplatformapp.service.price;

/**
 * Identifies a market-data backend (matches Postman collection providers).
 */
public enum MarketDataProvider {
    AUTO("Auto (best available)"),
    BINANCE("Binance"),
    COINGECKO("CoinGecko"),
    YAHOO("Yahoo Finance"),
    FRANKFURTER("Frankfurter (ECB)"),
    ALPHA_VANTAGE("Alpha Vantage"),
    POLYGON("Polygon.io"),
    FINNHUB("Finnhub"),
    TWELVE_DATA("Twelve Data"),
    MARKETSTACK("Marketstack"),
    FIXER("Fixer.io"),
    FREE_CURRENCY_API("FreeCurrencyAPI"),
    OPEN_EXCHANGE_RATES("Open Exchange Rates"),
    EXCHANGE_RATE_API("ExchangeRate-API"),
    CURRENCY_LAYER("CurrencyLayer");

    private final String label;

    MarketDataProvider(String label) {
        this.label = label;
    }

    public String getLabel() { return label; }

    public static MarketDataProvider fromString(String value) {
        if (value == null || value.isBlank()) return AUTO;
        try {
            return MarketDataProvider.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return AUTO;
        }
    }
}
