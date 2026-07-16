import { getRuntimeConfig } from './runtime-config';

/**
 * Production environment configuration. `gatewayBaseUrl` is loaded at
 * runtime from `/assets/config.json` (see `loadRuntimeConfig()` in main.ts).
 */
export const environment = {
  production: true,
  get gatewayBaseUrl(): string {
    return getRuntimeConfig().gatewayBaseUrl;
  },
};
