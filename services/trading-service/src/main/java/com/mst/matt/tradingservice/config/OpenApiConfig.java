package com.mst.matt.tradingservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_JWT = "bearer-jwt";

    @Bean
    public OpenAPI tradingOpenApi(@Value("${server.port:8083}") int port) {
        return new OpenAPI()
                .info(new Info()
                        .title("MTP Trading Service API")
                        .description("""
                                Trade journal, live order placement, portfolio stats, and reports.
                                User identity via Gateway X-User-Id (from JWT). Prefer http://localhost:8080.
                                """)
                        .version("0.1.0"))
                .servers(List.of(
                        new Server().url("http://localhost:" + port).description("Direct (trading-service)"),
                        new Server().url("http://localhost:8080").description("Via Gateway")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_JWT))
                .components(new Components().addSecuritySchemes(BEARER_JWT,
                        new SecurityScheme()
                                .name(BEARER_JWT)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
