package com.mst.matt.marketservice.config;

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
    public OpenAPI marketOpenApi(@Value("${server.port:8082}") int port) {
        return new OpenAPI()
                .info(new Info()
                        .title("MTP Market Service API")
                        .description("""
                                OHLCV, symbols, watchlist, chart drawings/layouts, and indicators.
                                Gateway injects X-User-Id from JWT for user-scoped endpoints.
                                Prefer http://localhost:8080.
                                """)
                        .version("0.1.0"))
                .servers(List.of(
                        new Server().url("http://localhost:" + port).description("Direct (market-service)"),
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
