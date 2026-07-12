package com.mst.matt.tradingplatformapp;

import com.mst.matt.tradingplatformapp.config.JavaFxApplication;
import javafx.application.Application;
import com.mst.matt.tradingplatformapp.config.MarketApiProperties;
import com.mst.matt.tradingplatformapp.config.MarketDataProperties;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Desktop launcher: starts JavaFX, which bootstraps the Spring context in {@link JavaFxApplication#init()}.
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync   // P1 (LOG-FIX): enables @Async on ProfilePersistenceService so JavaFX UI threads never block on JPA commits
@EnableConfigurationProperties({MarketApiProperties.class, MarketDataProperties.class})
public class TradingPlatformAppApplication {

    public static void main(String[] args) {
        Application.launch(JavaFxApplication.class, args);
    }
}
