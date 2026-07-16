package com.mst.matt.tradingplatformapp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.market-data")
public class MarketDataProperties {

    private DynamicTables dynamicTables = new DynamicTables();
    private Sync sync = new Sync();

    @Data
    public static class DynamicTables {
        /** When true, OHLCV is stored in per-symbol/provider/timeframe tables. */
        private boolean enabled = false;
    }

    @Data
    public static class Sync {
        private boolean enabled = true;
        private int defaultBars = 200;
        /** Fetch from API on first read when the table is empty. */
        private boolean bootstrapOnMiss = true;
    }
}
