# API Providers Reference

> Generated as part of task **T-16** in [`FIX_TASKLIST.md`](./FIX_TASKLIST.md).
>
> Lists every external market-data / fundamentals provider integrated in the platform, the
> property key that supplies its credentials, the asset classes it serves, and the fallback
> chain order used by `PriceProviderRegistry` / `FundamentalRouter`.

---

## How routing works

1. The user opens a chart or fundamentals view.
2. `AssetClassDetector` classifies the symbol → `CRYPTO`, `FOREX`, `STOCK`, `COMMODITY`, or `INDEX`.
3. `PriceProviderRegistry.chainFor(symbol, profile)` builds an ordered list of `PriceService`
   beans:
   * Profile-preferred provider goes first (Settings → Chart Provider).
   * Default per-asset order (see *Fallback chains* below) fills the rest.
   * Disabled providers (no API key) are silently skipped.
4. `PriceRouter` walks the chain. The first provider that returns a non-empty result wins,
   and its display name is exposed via `PriceRouter.getLastProviderName()` (shown in the
   chart status bar — T-14).

All HTTP traffic goes through `HttpJsonClient` (timeouts + retry from
`api.http.*` properties) and an OkHttp pool defined in `PriceHttpConfig`.

---

## Quote / OHLCV providers (price)

| Provider | Property key | Free tier | Asset classes | Notes |
|----------|--------------|-----------|----------------|-------|
| **Binance** | *(none — public spot API)* | Unlimited public | Crypto | Primary crypto source. Auto-falls back to `data-api.binance.vision`. |
| **CoinGecko** | `api.coingecko-key` *(optional demo key)* | 30 req/min | Crypto | Broader symbol coverage when Binance is missing a pair. |
| **Yahoo Finance** | *(no key)* | Polite use | Stock / Forex / Commodity / Index | Primary stock source. Symbol mapping: `EURUSD=X`, `GC=F` for gold, `^GSPC` for S&P 500. |
| **Frankfurter (ECB)** | *(no key)* | Unlimited | Forex | Daily ECB rates, no intraday delta. |
| **Alpha Vantage** | `api.alphavantage-key` | 5 req/min, 500/day | Stock / Forex / Crypto | Throttled in `HttpJsonClient`; see T-23. |
| **Polygon.io** | `api.polygon-key` | 5 req/min | Stock | Premium feed. |
| **Finnhub** | `api.finnhub-key` | 60 req/min | Stock / Forex / Crypto | Throttled in `HttpJsonClient`; see T-23. |
| **Twelve Data** | `api.twelvedata-key` | 8 req/min, 800/day | Stock / Forex / Crypto | Cross-asset fallback. |
| **Marketstack** | `api.marketstack-key` | 100 req/month | Stock | End-of-day quotes. |
| **Fixer.io** | `api.fixerio-key` | 100 req/month | Forex | Implemented in `FixerForexService` (T-05). |
| **Open Exchange Rates** | `api.openexchangerates-key` | 1000 req/month | Forex | Implemented in `OpenExchangeRatesForexService` (T-05). |
| **ExchangeRate-API** | `api.exchangerateapi-key` | 1500 req/month | Forex | Implemented in `ExchangeRateApiForexService` (T-05). |
| **FreeCurrencyAPI** | `api.freecurrencyapi-key` | 5000 req/month | Forex | Implemented in `FreeCurrencyApiForexService` (T-05). |
| **CurrencyLayer** | `api.currencylayer-key` | 100 req/month | Forex | Implemented in `CurrencyLayerForexService` (T-05). |

### Fallback chains

| Asset class | Default order |
|-------------|---------------|
| CRYPTO | Binance → CoinGecko → Twelve Data → Alpha Vantage → Finnhub → Yahoo |
| FOREX | Frankfurter → Fixer → FreeCurrencyAPI → Open Exchange Rates → ExchangeRate-API → CurrencyLayer → Twelve Data → Alpha Vantage → Finnhub → Yahoo |
| STOCK | Yahoo → Finnhub → Polygon → Alpha Vantage → Twelve Data → Marketstack → Binance → CoinGecko |
| COMMODITY | Yahoo (with `=F` futures mapping) → Twelve Data → Alpha Vantage → Finnhub |
| INDEX | Yahoo (with `^` index mapping) → Twelve Data → Alpha Vantage → Finnhub |

User-preferred provider from Settings is **prepended** to the chain; the rest of the order is
preserved as defined above.

---

## Fundamentals providers

| Provider | Property key | Coverage | Notes |
|----------|--------------|----------|-------|
| **Alpha Vantage** | `api.alphavantage-key` | US equities, annual & quarterly | `OVERVIEW`, `INCOME_STATEMENT`, `EARNINGS` endpoints. |
| **Finnhub** | `api.finnhub-key` | Global equities, basic financials | `/stock/metric`, `/stock/profile2`, `/stock/financials-reported`. |

Provider order in `FundamentalRouter`: profile preference → Alpha Vantage → Finnhub.

> Crypto, forex, commodity, and index symbols are **not supported** by these endpoints.
> `YearlyProfitController` surfaces an explicit message instead of an empty table — see T-10.

---

## Rate limiting (T-23)

`HttpJsonClient` applies token-bucket throttling for the two free tiers most likely to hit
limits:

| Provider | Limit | Implementation |
|----------|-------|----------------|
| Alpha Vantage | 5 req / 60 s | `HttpJsonClient.throttle("alphavantage", 5, Duration.ofSeconds(60))` |
| Finnhub | 60 req / 60 s | `HttpJsonClient.throttle("finnhub", 60, Duration.ofSeconds(60))` |

If a request would exceed the budget, the call blocks (up to 60 s) until a token is
available. Failed bursts no longer surface as `429` to the UI.

---

## Live API smoke test (T-21)

```bash
# Skipped by default (CI-safe):
mvn test -Dtest='!LivePriceApiSmokeTest'

# Opt-in run that hits real APIs (needs internet + populated application-local.properties):
mvn test -Dtest=LivePriceApiSmokeTest -Dlive.api.tests=true
```

The `LivePriceApiSmokeTest` is annotated with
`@EnabledIfSystemProperty(named = "live.api.tests", matches = "true")` so it stays inert in
the default `mvn test` run and during CI builds.

---

## Adding a new provider

1. Implement `PriceService` (or `FundamentalDataProvider`) with `getProviderId()` returning
   a new enum constant in `MarketDataProvider` / `FundamentalDataProvider`.
2. Annotate the class with `@Service` so Spring picks it up via component scan.
3. Add the key field + getter/setter/has-key helper in `MarketApiProperties`.
4. Add a row to `application.properties` (blank value) and
   `application.properties.example` (placeholder).
5. Register the provider in the relevant fallback chain inside `PriceProviderRegistry`.
6. Add unit tests using `MockWebServer` (see `BinanceServiceTest` as template).
7. Document it in this file.
