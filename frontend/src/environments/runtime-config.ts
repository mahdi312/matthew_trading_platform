export interface RuntimeConfig {
  gatewayBaseUrl: string;
}

const defaults: RuntimeConfig = {
  gatewayBaseUrl: 'http://localhost:8080',
};

let loaded = false;

export function getRuntimeConfig(): RuntimeConfig {
  return defaults;
}

export function setRuntimeConfig(config: Partial<RuntimeConfig>): void {
  Object.assign(defaults, config);
  loaded = true;
}

export function isRuntimeConfigLoaded(): boolean {
  return loaded;
}

/** Load /assets/config.json before bootstrap (production / Docker). */
export function loadRuntimeConfig(): Promise<void> {
  return fetch('/assets/config.json')
    .then((response) => (response.ok ? response.json() : null))
    .then((json: Partial<RuntimeConfig> | null) => {
      if (json?.gatewayBaseUrl) {
        setRuntimeConfig(json);
      }
    })
    .catch(() => undefined);
}
