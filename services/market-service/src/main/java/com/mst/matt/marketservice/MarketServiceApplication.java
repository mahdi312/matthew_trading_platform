package com.mst.matt.marketservice;

import com.mst.matt.marketservice.config.MarketProviderProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @EnableCaching} activates the Caffeine-backed cache layer (Step 5.3).
 * {@code @EnableScheduling} activates the candle-aggregation + market-data-sync schedulers.
 * {@code @EnableAsync} activates the async sync path in {@code MarketDataSyncService}.
 * {@code @EnableConfigurationProperties} registers {@link MarketProviderProperties}
 * so that the {@code api.*} prefix is bound at startup.
 */
@SpringBootApplication
@EnableCaching
@EnableScheduling
@EnableAsync
@EnableConfigurationProperties(MarketProviderProperties.class)
public class MarketServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarketServiceApplication.class, args);
    }

}
