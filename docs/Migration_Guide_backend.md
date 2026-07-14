# Matthew Trading Platform — Migration COMPLETION Guide (Gap-Closure, Agent-Optimized)

**Companion to:** `MTP_Microservices_Migration_Guide.md` (the architecture/scaffolding guide) and
`trading_platform_java_spring_microservices.docx` (the best-practice architecture reference).
**This document assumes those two are correct and already partly executed.** It does not repeat their
content — it tells you exactly **what's actually missing**, based on a full read of the current repo, and
gives you promptable steps to close every gap so `desktop/` can finally become a thin client.

**How to use this doc with an AI coding agent (same rules as the other guides):**

- Run steps **in order within a phase**; phases are ordered by dependency, not by importance.
- Paste each "Prompt" block verbatim — it already contains file paths and scope limits.
- Every prompt tells the agent to **port**, not reinvent: the source file paths are real paths in
  `desktop/src/main/java/com/mst/matt/tradingplatformapp/`. Point Cursor at them directly.
- After each step, check it against "Expected output" before moving to the next.
- Keep the existing `.cursorrules` loaded, plus the addendum in Appendix C below.

---

## Part 0 — Progress Review: `TradingPlatformApp_V_2_0_4` (Steps 1–7)

*Added after reviewing the codebase once Steps 1–7 below were attempted. Read this before doing anything
else — it corrects three integration gaps and adds one cross-cutting step (Redis) that the original Steps
1–14 never called out. Everything else in this document is unchanged and still accurate.*

**The good news first — the abstraction-layer discipline this guide asked for is real and consistent:**

- `market-service`'s `MarketOhlcvProviderRegistry` + `MarketProviderConfig` wire all 15 OHLCV providers
  through the shared `ProviderRegistry<T>` exactly as specified — real per-`AssetClass` priority chains,
  one Resilience4j circuit breaker per provider, NoOp registered last as guaranteed fallback.
- The monolith's `AbstractForexService` template-method pattern was correctly preserved as
  `AbstractForexOhlcvProvider` — that's why `ExchangeRateApiForexOhlcvProvider` is only 35 lines; the
  shared logic lives in the parent, not duplicated per-provider. Good instinct to keep that pattern instead
  of flattening it.
- `reference-data-service`'s `ReferenceDataController` is the **model implementation** — it injects
  `ProviderRegistry<T>` for all six interfaces and calls `executeWithFallback()` for everything, never a
  concrete provider or raw HTTP client directly. If you want a "what does 100% correct look like"
  reference for any future service, point Cursor at this one.
- `ai-service`'s news flow correctly calls `reference-data-service` over Feign (`ReferenceDataClient`)
  instead of re-implementing news fetching — exactly the "don't duplicate a provider that already has a
  home" discipline this guide asked for.
- `charting`'s `ChartDrawingService` correctly scopes every query by `userId`, matching the pattern
  `alert-service` already established for `PriceAlert`.

**Three places where the abstraction layer was built but never actually connected to anything that calls
it** — this is the exact failure mode to watch for going forward: an interface + registry existing is not
the same as a step being done; something reachable over HTTP has to actually invoke the registry.

### Gap 1 — `market-service`'s new OHLCV registry is unreachable (Step 3 is NOT done, despite existing)

`MarketDataController` (`/api/market/ohlcv/{symbol}`) only reads from `OhlcvStorageService` — its own
Javadoc still says *"Phase 2 does NOT implement actual third-party OHLCV provider fetches... endpoints
serve data that has already been stored."* `OhlcvCacheService` (the cache-aside layer that calls the new
registry) exists and compiles, but **nothing in the codebase injects or calls it** — it's an orphaned bean.
`MarketDataSyncService`'s class comment is stale, still reading *"Until Phase 3 beans are wired, sync falls
back to serving from the DB only"* even though those beans now exist. Net effect: hit the API today and
you only ever get BitUnix-sourced data — the 15 new providers never fire.

**Prompt:**

> In `services/market-service`: wire `OhlcvCacheService` into the read path. On `GET
> /api/market/ohlcv/{symbol}`, if `OhlcvStorageService` has no (or stale) bars for the requested
> symbol/timeframe, call `OhlcvCacheService.getCached(...)` — resolving `AssetClass` via the existing
> `AssetClassDetector` — and persist the result through `OhlcvStorageService`/`DynamicOhlcvTableService`
> before returning it, so the next request is served from storage. Then update `MarketDataSyncService`: its
> scheduled sync should call `OhlcvCacheService`/`MarketOhlcvProviderRegistry` instead of only touching the
> DB, and its class Javadoc should be corrected — it's no longer "waiting for Phase 3." Update
> `MarketDataController`'s Javadoc to match reality once this is done.

### Gap 2 — `ai-service` still points its OHLCV registry at the NoOp mock (Step 6 is NOT done for signals)

`AiAnalysisProviderConfig`'s `ohlcvRegistry` bean registers **only** `NoOpOhlcvDataProvider` — there is no
Feign client to `market-service` in `ai-service` at all (only `ReferenceDataClient` exists). `AiNewsService`
works because it correctly calls out to `reference-data-service`; `AiSignalService`/`AiAnalysisProviderImpl`
cannot generate a real price-based signal because they have no real price data to read. This is the same
failure mode as Gap 1: a registry exists, it's just wired to nothing real.

**Prompt:**

> In `services/ai-service`: add `client/MarketDataClient.java`, a `@FeignClient(name =
> "market-service", url = "${services.market.url:}", path = "/api/market")` exposing `getOhlcv(symbol,
> timeframe, limit)` — same pattern as `ReferenceDataClient`. Add a thin adapter (e.g.
> `MarketServiceOhlcvProvider implements OhlcvDataProvider`) that delegates to this Feign client and maps
> the response into `NormalizedOhlcvBar`. Register it ahead of `NoOpOhlcvDataProvider` in
> `AiAnalysisProviderConfig`'s `ohlcvRegistry` bean (NoOp stays registered, just demoted to fallback — same
> rule as every other registry in this codebase). Re-verify `AiSignalService` actually calls through this
> registry rather than working on empty data.

### Gap 3 — `market-service`'s charting REST layer was never built (Step 7 is ~60% done)

`charting/model/`, `charting/repository/`, and `charting/service/` are all ported correctly, including
`userId` scoping. But `charting/controller/` **exists as an empty directory** — no `ChartingController`, no
`IndicatorController`. None of `ChartDrawingService`, `IndicatorComputeService`, `IndicatorService`,
`SupportResistanceService` is reachable over HTTP yet, and nothing resolves `userId` from the JWT/Gateway
header at a REST boundary — that resolution only happens inside method parameters right now, with nothing
upstream supplying it.

