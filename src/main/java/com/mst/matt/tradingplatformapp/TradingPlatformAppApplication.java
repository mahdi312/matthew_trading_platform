package com.mst.matt.tradingplatformapp;

import com.mst.matt.tradingplatformapp.config.JavaFxApplication;
import javafx.application.Application;
import com.mst.matt.tradingplatformapp.config.MarketApiProperties;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Desktop launcher: starts JavaFX, which bootstraps the Spring context in {@link JavaFxApplication#init()}.
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(MarketApiProperties.class)
public class TradingPlatformAppApplication {

    public static void main(String[] args) {
        Application.launch(JavaFxApplication.class, args);
    }
}
