package com.mst.matt.identityservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
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
    public OpenAPI identityOpenApi(@Value("${server.port:8081}") int port) {
        return new OpenAPI()
                .info(new Info()
                        .title("MTP Identity Service API")
                        .description("""
                                Authentication, profiles, broker links, and admin user management.
                                Prefer calling via the API Gateway at http://localhost:8080.
                                Public: POST /api/auth/login, POST /api/auth/register.
                                """)
                        .version("0.1.0")
                        .contact(new Contact().name("Matthew Trading Platform")))
                .servers(List.of(
                        new Server().url("http://localhost:" + port).description("Direct (identity-service)"),
                        new Server().url("http://localhost:8080").description("Via Gateway")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_JWT))
                .components(new Components().addSecuritySchemes(BEARER_JWT, bearerJwt()));
    }

    static SecurityScheme bearerJwt() {
        return new SecurityScheme()
                .name(BEARER_JWT)
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("JWT from POST /api/auth/login");
    }
}