**Prompt:**

> In `services/market-service/.../charting/`, add the `ChartingController` and `IndicatorController` from
> the original Step 7 prompt: `ChartingController` → `/api/charts/drawings` (GET/POST/DELETE),
> `/api/charts/layouts` (GET/POST); `IndicatorController` → `/api/indicators/configs` (GET/POST),
> `/api/indicators/{symbol}/compute` (GET). Resolve `userId` the same way `alert-service`'s
> `AlertController` does today (from the JWT claim forwarded by the Gateway — check that controller for the
> exact mechanism, e.g. an `@AuthenticationPrincipal`-style resolver or a header the Gateway injects) — do
> not accept `userId` as a request parameter from the client.

### New cross-cutting item — Redis (missed in the original Step 1–14 list, not your fault)

The architecture reference doc marks **Redis as mandatory supporting infrastructure** for caching and
low-latency shared state — every service that added caching so far (`market-service`'s `CacheConfig`, and
`OhlcvCacheService` once Gap 1 is fixed) used **Caffeine**, which is local to a single JVM instance. That's
fine for one instance in local dev, but it silently stops matching the reference architecture the moment
you run more than one instance of a service: caches don't share, a cold instance re-hits rate-limited
provider APIs unnecessarily, and cache state doesn't survive a restart. This should have been in the
original guide and wasn't — that's on this document, not on the implementation work done so far.

**Prompt:**

