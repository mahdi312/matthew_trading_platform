package com.mst.matt.marketservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * {@code @EnableCaching} activates the Caffeine-backed cache layer (Step 5.3)
 * declared via {@code spring.cache.*} in application.yml and consumed by
 * {@code BitUnixMarketDataProvider}'s {@code @Cacheable} methods.
 */
@SpringBootApplication
@EnableCaching
public class MarketServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarketServiceApplication.class, args);
    }

}
