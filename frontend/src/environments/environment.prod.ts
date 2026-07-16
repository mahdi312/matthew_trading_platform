/**
 * Production environment configuration. `gatewayBaseUrl` should be
 * overridden at build/deploy time (e.g., via a replaced file or
 * runtime-injected config) to point at the deployed Gateway's public URL.
 */
export const environment = {
  production: true,
  gatewayBaseUrl: '/', // same-origin by default; override per deployment
};