> Add Redis as the shared cache layer, without removing Caffeine — use Caffeine as an L1 (in-JVM,
> sub-millisecond) cache and Redis as L2 (shared across instances), a standard two-tier pattern. Add
> `spring-boot-starter-data-redis` to `market-service` and `reference-data-service` (the two services doing
> the heaviest external-provider caching). Add a `redis` service to `docker-compose.yml` once Step 15 of the
> main guide is reached — until then, a local Redis via `docker run -p 6379:6379 redis:7-alpine` is enough
> for development. Reconfigure `OhlcvCacheService` (and the equivalent in `reference-data-service`, once it
> gets a caching layer — it doesn't have one yet either) to check Caffeine first, then Redis, then the
> provider registry on a full miss, writing back to both cache tiers on fetch.

### Architecture-doc compliance check (`trading_platform_java_spring_microservices.docx`)

A pass against the reference doc's recommended stack table, beyond the Java 25 / Spring Boot 4.1.0 version
correction already made:

| Doc recommendation                                      | Actual state                                                 | Verdict                                                      |
| ------------------------------------------------------- | ------------------------------------------------------------ | ------------------------------------------------------------ |
| PostgreSQL as default transactional DB                  | Every service still on H2 in-memory                          | Expected — this is Step 14, deliberately last so you're not fighting migrations mid-port |
| Kafka for event backbone                                | `alert-service` publishes `alerts.triggered` via native `KafkaTemplate` | ✅ Matches (doc explicitly allows native Kafka clients over Spring Cloud Stream) |
| Redis for caching/hot state, marked "mandatory"         | Not used anywhere — Caffeine (in-JVM only) instead           | 🔴 Gap — see the new Redis step above                         |
| OpenFeign + Resilience4j for sync inter-service calls   | Used correctly (`ai-service → reference-data-service`)       | ✅ Matches                                                    |
| `BigDecimal` for financial fields                       | `Trade` model uses `BigDecimal` throughout, no `double`/`float` | ✅ Matches                                                    |
| Idempotency keys on order/command submission            | `POST /api/trades` has none yet                              | ⚠️ Already tracked as Step 13 — not urgent until Step 8 lands, but don't forget it |
| OpenSearch/Elasticsearch for search/analytics           | Not used                                                     | Fine to defer — the doc frames this for large-scale trade/audit search, not needed at current scale; revisit in Phase 3 if trade-history search gets slow |
| Dedicated IdP (Keycloak/Okta/Auth0) vs. self-built auth | `identity-service` is a self-built OAuth2 (Google) + JWT issuer, predating this guide | Not a defect, just worth naming: the doc recommends delegating to an external IdP. Swapping this out now would be a large, separate migration — treat as an open question for later, not part of this gap-closure pass |

Net: the deviations are Redis (real gap, now tracked) and the DB/idempotency items that were already
correctly sequenced as later steps. Everything else lines up with the reference architecture.

---

## Part 1 — Audit: What Actually Happened vs. What Was Planned

*This is the original baseline audit, from before Steps 1–7 were attempted — kept as-is for history. For
current status after Steps 1–7, read Part 0 above instead; several rows below are now out of date (e.g.
`market-service` and `reference-data-service` are no longer empty).*

This is the finding that triggered this document: **the infrastructure, contracts, and interfaces from
Phase A/B of the main guide are real and solid — but most of the actual business logic from the desktop app
was never copied into the services that are supposed to own it.** Several services are empty shells wired
to `NoOp*` mock beans. This is normal for a staged migration, but nothing after Step 5/7 of the main guide
was ever executed.

### 1.1 Service-by-service status

| Service                           | Scaffolding (infra, config, contracts wiring)                | Real business logic ported?                                  | Verdict                                                      |
| --------------------------------- | ------------------------------------------------------------ | ------------------------------------------------------------ | ------------------------------------------------------------ |
| `infra/discovery-service`         | Done (Eureka)                                                | N/A                                                          | ✅ Complete                                                   |
| `infra/gateway-service`           | Done (routing stub + JWT filter)                             | N/A                                                          | ⚠️ Needs routes for every service added below (Step 11)       |
| `infra/config-service`            | Scaffolded                                                   | `config-repo/` is empty                                      | ⚠️ Low priority, revisit in Phase 3                           |
| `shared/contracts`                | **Excellent** — every interface from Steps 4/4.5/4.75 exists (`MarketDataProvider`, `TradingProvider`, `OhlcvDataProvider`, `FundamentalsProvider`, `NftDataProvider`, `NewsProvider`, `SentimentProvider`, `EconomicCalendarProvider`, `SymbolSearchProvider`, `AiAnalysisProvider`, `NotificationChannel`, all DTOs/enums) | N/A (this module is interfaces only, by design)              | ✅ Complete — do not touch structure, only add DTOs as needed |
| `services/identity-service`       | Done (OAuth2 Google + password, JWT, Gateway filter)         | `AppUser`/`RolePermission` copied; auth works                | ⚠️ Missing: profile settings, admin user management, app settings (see Step 9) |
| `services/market-service`         | Done (BitUnix `MarketDataProvider` impl, WS streaming, Caffeine cache) | **Only BitUnix.** No historical OHLCV storage, no multi-provider abstraction, no symbol/company/share reference data | 🔴 Largest gap — Steps 2 & 3                                  |
| `services/trading-service`        | Done (BitUnix `TradingProvider` impl, spot+futures, signing) | **No `Trade` entity, no repository, no `TradeService`, no `TradeController`.** `application.yml` literally says `# H2 in-memory database (order persistence scaffold)` — it's waiting for you | 🔴 Critical gap — Step 1 (do this first, other steps reference trades) |
| `services/alert-service`          | Done                                                         | `PriceAlert` CRUD, evaluation loop, Kafka publish, in-app WS channel all ported and look complete. Email/Telegram channels are **deliberate, well-commented stubs** pointing at Step 7 | ✅ ~90% — verify parity only (Step 8 closes the stubs)        |
| `services/notification-service`   | Empty shell (only the `@SpringBootApplication` class + `application.yml`) | Nothing ported                                               | 🔴 Critical gap — Step 8                                      |
| `services/reference-data-service` | Scaffolded with `NoOpProviderConfig` (6 mock beans + registries) | Nothing real ported                                          | 🔴 Critical gap — Steps 4 & 5                                 |
| `services/ai-service`             | Scaffolded with `NoOpAiProviderConfig` (mock `AiAnalysisProvider` + `OhlcvDataProvider`) | Nothing ported                                               | 🔴 Critical gap — Step 6                                      |
| `frontend/`                       | Only `core/auth` + a notification bell exist                 | N/A                                                          | Not in scope of this doc (see main guide Steps 12–13)        |
| `desktop/`                        | Still a full fat client: `pom.xml` still has `spring-boot-starter-data-jpa`, `postgresql`, `sqlite-jdbc`, `spring-boot-starter-mail`, `telegrambots`, `poi-ooxml`, `ta4j-core` | N/A — this is the thing that shrinks *after* the above is done | ⚠️ Step 12, deliberately last                                 |

### 1.2 What's sitting in the desktop app, unclaimed

Actual counts from `desktop/src/main/java/com/mst/matt/tradingplatformapp/`:

- **16 JavaFX FXML controllers** in `controller/` — **these are UI controllers, not REST controllers.**
  They call services in-process (`@Autowired TradeService`, etc). Nothing here gets "copied" to a
  microservice as-is; instead, each microservice grows a **new** `@RestController`, and the JavaFX
  controller gets refactored in Step 12 to call it over HTTP.
- **23 JPA entities** in `model/` (+2 in `model/fundamental/`).
- **14 Spring Data repositories** in `repository/`.
- **213 files / ~17k+ LOC** in `service/`, of which `service/price/` alone is 168 files across 15
  external-provider integrations (AlphaVantage, Binance, CoinGecko, CoinMarketCap, CurrencyLayer,
  ExchangeRate-API, Finnhub, Fixer, Frankfurter, FreeCurrencyAPI, Marketstack, OpenExchangeRates, Polygon,
  TwelveData, YahooFinance).
- **7 configs** in `config/` (2 of these — `JavaFxApplication`, `StageInitializer`/`StageReadyEvent` — are
  JavaFX bootstrap and never migrate; the rest do).

None of this is a criticism of the earlier work — Steps 0–5/7 built the plumbing correctly. This doc is
purely the "now actually move the water through the pipes" pass.

---

## Part 2 — Domain Ownership Map (decisions the original guide left open)

The main guide assigned homes for Steps 3–7's domains but **never assigned a home for four domains** that
exist in the desktop app. Decide these once, put them in `.cursorrules` (Appendix C), and never re-litigate
per-prompt:

| Desktop domain                                               | Files                                                        | Assigned to                                                  | Why                                                          |
| ------------------------------------------------------------ | ------------------------------------------------------------ | ------------------------------------------------------------ | ------------------------------------------------------------ |
| Historical OHLCV storage, candle aggregation, symbol/company/share master data | `service/marketdata/*`, `model/{OhlcvBar,Market,MarketDataTableRegistry,SymbolEntry,Company,Share}.java` + their repos | **`market-service`**                                         | It already owns live market data; historical storage is the same bounded context, not a new one. |
| Multi-provider OHLCV/price/forex                             | `service/price/*` (all 15 providers)                         | **`market-service`**, as `OhlcvDataProvider` implementations behind `ProviderRegistry<OhlcvDataProvider>` | Step 4.5 already defined the interface; it was just never implemented. Binance/CoinGecko/Polygon/etc. are *data* providers, distinct from BitUnix the *broker* (`MarketDataProvider`) — both live in `market-service` but behind different interfaces. |
| Charting: drawings, indicator configs/layouts, indicator math (SMA/RSI/MACD/Ichimoku/support-resistance) | `controller/{Chart,IndicatorMixer}Controller.java`, `service/ChartDrawingService.java`, `service/analysis/{IndicatorComputeService,IndicatorService,IndicatorResult,IchimokuResult,SupportResistanceService}.java`, `model/{ChartDrawing,ChartDrawingProperties,ChartDrawingToolType,ChartPoint,DrawingLayout,GlobalDrawingSettings,IndicatorConfig,IndicatorDefinition,TradeDrawingDraft}.java` + repos | **`market-service`** (new `charting` sub-package, not a new microservice) | This is a real gap — no service in the plan owns it. It's tightly coupled to OHLCV data and per-symbol chart state, and the architecture doc explicitly warns against service sprawl ("a limited set of meaningful services, not 50 tiny services"). Spinning up a 12th/8th microservice for what is fundamentally CRUD-on-JSON-blobs + math over data `market-service` already has is not justified yet. Revisit only if this package outgrows the service. |
| On-chain / DeFi / NFT data                                   | `controller/{DeFiDashboard,NftExplorer,OnchainPools}Controller.java`, CoinGecko DeFi/DEX/NFT classes in `service/price/api/coingecko/` | **`reference-data-service`**, as `NftDataProvider` implementation (+ extend with a `DeFiDataProvider` if you want strict typing, or fold pools/tokens into `NftDataProvider`'s DTOs — your call, but don't create a 9th service for it) | Step 4.5 already reserved `reference-data-service` for exactly this class of "supporting market context" data. |
| Reporting/export                                             | `controller/{Export,YearlyProfit}Controller.java`, `service/export/ExcelExportService.java`, `model/fundamental/YearlyFinancialRow.java` | **`trading-service`**, as a `ReportingController`            | It's fundamentally "export my trades/portfolio," which only `trading-service` has the data to do locally without cross-service joins. |
| Profile, app settings, admin user management                 | `controller/{AdminUserManagement,ProfileSettings}Controller.java`, `service/{ProfilePersistenceService,ProfileMigrationRunner,AppSettingsService}.java`, `model/UserProfile.java` | **`identity-service`**                                       | It already owns `AppUser`; `UserProfile` and settings are 1:1 extensions of the same identity aggregate. |
| Real email + Telegram sending                                | `service/NotificationService.java` (email), `service/alert/TradingTelegramBot.java` | **`notification-service`**, replacing the stubs currently sitting in `alert-service` | This is exactly what Step 7 of the main guide already specifies — it was just never executed. |
| AI synthesis: news summarization, sentiment scoring, signal generation | `service/AiNewsService.java`, `service/ai/{AiLlmModel,AiLlmModelRegistry}.java`, `service/analysis/{AnalysisService,SignalScoringService}.java` | **`ai-service`**, as `AiAnalysisProvider` implementation     | Matches Step 4.5's tab-to-abstraction table exactly.         |

---

## Part 3 — Phase 1 (P0): Port the Business Logic

Do these in order. Trading depends on nothing else below; market-service's two steps unblock alert-service's
real evaluation and the AI/reference-data steps; identity and notification can happen any time after Step 1.

### Step 1: Trading Service — Trade Persistence, Portfolio Stats, REST API

**Goal:** `trading-service` currently only talks to BitUnix. It has zero persistence for trades and no API
for the frontend/desktop to call. This is the single biggest functional hole — close it first.

**Prompt:**

> In `services/trading-service`, port the business logic from the JavaFX monolith at
> `desktop/src/main/java/com/mst/matt/tradingplatformapp/`:
>
> 1. Copy `model/Trade.java` (and its nested `Trade.*` types) into
     >    `trading-service/src/main/java/com/mst/matt/tradingservice/model/`, keeping all JPA annotations.
> 2. Copy `repository/TradeRepository.java` into `trading-service/.../repository/`, adjusting the package
     >    and keeping all query methods.
> 3. Copy the business logic (not the class name conflicts — rename if needed) from
     >    `service/TradeService.java` into `trading-service/.../service/TradeService.java`: CRUD, `computePnL()`,
     >    close-trade flow, and portfolio statistics. Keep `@Service` + `@Transactional`, injected via constructor
     >    (not field `@Autowired`, per current Spring conventions).
> 4. Copy the import logic from `service/BrokerImportService.java` into
     >    `trading-service/.../service/BrokerImportService.java` — this becomes the handler for broker trade
     >    history imports.
> 5. Create a new `TradeController` (`@RestController`, `/api/trades`) exposing: `POST /trades`,
     >    `PUT /trades/{id}`, `DELETE /trades/{id}`, `POST /trades/{id}/close`, `GET /trades`,
     >    `GET /trades/{id}`, `GET /portfolio/stats`. This controller did not exist before — the old
     >    `TradeEntryController` was a JavaFX view controller, not a REST controller, so this is new code that
     >    exposes the same operations over HTTP.
> 6. Wire `TradeService` to also call the already-existing `BitUnixTradingProvider` (via the
     >    `TradingProvider` interface, not directly) when a trade is placed against a live broker, vs. a manually
     >    logged trade which only touches the database.
> 7. Add DTOs (`TradeRequest`, `TradeResponse`, `PortfolioStatsResponse`) in `trading-service/.../dto/`
     >    instead of exposing the JPA entity directly.
>
> DO NOT touch `bitunix/` package — it's complete. DO NOT change the H2 datasource yet (that's Phase 3, Step
>
> 14) — get it working on H2 first, verify, then swap to Postgres.

