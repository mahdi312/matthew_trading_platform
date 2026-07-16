
package com.mst.matt.contracts.observability;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.MDC;

/**
 * Propagates the current request's correlation ID (from MDC, set by
 * {@link CorrelationIdFilter}) onto every outgoing Feign call, so the chain
 * doesn't break at a service-to-service hop.
 */
public class CorrelationIdFeignInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null) {
            template.header(CorrelationIdFilter.HEADER, correlationId);
        }
    }
}