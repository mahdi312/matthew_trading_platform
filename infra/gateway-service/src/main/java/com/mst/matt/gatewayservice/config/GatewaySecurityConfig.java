package com.mst.matt.gatewayservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Reactive Spring Security configuration for the API Gateway.
 *
 * <p>Spring Security on the gateway is intentionally minimal: all real
 * JWT validation is handled by the custom {@link com.mst.matt.gatewayservice.security.GatewayJwtAuthFilter}
 * (a {@link org.springframework.cloud.gateway.filter.GlobalFilter}) that runs
 * <em>before</em> routing.  Spring Security here only disables CSRF and turns
 * off its own form-login/http-basic, which would conflict with the stateless
 * JWT model.</p>
 *
 * <p>Route-level authorisation (e.g., role checks) can be added here in a
 * later step using {@code .authorizeExchange(...)} if per-route rules are
 * needed at the gateway tier.</p>
 */
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                // Permit all at the Spring Security level; the GatewayJwtAuthFilter
                // enforces JWT auth before requests reach the routes.
                .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
                .build();
    }
}