**Expected output:** `trading-service` compiles, has a real `trades` table (H2, `ddl-auto: create-drop` is
fine for now), and `GET /portfolio/stats` returns real numbers computed from posted trades.

---

### Step 2: Market Service — Historical OHLCV Storage & Symbol Reference Data

**Goal:** `market-service` currently only streams live BitUnix ticks. It has no historical bar storage, no
candle aggregation, and no symbol/company/share master data — meaning nothing (charts, indicators, backtests)
has anything to read from yet.

**Prompt:**

> In `services/market-service`, port from
> `desktop/src/main/java/com/mst/matt/tradingplatformapp/`:
>
> 1. Copy these entities into `market-service/.../model/`: `OhlcvBar.java`, `Market.java`,
     >    `MarketDataTableRegistry.java`, `SymbolEntry.java`, `Company.java`, `Share.java`. Keep JPA annotations.
> 2. Copy their repositories: `OhlcvBarRepository`, `MarketRepository`, `MarketDataTableRegistryRepository`,
     >    `SymbolEntryRepository`, `CompanyRepository`, `ShareRepository`.
> 3. Copy the storage/aggregation logic from `service/marketdata/`:
     >    - `OhlcvStorageService.java` (top-level `service/`, not `service/marketdata/`) — bar persistence
>    - `CandleAggregationService.java` + `CandleAggregationScheduler.java` — timeframe roll-ups
>    - `DynamicOhlcvTableService.java` + `MarketDataTableNameUtil.java` — per-symbol dynamic tables, if you
       >      want to keep that pattern; otherwise flag it as a candidate to simplify into one partitioned table
       >      now that you're on Postgres-by-service (your call, note the decision in a code comment either way)
>    - `AggregatedCandleQueryService.java`, `ChartLiveSessionService.java`, `MarketDataSyncService.java` +
       >      `MarketDataSyncScheduler.java`, `MarketReferenceDataService.java`, `DatabaseDialectHelper.java`
> 4. Copy `service/price/SymbolNormalizer.java`, `service/price/AssetClassDetector.java`,
     >    `service/SymbolSyncService.java`, and `service/WatchlistDefaults.java`.
> 5. Add REST endpoints on a new `MarketDataController`: `GET /api/market/ohlcv/{symbol}`,
     >    `GET /api/market/symbols/search`, `GET /api/market/symbols/{symbol}`.
>
> Keep this entirely separate from the `bitunix/` package — this is the historical/reference-data path, not
> the live-broker path. DO NOT implement the actual third-party OHLCV providers (AlphaVantage, CoinGecko,
> etc.) in this step — that's Step 3, and it plugs into the storage you're building here.

**Expected output:** `market-service` has its own Postgres/H2-backed OHLCV tables, symbol/company/share
reference tables, and can serve historical bars once something writes to them (Step 3).

---

### Step 3: Market Service — Multi-Provider OHLCV Implementations

> **Status as of V2.0.4: registry built correctly, not wired in — see Part 0, Gap 1, before re-running this.**

**Goal:** Implement `OhlcvDataProvider` for real, replacing `NoOpOhlcvDataProvider`, using the 15
provider integrations already written in the desktop app — they just need to be adapted to the interface
and registered.

**Prompt:**

