
package com.mst.matt.contracts.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Reads {@code X-Correlation-Id} (set by the Gateway — see
 * {@code GatewayJwtAuthFilter}) into SLF4J's MDC for the duration of the request,
 * so every log line in this service can include it via {@code %X{correlationId}}
 * in the logging pattern. Always cleared in a {@code finally} block — MDC is
 * thread-local and threads are reused by the servlet container's thread pool, so
 * a missed clear would leak one request's ID into a later unrelated request's logs.
 *
 * <p>Register as a {@code @Bean} of type {@link org.springframework.boot.web.servlet.FilterRegistrationBean}
 * or simply {@code @Component} it directly — Spring Boot auto-registers any
 * {@link jakarta.servlet.Filter} bean.</p>
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = java.util.UUID.randomUUID().toString(); // request arrived without one — still tag it
        }
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId); // echo back — useful for client-side correlation too
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}