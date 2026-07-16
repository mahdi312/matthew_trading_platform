
package com.mst.matt.notificationservice.config; // adjust package per service

import com.mst.matt.contracts.observability.CorrelationIdFeignInterceptor;
import com.mst.matt.contracts.observability.CorrelationIdFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObservabilityConfig {

    @Bean
    public CorrelationIdFilter correlationIdFilter() {
        return new CorrelationIdFilter();
    }

    @Bean
    public CorrelationIdFeignInterceptor correlationIdFeignInterceptor() {
        return new com.mst.matt.contracts.observability.CorrelationIdFeignInterceptor();
    }
}