> In `services/market-service`, implement `com.mst.matt.contracts.provider.ohlcv.OhlcvDataProvider` once
> per external provider, porting logic from `desktop/.../service/price/api/`:
>
> 1. **Crypto:** `AlphaVantageMarketService`/`AlphaVantagePriceService` (`alphavantage/`),
     >    `BinanceService` (`binance/`), `CoinGeckoMarketService` (`coingecko/`),
     >    `CoinMarketCapMarketService`/`CoinMarketCapPriceService` (`coinmarketcap/`).
> 2. **Stocks:** `AlphaVantage` (reuse), `FinnhubMarketService`/`FinnhubPriceService` (`finnhub/`),
     >    `MarketstackPriceService` (`marketstack/`), `PolygonPriceService` (`polygon/`),
     >    `TwelveDataMarketService`/`TwelveDataPriceService` (`twelvedata/`), `YahooFinanceService`
     >    (`yahoofinance/`).
> 3. **Forex:** `AbstractForexService` + `FixerForexService`, `frakfurter/ForexService`,
     >    `FreeCurrencyApiForexService`, `CurrencyLayerForexService`, `ExchangeRateApiForexService`,
     >    `OpenExchangeRatesForexService`.
> 4. Port the supporting classes as-is: `HttpJsonClient.java`, `JsonParseUtil.java`,
     >    `ApiErrorDetector.java`, and every provider-specific response DTO in each `price/api/<provider>/`
     >    folder (these are plain data classes — copy verbatim).
> 5. Port `PriceRouter.java` and `PriceProviderRegistry.java` logic into a new
     >    `MarketOhlcvProviderRegistry` that wraps the shared `ProviderRegistry<OhlcvDataProvider>` from
     >    `shared/contracts` — one registry per `AssetClass` (`CRYPTO`, `STOCK`, `FOREX`), each with a
     >    priority/fallback chain, exactly as specified in Step 4.5 of the main guide. Wire Resilience4j circuit
     >    breakers per provider (not global).
> 6. Port `PriceCacheService.java` into a Caffeine-backed cache layer in front of the registry — same
     >    pattern already used for BitUnix in this service's `CacheConfig`.
> 7. Every implementation writes through to the `OhlcvStorageService` from Step 2 on fetch, so historical
     >    data accumulates instead of being fetched-and-discarded every time.
>
> Register real beans in place of `NoOpOhlcvDataProvider` (leave the no-op available as a fallback-of-last-
> resort in the registry, not removed). DO NOT put API keys in code — use the same `${ENV_VAR:}` pattern as
> `TradingConfig`'s BitUnix keys.

**Expected output:** `GET /api/market/ohlcv/{symbol}?assetClass=CRYPTO` returns real data, falling back
across providers if the primary fails, and caching/persisting it.

---

### Step 4: Reference Data Service — Fundamentals, News, Sentiment, Economic Calendar, Symbol Search

**Goal:** Same pattern as Step 3, but for `reference-data-service`'s five remaining `NoOp*` interfaces.

**Prompt:**

> In `services/reference-data-service`, implement each interface for real, porting from
> `desktop/.../service/fundamental/` and `service/price/api/`:
>
> 1. **`FundamentalsProvider`**: port `AlphaVantageFundamentalService.java`, `FinnhubFundamentalService.java`,
     >    `FundamentalRouter.java`, `FundamentalDataProvider.java`, `FundamentalService.java`, and
     >    `model/fundamental/FundamentalsReport.java`. Normalize both providers' output into
     >    `CompanyFundamentalsDto`/`CryptoTokenomicsDto`/`ForexMacroIndicatorsDto` from `shared/contracts` (Step
     >    4.5 already defines these — use them, don't invent new shapes).
> 2. **`NewsProvider`**: port the news-shaped classes — `AlphaVantageNewsSentiment.java`,
     >    `FinnhubNewsArticle.java`, `FinnhubNewsSentiment.java` — into `NewsArticleDto` results.
> 3. **`SentimentProvider`**: port `CmcFearAndGreed.java` (`coinmarketcap/`) and CoinGecko's global/trending
     >    endpoints where relevant, into `SentimentSnapshotDto`.
> 4. **`EconomicCalendarProvider`**: port `AlphaVantageEarningsCalendar.java`, `AlphaVantageIpoCalendar.java`,
     >    `AlphaVantageEconomicIndicator.java`, `FinnhubEconomicData.java`, `FinnhubEarningsCalendarEvent.java`,
     >    `FinnhubIpoEvent.java` into `EconomicEventDto`.
> 5. **`SymbolSearchProvider`**: port `AlphaVantageSearchResult.java`, `FinnhubSearchResult.java`,
     >    `TwelveDataSymbolSearch.java`, `CoinGeckoSearchService.java`/`CoinGeckoSearchResult.java` into
     >    `SymbolSearchResultDto`.
> 6. Register real beans alongside (not replacing) the `NoOp*` beans in `NoOpProviderConfig` — rename that
     >    class or split it once real beans exist, since "NoOp" will no longer be accurate for the whole file.
> 7. Add a `ReferenceDataController` exposing `GET /api/reference/fundamentals/{symbol}`,
     >    `GET /api/reference/news`, `GET /api/reference/sentiment`, `GET /api/reference/calendar`,
     >    `GET /api/reference/search`.
>
> Every implementation goes through the existing `ProviderRegistry<T>` + circuit breaker pattern already
> proven in this service's config. DO NOT call any provider SDK/HTTP client directly from the controller.

**Expected output:** All five interfaces backed by real data, with the mocks demoted to fallback-only.

---

### Step 5: Reference Data Service — NFT / On-Chain / DeFi Data

**Goal:** Close the last `NoOp*` interface (`NftDataProvider`) and give the DeFi/NFT/on-chain tabs a real
backend.

**Prompt:**

> In `services/reference-data-service`, implement `NftDataProvider`, porting from
> `desktop/.../service/price/api/coingecko/`: `CoinGeckoNftService.java` and its DTOs
> (`CoinGeckoNftBasic`, `CoinGeckoNftCollection`, `CoinGeckoNftFloorPrice`), plus the on-chain/DeFi classes
> `CoinGeckoDefiService.java`/`CoinGeckoDefiData.java`, `CoinGeckoDexService.java`/`CoinGeckoDex.java`,
> `CoinGeckoPool.java`/`CoinGeckoPoolTrade.java`, `CoinGeckoNetwork.java`,
> `CoinGeckoToken*.java` (`TokenIdentity`, `TokenInfo`, `TokenPrice`, `TokenMarketChart`).
>
> Normalize into `NftAssetDto`/`NftCollectionDto`/`NftEventDto`. If pool/DEX data doesn't cleanly fit those
> DTOs, extend `shared/contracts` with the minimum additional DTOs needed (e.g. `DeFiPoolDto`) rather than
> overloading the NFT DTOs.
>
> Add endpoints to `ReferenceDataController` (from Step 4): `GET /api/reference/nft/collections`,
> `GET /api/reference/defi/pools`.

**Expected output:** NFT/DeFi tabs have a real API to call instead of the old direct CoinGecko calls from
JavaFX.

---

### Step 6: AI Service — Analysis, News Synthesis, Signal Scoring

> **Status as of V2.0.4: news flow done correctly; OHLCV registry still points at NoOp — see Part 0, Gap 2.**

**Goal:** `ai-service` is currently an empty shell around a mock. This is where the AI tab's actual value
lives in the monolith.

**Prompt:**

> In `services/ai-service`, implement `AiAnalysisProvider` for real, porting from
> `desktop/.../service/`:
>
> 1. `AiNewsService.java` + `AiNewsException.java` — news fetch/summarization flow. Where it currently calls
     >    a news API directly, redirect it to call `reference-data-service`'s `NewsProvider` (via Feign/HTTP, not
     >    direct SDK) from Step 4, so news fetching isn't duplicated in two services.
