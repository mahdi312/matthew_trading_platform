/**
 * Central registry of every Gateway-routed API path prefix.
 *
 * Rule: no feature module or service may hardcode a path string.
 * All REST and WebSocket calls go through environment.gatewayBaseUrl
 * (never a microservice's own port) — these constants are the single
 * source of truth for what gets appended to that base URL.
 *
 * See Migration_Guide_frontend.md Part 2 (Screen → Module → API Map)
 * for the full ownership table this file reflects.
 */

/** identity-service routes */
export const AUTH_API = '/api/auth';
export const PROFILE_API = '/api/profile';
export const PROFILE_PREFERENCES_API = '/api/profile/preferences';
export const ADMIN_API = '/api/admin';

/** trading-service routes */
export const TRADES_API = '/api/trades';
export const PORTFOLIO_API = '/api/portfolio';
export const REPORTS_API = '/api/reports';

/** market-service routes */
export const MARKET_API = '/api/market';
export const CHARTS_API = '/api/charts';
export const INDICATORS_API = '/api/indicators';

/** alert-service routes */
export const ALERTS_API = '/api/alerts';

/** ai-service routes */
export const AI_API = '/api/ai';

/** reference-data-service routes */
export const REFERENCE_API = '/api/reference';

/** WebSocket / STOMP endpoint (proxied through the Gateway) */
export const WS_ENDPOINT = '/ws';
