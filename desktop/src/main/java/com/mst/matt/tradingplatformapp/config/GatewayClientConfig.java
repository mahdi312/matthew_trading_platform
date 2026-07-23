package com.mst.matt.tradingplatformapp.config;

import com.mst.matt.tradingplatformapp.client.TokenStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Configures the shared {@link WebClient} used by every API-client bean
 * ({@code IdentityApiClient}, {@code TradeApiClient}, etc.).
 *
 * <h3>Design</h3>
 * <ul>
 *   <li>Base URL is the gateway (default {@code http://localhost:8080}).
 *       Override via {@code gateway.base-url} in {@code application.properties}.</li>
 *   <li>An {@link ExchangeFilterFunction} automatically attaches
 *       {@code Authorization: Bearer <token>} to every request when
 *       {@link TokenStore} holds a non-null token.  Unauthenticated calls
 *       (login / register) are sent without the header — the gateway's JWT
 *       filter whitelists those paths.</li>
 * </ul>
 */
@Configuration
public class GatewayClientConfig {

    /**
     * Base URL for the API Gateway.
     * Override in {@code application.properties} for production:
     * {@code gateway.base-url=https://api.your-platform.com}
     */
    @Value("${gateway.base-url:http://localhost:8080}")
    private String gatewayBaseUrl;

    @Bean
    public WebClient gatewayWebClient(TokenStore tokenStore) {
        return WebClient.builder()
                .baseUrl(gatewayBaseUrl)
                .filter(bearerAuthFilter(tokenStore))
                .build();
    }

    /**
     * Injects the JWT as a Bearer token when one is present in {@link TokenStore}.
     * Login/register calls are sent with no token — the gateway's
     * {@code GatewayJwtAuthFilter} whitelists {@code /api/auth/login} and
     * {@code /api/auth/register} by path prefix, so the absence of the header
     * is harmless for those routes.
     */
    private ExchangeFilterFunction bearerAuthFilter(TokenStore tokenStore) {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            String token = tokenStore.getToken();
            if (token == null || token.isBlank()) {
                return Mono.just(request);
            }
            ClientRequest mutated = ClientRequest.from(request)
                    .header("Authorization", "Bearer " + token)
                    .build();
            return Mono.just(mutated);
        });
    }
}