> 2. `service/ai/AiLlmModel.java` + `AiLlmModelRegistry.java` — the LLM abstraction, kept as-is.
> 3. `service/analysis/AnalysisService.java` + `SignalScoringService.java` — market analysis + signal
     >    generation logic.
> 4. Normalize outputs into `AiMarketSummaryDto`, `AiSignalDto`, `AiTradeJournalCritiqueDto` from
     >    `shared/contracts` (already defined, unused until now).
> 5. Add an `AiController` exposing `GET /api/ai/summary/{symbol}`, `GET /api/ai/signals/{symbol}`,
     >    `POST /api/ai/journal-critique`.
>
> DO NOT port `service/analysis/IndicatorComputeService.java`, `IndicatorService.java`, `IchimokuResult.java`,
> `IndicatorResult.java`, or `SupportResistanceService.java` here — those are pure technical-indicator math,
> not AI, and belong to `market-service`'s charting package (Step 7). It's an easy mix-up since they live in
> the same `service/analysis/` folder in the monolith — don't let that folder structure dictate the service
> boundary.

**Expected output:** `ai-service` returns real LLM-backed summaries/signals instead of the no-op mock.

---

### Step 7: Market Service — Charting, Indicators, Drawings

> **Status as of V2.0.4: model/repository/service done; REST controllers missing — see Part 0, Gap 3.**

**Goal:** Close the domain that Part 2 identified as having no assigned home in the original plan at all.

**Prompt:**

> In `services/market-service`, add a new `charting` sub-package and port from
> `desktop/.../`:
>
> 1. Entities → `market-service/.../charting/model/`: `ChartDrawing.java`, `ChartDrawingProperties.java`,
     >    `ChartDrawingToolType.java`, `ChartPoint.java`, `DrawingLayout.java`, `GlobalDrawingSettings.java`,
     >    `IndicatorConfig.java`, `IndicatorDefinition.java`, `TradeDrawingDraft.java`.
> 2. Repositories → `.../charting/repository/`: `ChartDrawingRepository.java`, `DrawingLayoutRepository.java`,
     >    `IndicatorConfigRepository.java`.
> 3. Services → `.../charting/service/`: `ChartDrawingService.java`, and from `service/analysis/`:
     >    `IndicatorComputeService.java`, `IndicatorService.java`, `IndicatorResult.java`, `IchimokuResult.java`,
     >    `SupportResistanceService.java`. These compute indicators over the `OhlcvBar` data this service already
     >    owns (Step 2) — wire them to `OhlcvStorageService`/`AggregatedCandleQueryService` directly, no HTTP hop
     >    needed since it's the same service.
> 4. New REST layer: `ChartingController` (`/api/charts/drawings`, `/api/charts/layouts`) and
     >    `IndicatorController` (`/api/indicators/configs`, `/api/indicators/{symbol}/compute`).
>
> These entities are per-user — make sure `userId` (from the JWT principal forwarded by the Gateway, not a
> locally-resolved user) scopes every query, the same way `PriceAlert` does in `alert-service`.

**Expected output:** Chart drawings/indicator configs persist server-side, and indicator math runs against
real stored OHLCV data.

---

### Step 8: Notification Service — Real Kafka Fan-Out (Closes Step 7 of the Main Guide)

**Goal:** `notification-service` is an empty shell. `alert-service`'s email/Telegram channels are
deliberate stubs with comments pointing exactly here — this step is already fully specified by their
Javadoc, just execute it.

**Prompt:**

