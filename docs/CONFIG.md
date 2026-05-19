# Configuration & secrets

## Quick setup

1. Copy the example file:

   ```bash
   cp src/main/resources/application.properties.example src/main/resources/application-local.properties
   ```

2. Edit `application-local.properties` and add your API keys, mail password, and Telegram settings.

3. Run the app — `application.properties` imports the local file automatically:

   ```properties
   spring.config.import=optional:classpath:application-local.properties
   ```

`application-local.properties` is **gitignored**. Never commit real keys.

## Providers without keys

These work with defaults in `application.properties`:

- **Binance** — crypto quotes & OHLCV
- **Yahoo Finance** — US stocks
- **Frankfurter** — major forex pairs

Keyed providers (Alpha Vantage, Finnhub, Polygon, etc.) are skipped when the matching `api.*-key` is blank.

## Rotate exposed keys

If keys were ever committed to git, revoke them at each provider’s dashboard and issue new ones in `application-local.properties` only.
