package com.mst.matt.tradingplatformapp.service.marketdata;

import com.mst.matt.tradingplatformapp.service.price.MarketDataProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketDataTableNameUtilTest {

    @Test
    void buildsExpectedTableName() {
        assertEquals("ETHUSDT_BINANCE_1h",
                MarketDataTableNameUtil.buildTableName("ETHUSDT", MarketDataProvider.BINANCE, "1h"));
    }

    @Test
    void sanitizesSpecialCharacters() {
        assertEquals("EURUSD_X_YAHOO_1d",
                MarketDataTableNameUtil.buildTableName("EURUSD=X", MarketDataProvider.YAHOO, "1d"));
    }
}