> In `services/notification-service`, port from `desktop/.../service/`:
>
> 1. `NotificationService.java` — the SMTP email-sending logic (uses `spring-boot-starter-mail`, already a
     >    dependency pattern you can copy into this service's `pom.xml`).
> 2. `service/alert/TradingTelegramBot.java` — Telegram Bot API integration (uses the `telegrambots` /
     >    `telegrambots-spring-boot-starter` libraries, same story).
> 3. Add a Kafka consumer for the `alerts.triggered` topic (published by `alert-service`'s
     >    `AlertEventPublisher`, per Step 4.75) and, per Step 7 of the main guide, also consume trade-executed
     >    events — add a Kafka producer in `trading-service`'s `TradeService` (Step 1) for `trades.executed` /
     >    `trades.closed` if it doesn't already publish one.
> 4. Implement `EmailNotificationChannel` and `TelegramNotificationChannel` for real here (same interface
     >    name/shape as the stubs in `alert-service` — that's intentional, they're the production versions of the
     >    same contract).
> 5. Once this compiles and the Kafka consumer is verified end-to-end, replace the bodies of
     >    `alert-service/.../notification/EmailNotificationChannel.java` and `TelegramNotificationChannel.java`
     >    with either (a) a straight delete + Kafka-only publish (alert-service no longer sends directly, only
     >    publishes the event notification-service already consumes), or (b) leave them as pure logging stubs for
     >    local dev — pick (a) for production correctness, note the choice in a commit message.

**Expected output:** Alerts and trade events trigger real emails/Telegram messages via
`notification-service`, and `alert-service` no longer has duplicate, half-implemented sending logic.

---

### Step 9: Identity Service — Profile, Settings, Admin

**Goal:** `identity-service` handles login but nothing else identity-adjacent that the desktop app has.

**Prompt:**

> In `services/identity-service`, port from `desktop/.../`:
>
> 1. `model/UserProfile.java` + `repository/UserProfileRepository.java`.
> 2. `service/ProfilePersistenceService.java` and `service/ProfileMigrationRunner.java` — profile
     >    read/write and any migration-on-login logic.
> 3. `service/AppSettingsService.java` — app-level settings persistence.
> 4. Business logic behind `controller/ProfileSettingsController.java` and
     >    `controller/AdminUserManagementController.java` (both JavaFX view controllers — port their underlying
     >    service calls, not the FXML-facing code).
> 5. New REST layer: `ProfileController` (`GET/PUT /api/profile`), `AdminController`
     >    (`GET /api/admin/users`, `PUT /api/admin/users/{id}/role`, etc. — gate with the existing RBAC/JWT scopes
     >    from `SecurityConfig`, admin-only).
>
> Reuse the existing `AppUser`/`RolePermission` entities already in this service — `UserProfile` should
> reference `AppUser`'s ID, not duplicate its fields.

**Expected output:** Profile and admin screens have a real backend; `identity-service` now fully owns the
user aggregate.

---

### Step 10: Trading Service — Reporting & Export

**Goal:** Close the last unassigned domain from Part 2.

**Prompt:**

> In `services/trading-service`, port from `desktop/.../`:
>
> 1. `service/export/ExcelExportService.java` (uses Apache POI — add `poi-ooxml` to this service's
     >    `pom.xml`).
> 2. Business logic behind `controller/YearlyProfitController.java` and
     >    `model/fundamental/YearlyFinancialRow.java` (move the entity into
     >    `trading-service/.../model/` since it's trade/portfolio-derived, not truly a "fundamentals" concept —
     >    despite living in the `model/fundamental/` package in the monolith).
> 3. New `ReportingController`: `GET /api/reports/yearly`, `GET /api/reports/export.xlsx`. For any
     >    fundamentals data the yearly report needs (e.g. company financials context), call
     >    `reference-data-service`'s `FundamentalsProvider` API from Step 4 over HTTP — don't duplicate that
     >    logic here.

**Expected output:** Export/reporting works against real trade data, calling out to `reference-data-service`
only for the fundamentals it doesn't own.

---

## Part 4 — Phase 2 (P1): Wire It Together, Then Shrink the Desktop Client

Do these only after Phase 1's services actually hold real data — there's nothing useful to shrink the
desktop app toward otherwise.

### Step 11: Gateway Routes for Every Service

**Prompt:**

> In `infra/gateway-service`, fill in the route-stub list left in Step 1 of the main guide. Add a route per
> service built in Phase 1: `trading-service` (`/api/trades/**`, `/api/portfolio/**`, `/api/reports/**`),
> `market-service` (`/api/market/**`, `/api/charts/**`, `/api/indicators/**`), `reference-data-service`
> (`/api/reference/**`), `ai-service` (`/api/ai/**`), `identity-service` (`/api/auth/**`, `/api/profile/**`,
> `/api/admin/**`), `alert-service` (`/api/alerts/**`). Confirm `GatewayJwtAuthFilter` applies to all of them
> except the login/register paths under `/api/auth/**`.

**Expected output:** Every new endpoint from Phase 1 is reachable through the Gateway on one host:port.

---

### Step 12: Shrink the JavaFX Desktop Client to a Thin API Client

**Goal:** This is item 4 from your original ask — now that the backend actually has something to call, do
it.

**Prompt:**

> Refactor `desktop/src/main/java/com/mst/matt/tradingplatformapp/`:
>
> 1. Replace direct `@Autowired` service injection in every `controller/*.java` (JavaFX view controller)
     >    with calls through a new `desktop/.../client/` package of typed HTTP clients (`RestTemplate` or
     >    `WebClient`) hitting the Gateway — one client class per backend domain (`TradeApiClient`,
     >    `MarketApiClient`, `AlertApiClient`, `ReferenceDataApiClient`, `AiApiClient`, `IdentityApiClient`).
> 2. Delete the now-redundant local business logic: everything under `service/` that was ported in Phase 1
     >    (i.e., all of `service/price/`, `service/marketdata/`, `service/fundamental/`, `service/analysis/`,
     >    `service/ai/`, `TradeService.java`, `ChartDrawingService.java`, `BrokerImportService.java`,
     >    `NotificationService.java`, `service/alert/*`, `ProfilePersistenceService.java`,
     >    `ProfileMigrationRunner.java`, `AppSettingsService.java`, `export/ExcelExportService.java`). Keep
     >    `service/auth/AuthService.java` only as a thin wrapper that calls `identity-service`'s
     >    `/api/auth/login`, not the local JWT logic.
> 3. Delete all JPA entities in `model/` and `repository/` — the desktop app no longer touches a database
     >    directly. Keep only DTOs it needs for deserializing API responses (these can be the same DTO classes
     >    from `shared/contracts` if you add that as a desktop dependency, avoiding duplication).
> 4. Strip `desktop/pom.xml` of: `spring-boot-starter-data-jpa`, `postgresql`, `sqlite-jdbc`,
     >    `hibernate-community-dialects`, `spring-boot-starter-mail`, `telegrambots`,
     >    `telegrambots-spring-boot-starter`, `poi-ooxml`, `ta4j-core`. Keep `javafx-*`, `lombok`,
     >    `spring-boot-starter` (for DI/config only), and add a lightweight HTTP client dependency if not already
     >    present (`spring-boot-starter-webflux` for `WebClient`, or keep `RestTemplate` with just
     >    `spring-boot-starter-web`).
> 5. Add a STOMP/WebSocket client (pattern from Step 8 of the older `Migration_Guide_1.md`/main guide) for
     >    live price ticks from `market-service` and in-app alert notifications from `alert-service`.
> 6. Do this **one controller/domain at a time**, starting with `LoginController`/`RegisterController` (→
     >    `identity-service`) and `MainDashboardController`/`TradeEntryController` (→ `trading-service`), since
     >    those are the ones most other screens depend on.
>
> DO NOT do this in a single pass — one domain per prompt, verify the screen still works against the live
> backend before moving to the next.

**Expected output:** `desktop/pom.xml` shrinks noticeably (no DB drivers, no mail/Telegram/POI/ta4j
libraries); the app becomes a pure JavaFX + HTTP/WebSocket client; measure and report the before/after JAR
size.

---

### Step 13: Cross-Service Event & Idempotency Audit

**Goal:** Per the architecture doc's Section 6 ("Consistency, Transactions, and Correctness") — now that
real events flow (trade executed, alert triggered), verify the correctness rules were actually followed,
not just assumed.

**Prompt:**

> Audit `trading-service`, `alert-service`, and `notification-service`:
>
> 1. Confirm every externally-submitted command (`POST /trades`, `POST /alerts`) accepts/generates an
     >    idempotency key and rejects duplicate submissions.
> 2. Confirm Kafka publishing from `trading-service`/`alert-service` uses the outbox pattern (write to DB +
     >    outbox table in one local transaction, separate poller/publisher relays to Kafka) rather than a direct
     >    dual-write to DB and Kafka in the same request thread.
> 3. Add a reconciliation job stub in `trading-service` comparing local `Trade` records against BitUnix's
     >    order/position endpoints (via `TradingProvider`) on a schedule, logging discrepancies (full auto-repair
     >    logic can be a later step — just get detection in place).
>
> This is an audit + fix pass, not new features — flag anything found, fix the idempotency and outbox gaps
> now, leave reconciliation auto-repair as a follow-up ticket if it's too large to fit in one prompt.

**Expected output:** A short written audit result (what was already correct, what was fixed) plus the
actual code fixes for idempotency/outbox gaps.

---

## Part 5 — Phase 3 (P2): Only After Phase 1 & 2 Are Verified Working

These overlap with Phase E of `MTP_Microservices_Migration_Guide.md` (Steps 15–17) — don't duplicate that
work, just note the one new item Phase 1 introduced:

### Step 14: Move Every Service Off H2 Onto Real Postgres-Per-Service

**Prompt:**

> `trading-service` (and any other service you defaulted to H2 during Phase 1 for speed) currently uses an
> in-memory H2 database (`ddl-auto: create-drop`, data lost on restart). Per the architecture doc's database
> strategy (Section 3, "database per service"), give each stateful service — `trading-service`,
> `market-service`, `identity-service`, `alert-service` — its own PostgreSQL database/schema, add Flyway or
> Liquibase migrations (don't rely on `ddl-auto` in anything beyond local dev), and update
> `docker-compose.yml` (Step 15 of the main guide) to spin up one Postgres container per service, or one
> container with per-service schemas/users if you'd rather not run four Postgres instances locally.

**Expected output:** No service loses data on restart; each has its own schema; migrations are version
controlled.

---

## Appendix A — Suggested Prompt Order (Priority)

| #    | Step                                                         | Depends on                  | Priority                                             | Status (V2.0.4)                                         |
| ---- | ------------------------------------------------------------ | --------------------------- | ---------------------------------------------------- | ------------------------------------------------------- |
| 1    | Trading Service — trades/portfolio/API                       | —                           | P0                                                   | ✅ Done                                                  |
| 2    | Market Service — OHLCV storage & symbols                     | —                           | P0                                                   | ✅ Done                                                  |
| 3    | Market Service — multi-provider OHLCV                        | Step 2                      | P0                                                   | ⚠️ Registry built, not wired — Part 0 Gap 1              |
| 3b   | **(new)** Wire Gap 1                                         | Step 3                      | **P0 — do next**                                     | Not started                                             |
| 4    | Reference Data — fundamentals/news/sentiment/calendar/search | —                           | P0                                                   | ✅ Done                                                  |
| 5    | Reference Data — NFT/DeFi                                    | Step 4 (shared config)      | P1                                                   | ✅ Done                                                  |
| 6    | AI Service — analysis/news synthesis                         | Step 4 (calls NewsProvider) | P0                                                   | ⚠️ News done, signals blocked — Part 0 Gap 2             |
| 6b   | **(new)** Wire Gap 2                                         | Step 6                      | **P0 — do next**                                     | Not started                                             |
| 7    | Market Service — charting/indicators                         | Step 2 (needs OHLCV)        | P0                                                   | ⚠️ Backend done, no REST layer — Part 0 Gap 3            |
| 7b   | **(new)** Wire Gap 3                                         | Step 7                      | **P0 — do next**                                     | Not started                                             |
| —    | **(new)** Redis L2 cache                                     | Step 3b, Step 4             | P1                                                   | Not started                                             |
| 8    | Notification Service — real send                             | Step 1 (trade events)       | P0                                                   | Not started                                             |
| 9    | Identity Service — profile/settings/admin                    | —                           | P1                                                   | Not started                                             |
| 10   | Trading Service — reporting/export                           | Step 1, Step 4              | P1                                                   | Not started                                             |
| 11   | Gateway routes                                               | Steps 1–10                  | P0 (do right after each service gets its controller) | Not started                                             |
| 12   | Desktop client shrink                                        | Steps 1–11                  | P1                                                   | Not started                                             |
| 13   | Event/idempotency audit                                      | Step 1, Step 8              | P1                                                   | Not started (`POST /trades` has no idempotency key yet) |
| 14   | Postgres-per-service                                         | All above                   | P2                                                   | Not started (all services still on H2)                  |

**Do next, in this order:** 3b → 6b → 7b → Redis → 8 → 9 → 10 → 11 → 12 → 13 → 14. Steps 3b/6b/7b are
short — they're wiring, not new business logic — and unblock everything asset-data-related downstream
(charting can't show real prices without 3b; AI signals can't work without 6b).

## Appendix B — General Rules for Token Efficiency (unchanged from `Migration_Guide_1.md`)

| Rule                         | Why                                                          |
| ---------------------------- | ------------------------------------------------------------ |
| One task per prompt          | Prevents redundant/hallucinated code                         |
| Reference exact source paths | Every prompt above gives real `desktop/...` paths — use them, don't let the agent guess |
| Use "DO NOT" constraints     | Prevents scope creep into steps not yet reached              |
| Ask for code only            | Skip explanations unless you need them                       |
| Verify before advancing      | Compile + hit the endpoint before the next step              |

## Appendix C — `.cursorrules` Addendum (append to the existing file, don't replace it)

```text
Migration-completion rules (Migration_Guide_3):
- desktop/controller/*.java are JavaFX FXML view controllers, NOT REST controllers. Never port them
  1:1 into a microservice as a @RestController — write a new REST controller per service instead, then
  refactor the JavaFX controller (Step 12) to call it over HTTP.
- Charting/indicator/drawing domain lives in market-service (charting sub-package), not a new service.
- On-chain/NFT/DeFi data lives in reference-data-service under NftDataProvider (extend DTOs as needed,
  don't create a new microservice for it).
- Email/Telegram sending logic lives ONLY in notification-service. alert-service publishes events;
  it does not send notifications directly once Step 8 is complete.
- Every external provider integration (AlphaVantage, CoinGecko, Finnhub, etc.) must be wired through
  the existing ProviderRegistry<T> + Resilience4j pattern already established in market-service's
  BitUnix integration and reference-data-service's NoOpProviderConfig — never call a provider SDK/HTTP
  client directly from a controller or another service's code.
- Before marking any Phase 1 step "done," confirm the corresponding NoOp*/mock bean is still present
  as a registered fallback in the registry, not deleted — only its priority should drop below the real
  implementation.
```