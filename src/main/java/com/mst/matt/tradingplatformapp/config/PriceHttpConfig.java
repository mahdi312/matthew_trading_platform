package com.mst.matt.tradingplatformapp.config;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Shared HTTP client for market-data providers — longer timeouts and simple retries
 * for flaky or slow networks (common cause of empty ticker/chart data).
 */
@Configuration
public class PriceHttpConfig {

    @Bean(name = "priceHttpClient")
    public OkHttpClient priceHttpClient(
            // P3 (LOG-FIX): fail fast on bad networks (default 5s/10s instead of 30s/45s).
            // The CircuitBreakerInterceptor + retry-count=0 prevent us from queueing
            // up doomed requests behind a dead provider.
            @Value("${api.http.connect-timeout-sec:5}") int connectSec,
            @Value("${api.http.read-timeout-sec:10}") int readSec,
            @Value("${api.http.retry-count:0}") int retryCount) {

        return new OkHttpClient.Builder()
                .connectTimeout(connectSec, TimeUnit.SECONDS)
                .readTimeout(readSec, TimeUnit.SECONDS)
                .writeTimeout(connectSec, TimeUnit.SECONDS)
                .callTimeout(connectSec + readSec + 5L, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(false)   // P3 (LOG-FIX): circuit breaker decides, not OkHttp
                .addInterceptor(new RetryInterceptor(retryCount))
                .build();
    }

    /** Retries idempotent GETs on timeout / 5xx (not on 4xx). */
    static final class RetryInterceptor implements Interceptor {
        private final int maxRetries;

        RetryInterceptor(int maxRetries) {
            this.maxRetries = Math.max(0, maxRetries);
        }

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            IOException last = null;

            for (int attempt = 0; attempt <= maxRetries; attempt++) {
                if (attempt > 0) {
                    try {
                        Thread.sleep(500L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Retry interrupted", ie);
                    }
                }
                try {
                    Response response = chain.proceed(request);
                    if (response.isSuccessful() || response.code() < 500 || attempt == maxRetries)
                        return response;
                    response.close();
                } catch (IOException e) {
                    last = e;
                    if (attempt == maxRetries) throw e;
                }
            }
            throw last != null ? last : new IOException("Request failed");
        }
    }
}
