package com.matthew.desktop.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * Central API client service for all microservice communication
 * 
 * This service handles:
 * - Authentication token management
 * - Request/response serialization
 * - Error handling and retry logic
 * - API endpoint routing
 */
@Slf4j
@Service
public class ApiClientService {
    
    @Value("${api.gateway.url:http://localhost:8080}")
    private String gatewayUrl;
    
    @Value("${api.identity.url:http://localhost:9898}")
    private String identityServiceUrl;
    
    @Value("${api.market.url:http://localhost:9001}")
    private String marketServiceUrl;
    
    @Value("${api.trading.url:http://localhost:9002}")
    private String tradingServiceUrl;
    
    private final RestTemplate restTemplate;
    private String authToken;

    public ApiClientService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Set authentication token for subsequent API calls
     */
    public void setAuthToken(String token) {
        this.authToken = token;
        log.info("Authentication token updated");
    }

    /**
     * Create HTTP headers with authentication
     */
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (authToken != null && !authToken.isEmpty()) {
            headers.setBearerAuth(authToken);
        }
        return headers;
    }

    /**
     * Get user profile from Identity Service
     */
    public Object getUserProfile(String userId) {
        try {
            String url = identityServiceUrl + "/api/users/" + userId;
            return restTemplate.getForObject(url, Object.class);
        } catch (Exception e) {
            log.error("Failed to fetch user profile", e);
            throw new RuntimeException("Failed to fetch user profile", e);
        }
    }

    /**
     * Get market data from Market Service
     */
    public Object getMarketData(String symbol) {
        try {
            String url = marketServiceUrl + "/api/market/data/" + symbol;
            return restTemplate.getForObject(url, Object.class);
        } catch (Exception e) {
            log.error("Failed to fetch market data for symbol: {}", symbol, e);
            throw new RuntimeException("Failed to fetch market data", e);
        }
    }

    /**
     * Get live price from Market Service
     */
    public Object getLivePrice(String symbol) {
        try {
            String url = marketServiceUrl + "/api/market/price/" + symbol;
            return restTemplate.getForObject(url, Object.class);
        } catch (Exception e) {
            log.error("Failed to fetch live price for symbol: {}", symbol, e);
            throw new RuntimeException("Failed to fetch live price", e);
        }
    }

    /**
     * Place a trade order via Trading Service
     */
    public Object placeOrder(Object orderRequest) {
        try {
            String url = tradingServiceUrl + "/api/orders";
            HttpEntity<Object> entity = new HttpEntity<>(orderRequest, createHeaders());
            return restTemplate.postForObject(url, entity, Object.class);
        } catch (Exception e) {
            log.error("Failed to place order", e);
            throw new RuntimeException("Failed to place order", e);
        }
    }

    /**
     * Get user's open positions
     */
    public Object getOpenPositions(String userId) {
        try {
            String url = tradingServiceUrl + "/api/positions/" + userId;
            return restTemplate.getForObject(url, Object.class);
        } catch (Exception e) {
            log.error("Failed to fetch open positions", e);
            throw new RuntimeException("Failed to fetch open positions", e);
        }
    }

    /**
     * Get trade history
     */
    public Object getTradeHistory(String userId) {
        try {
            String url = tradingServiceUrl + "/api/trades/" + userId;
            return restTemplate.getForObject(url, Object.class);
        } catch (Exception e) {
            log.error("Failed to fetch trade history", e);
            throw new RuntimeException("Failed to fetch trade history", e);
        }
    }
}
