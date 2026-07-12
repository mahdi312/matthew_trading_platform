package com.mst.matt.aiservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AI Service — Spring Boot entry point.
 * Hosts AiAnalysisProvider implementations (AI tab + Analysis tab AI insights
 * + Trade Journal critique). Step 4.5: boots with NoOpAiAnalysisProvider.
 */
@SpringBootApplication
public class AiServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}
