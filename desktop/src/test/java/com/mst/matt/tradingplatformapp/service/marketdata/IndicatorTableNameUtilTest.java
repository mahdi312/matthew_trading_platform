package com.mst.matt.tradingplatformapp.service.marketdata;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IndicatorTableNameUtilTest {

    @Test
    void buildsSanitizedIndicatorTableName() {
        assertEquals("BTC_USDT_MACD_1H",
                IndicatorTableNameUtil.tableName("btc/usdt", IndicatorTableNameUtil.SeriesType.MACD, "1h"));
    }

    @Test
    void normalizesTimeframeAndKeepsSeriesType() {
        assertEquals("EURUSD_X_RSI_1W",
                IndicatorTableNameUtil.tableName("EURUSD=X", IndicatorTableNameUtil.SeriesType.RSI, "1w"));
    }
}
