package com.mst.matt.aiservice.config;

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
    public OpenAPI aiOpenApi(@Value("${server.port:8089}") int port) {
        return new OpenAPI()
                .info(new Info()
                        .title("MTP AI Service API")
                        .description("""
                                LLM market summaries, trading signals, journal critique, and news insight.
                                Prefer http://localhost:8080.
                                """)
                        .version("0.1.0"))
                .servers(List.of(
                        new Server().url("http://localhost:" + port).description("Direct (ai-service)"),
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
