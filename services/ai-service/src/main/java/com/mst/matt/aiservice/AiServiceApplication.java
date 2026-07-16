package com.mst.matt.aiservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * AI Service — Spring Boot entry point.
 * Hosts AiAnalysisProvider implementations (AI tab + Analysis tab AI insights
 * + Trade Journal critique).
 */
@SpringBootApplication
@EnableFeignClients
@EnableConfigurationProperties
public class AiServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}
