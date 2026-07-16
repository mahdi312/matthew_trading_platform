/**
 * Development environment configuration.
 *
 * Per the migration guide (docs/MTP_Microservices_Migration_Guide.md,
 * Step 0 / .cursorrules), the Angular app talks to the backend ONLY
 * through the API Gateway (infra/gateway-service) — never directly to a
 * microservice. `gatewayBaseUrl` is therefore the single base URL for
 * both REST calls and the STOMP/SockJS WebSocket connection (the Gateway
 * routes `/ws/**` through to `alert-service`, see
 * infra/gateway-service/src/main/resources/application.yml).
 */
export const environment = {
  production: false,
  gatewayBaseUrl: 'http://localhost:8080',
};
