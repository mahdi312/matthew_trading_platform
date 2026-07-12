package com.mst.matt.tradingplatformapp.service.fundamental;

public enum FundamentalDataProvider {
    AUTO("Auto (best available)"),
    ALPHA_VANTAGE("Alpha Vantage"),
    FINNHUB("Finnhub"),
    POLYGON("Polygon.io");

    private final String label;

    FundamentalDataProvider(String label) {
        this.label = label;
    }

    public String getLabel() { return label; }

    public static FundamentalDataProvider fromString(String value) {
        if (value == null || value.isBlank()) return AUTO;
        try {
            return FundamentalDataProvider.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return AUTO;
        }
    }
}
