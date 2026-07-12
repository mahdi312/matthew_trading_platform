# Matthew Trading Platform — Microservices Migration Guide (Agent-Optimized)

**Target architecture:** Spring Cloud microservices backend + Angular 17 web frontend + standalone JavaFX desktop client (Win/Mac/Linux), all talking to the backend only through the API Gateway.

**How to use this doc with an AI coding agent:**

- Run steps **in order**. Each step = one prompt. Don't batch steps.
- Paste the "Prompt" block verbatim; it already contains scope limits.
- After each step, verify the "Expected Output" before moving on.
- Keep `.cursorrules` (Step 0) loaded at all times — it removes the need to repeat conventions every prompt.

------

## Phase A — Foundation (P0)

### Step 0: Workspace Scaffolding + Agent Rules File

**Goal:** Create the repo skeleton and a persistent rules file so every later prompt is shorter.

**Prompt:**

> Create this top-level structure (empty modules where noted, do not generate business code yet):
>
> ```
> /infra/discovery-service/     (Eureka)
> /infra/gateway-service/       (Spring Cloud Gateway)
> /infra/config-service/        (Spring Cloud Config, optional but scaffold it)
> /services/identity-service/
> /services/market-service/
> /services/trading-service/
> /services/notification-service/
> /shared/contracts/            (shared DTOs/interfaces only, NO entities)
> /frontend/                    (Angular 17 workspace)
> /desktop/                     (copy existing JavaFX source here, unmodified)
> pom.xml                       (root, dependency + version management only)
> .cursorrules
> docker-compose.yml            (empty placeholder, filled in Step 15)
> ```
>
> Populate `.cursorrules` with the content from the "Agent Rules File" section below. DO NOT write service logic, controllers, or entities in this step.

**Expected output:** Folder tree + root `pom.xml` + `.cursorrules` + JavaFX code copied into `/desktop/` untouched.

------

### Step 1: Discovery + Gateway + Config Infra

**Goal:** Stand up Spring Cloud plumbing before any real service exists.

**Prompt:**

> In `/infra/discovery-service`, implement a Spring Cloud **Netflix Eureka** server on **Java 25 + Spring Boot 4.1** (verify these are the actual current stable releases before scaffolding; use the closest stable versions if 25/4.1 aren't GA yet). In `/infra/gateway-service`, implement **Spring Cloud Gateway** with Eureka-based service discovery routing (routes will be added per-service later — leave a route stub list). In `/infra/config-service`, implement a minimal Spring Cloud Config server pointing at a local `/infra/config-repo` folder. All three, and every service built in later steps, target Java 25 / Spring Boot 4.1 — there is no separate "upgrade later" step for this project. DO NOT add authentication/JWT filters yet — that's Step 4.

**Expected output:** Three runnable infra services + one route-stub file the agent will append to later.

------

### Step 2: Shared Contracts Module

**Goal:** Give every service a common vocabulary without coupling their databases.

**Prompt:**

> In `/shared/contracts`, create a Java library module containing ONLY:
>
> - Cross-service DTOs (e.g., `TradeEventDto`, `PriceTickDto`, `UserPrincipalDto`)
> - Broker abstraction interfaces (see Step 5) — interfaces only, no implementations
> - Common enums (`BrokerType`, `InstrumentType`, `OrderSide`, `OrderType`)
>
> DO NOT put JPA `@Entity` classes here — each service owns its own persistence model.

**Expected output:** A dependency-light shared module every service can import.

------

## Phase B — Core Services (P0 → P1)

### Step 3: Identity Service — Multi-Provider OAuth2 + JWT

**Goal:** Central auth supporting username/password, Google, and (later) per-broker connections.

**Prompt:**

> In `/services/identity-service`, implement:
>
> 1. `SecurityConfig` — Spring Security **OAuth2 Client** for Google login, plus a standard username/password path, both issuing stateless JWTs.
> 2. `JwtUtil` / `JwtAuthFilter` — generate/validate tokens; register the same validation filter in `gateway-service` so JWT is checked once, at the edge.
> 3. `AuthController` — `POST /auth/login`, `POST /auth/register`, `GET /auth/oauth2/google/callback`.
> 4. Reuse existing `AppUser` / `RolePermission` entities (copy from the old monolith as-is).
> 5. A `BrokerLinkController` stub (`POST /auth/brokers/{brokerType}/connect`) for later broker API-key/OAuth linking (BitUnix etc.) — implement fully in Step 5, stub only here.
>
> **Note on TradingView:** TradingView does not offer a public consumer "Sign in with TradingView" OAuth flow. It exposes a partner **Broker Integration API** for approved brokers only. Do NOT implement a generic TradingView login button — model it as a broker connection (API key or partner OAuth) under `BrokerLinkController`, and confirm current terms in TradingView's broker-integration docs before building it out.
>
> DO NOT implement broker-specific trading calls here — identity-service only issues identity, not trading permissions.

**Expected output:** Working Google + password login issuing JWTs validated at the Gateway; broker-link endpoint stubbed.

------

### Step 4: Broker Abstraction Layer (Do This Before Any Broker Code)

**Goal:** One unified contract every broker implementation must satisfy — this is the piece both old guides were missing.

**Prompt:**

> In `/shared/contracts`, define these interfaces (used by both `market-service` and `trading-service`):
>
> - `MarketDataProvider`: `getOhlcv(symbol, interval, range)`, `streamLivePrice(symbol)`, `getTickerSnapshot(symbol)`
> - `TradingProvider`: `placeSpotOrder(...)`, `placeFuturesOrder(...)`, `cancelOrder(orderId)`, `getOpenPositions(userId)`, `getBalances(userId)`
> - `BrokerCapabilities`: a metadata object describing what a given broker supports (spot only? futures? max leverage? supported intervals?)
>
> Every future broker (BitUnix now, others later) implements these two interfaces and registers a `BrokerCapabilities` bean. No controller or service should ever call a broker SDK directly — only through these interfaces.
>
> DO NOT implement BitUnix yet — interfaces only.

**Expected output:** Interfaces + a `BrokerRegistry` component that resolves the right implementation by `BrokerType` at runtime.

------



### Step 4.5: Platform-Wide Data Provider Abstraction Layer (Do This Before Step 5)

**Goal:** Step 4 only covers *trading brokers*. Your platform also pulls OHLCV/market data across stock, crypto, and forex, plus NFTs, fundamentals, news, AI analysis, sentiment, and economic-calendar data — all feeding the AI tab, Analysis tab, Trade Journal tab, and others. None of that has an abstraction yet, which is why Cursor starts free-styling provider-specific code per tab once you pass Step 5. Lock this down first.

**Prompt:**

> Extend `/shared/contracts` with a full data-provider abstraction layer, in addition to the broker interfaces from Step 4:
>
> 1. **Extend `InstrumentType`/add `AssetClass`** enum: `STOCK`, `CRYPTO`, `FOREX`, `NFT` — every interface below is parameterized by this.
> 2. **`OhlcvDataProvider`** — generalized historical/live OHLCV, decoupled from any single broker (e.g., Alpha Vantage/Polygon for stocks, OANDA for forex, CoinGecko/Binance for crypto, on top of the broker-tied `MarketDataProvider` from Step 4).
> 3. **`FundamentalsProvider`** — company financials (stocks), tokenomics (crypto), macro indicators (forex).
> 4. **`NftDataProvider`** — collections, floor price, rarity, metadata (e.g., OpenSea/Reservoir).
> 5. **`NewsProvider`** — raw news/article aggregation per symbol or asset class.
> 6. **`AiAnalysisProvider`** — summarization, sentiment scoring, and signal/insight generation over market data + news (wraps whatever LLM/AI backend you use).
> 7. **`SentimentProvider`** — social/crowd sentiment, fear-and-greed style indices.
> 8. **`EconomicCalendarProvider`** — macro events (critical for forex, relevant to all).
> 9. **`SymbolSearchProvider`** — unified symbol search across asset classes and providers.
>
> All interfaces return **normalized DTOs** defined in `/shared/contracts` — no consumer (AI tab, Analysis tab, Journal tab, etc.) should ever see a provider-specific response shape.
>
> **Registry + resilience:** Build a generic `ProviderRegistry<T>` resolving implementations by `(AssetClass, ProviderName)`, supporting a **priority/fallback chain** (e.g., if Provider A's OHLCV call fails, fall back to Provider B) via **Resilience4j** circuit breakers — one per provider, not global.
>
> **New service homes (add now, alongside existing ones from Step 0):**
>
> - `/services/reference-data-service` — hosts `FundamentalsProvider`, `NftDataProvider`, `NewsProvider`, `SentimentProvider`, `EconomicCalendarProvider`, `SymbolSearchProvider` implementations.
> - `/services/ai-service` — hosts `AiAnalysisProvider` implementations (AI tab + Analysis tab's AI-driven insights + trade-journal critique later).
>
> **Tab-to-abstraction mapping (for your own reference, not code yet):**
>
> | Tab                      | Consumes                                                     |
> | ------------------------ | ------------------------------------------------------------ |
> | AI tab                   | `AiAnalysisProvider`, `NewsProvider`, `SentimentProvider`    |
> | Analysis tab             | `OhlcvDataProvider`, `FundamentalsProvider`, `AiAnalysisProvider` |
> | Trade Journal tab        | internal journal service + normalized trade history from `TradingProvider`(s) from Step 4, optionally enriched via `AiAnalysisProvider` |
> | NFT tab                  | `NftDataProvider`                                            |
> | Fundamentals panel       | `FundamentalsProvider`                                       |
> | Economic calendar widget | `EconomicCalendarProvider`                                   |
>
> DO NOT implement any real provider yet (BitUnix starts in Step 5; other providers get their own steps later). Only build: the interfaces, the normalized DTOs, the `ProviderRegistry<T>` with fallback + circuit breaker wiring, and **one no-op/mock implementation per interface** so the registry and downstream services compile and are testable before real providers exist.

**Expected output:** A complete, provider-agnostic contract layer + resilient registry + two new service shells (`reference-data-service`, `ai-service`), all wired to mock providers — ready for real implementations to slot in one at a time in later steps without touching any tab's consuming code.

------

### Step 4.75: Price Alert Pipeline + Notification Channel Abstraction

**Goal:** The Alerts tab needs a full pipeline — create alert → evaluate against live prices → trigger → notify the user in-app, by email, and via Telegram. Build the contracts and the alert-service now, on top of Step 4/4.5's price abstractions; the real SMTP/Telegram sending backend is wired in Step 7 — this step defines the shape everything talks through so that later step is a pure plug-in.

**Prompt:**

> 1. Create 
>
>    ```
>    /services/alert-service
>    ```
>
>    :
>
>    - `PriceAlert` entity (symbol, assetClass, brokerType/provider, condition — above/below/percent-change, target value, status, userId).
>    - CRUD endpoints: `POST/GET/PUT/DELETE /alerts`.
>    - An evaluation loop/subscriber that consumes live price ticks (from `market-service`'s WebSocket/Kafka price stream via the `MarketDataProvider`/`OhlcvDataProvider` abstractions from Steps 4/4.5 — do NOT call any broker or provider SDK directly here).
>    - On condition match, publish an `AlertTriggeredEvent` to Kafka (topic: `alerts.triggered`) and mark the alert as fired (avoid re-firing on every tick — one-shot or cooldown-based).
>
> 2. In 
>
>    ```
>    /shared/contracts
>    ```
>
>    , define a 
>
>    `NotificationChannel`
>
>     interface: 
>
>    ```
>    send(AlertTriggeredEvent event, UserPreferences prefs)
>    ```
>
>    . Define three implementations as separate concerns (real logic lands in Step 7, stub them here so the pipeline compiles end-to-end now):
>
>    - `InAppNotificationChannel` — pushes to the frontend over WebSocket/STOMP (this one CAN be fully implemented now, it doesn't depend on Step 7's Kafka consumer — wire a direct WebSocket broadcast from `alert-service` to subscribed frontend clients for the in-app toast/notification bell).
>    - `EmailNotificationChannel` — stub only (real SMTP wiring in Step 7's `notification-service`).
>    - `TelegramNotificationChannel` — stub only (real Bot API wiring in Step 7's `notification-service`).
>
> 3. In `/frontend`, add a lightweight, global **notification bell / toast component** (not the full `AlertsModule` feature — that's still Step 13) that subscribes to the in-app WebSocket channel and shows a toast + badge count when an alert fires. This can ship now since it only depends on `InAppNotificationChannel`, already real in this step.
>
> DO NOT implement actual SMTP or Telegram Bot API calls in this step — only the interface + stub, consumed for real once `notification-service` exists in Step 7. DO NOT let `alert-service` call broker/provider SDKs directly — only through the registries from Steps 4/4.5.

**Expected output:** Working alert CRUD + live evaluation + real in-app WebSocket notifications end-to-end; email/Telegram channels compile against the same event but no-op until Step 7 fills them in.



### Step 5: Market Service — BitUnix Implementation + Caching + Live Streaming

**Goal:** First concrete broker implementation, built strictly against Step 4's contracts.

**Prompt:**

> In `/services/market-service`:
>
> 1. Implement `BitUnixMarketDataProvider implements MarketDataProvider`, following BitUnix's official API docs for OHLCV, ticker, and live price endpoints.
> 2. Add a **Caffeine Cache** in front of `getOhlcv`/`getTickerSnapshot` (short TTL for ticker, longer for historical OHLCV bars) — expose cache stats via Actuator.
> 3. Implement a WebSocket/STOMP controller that streams live OHLCV/price updates to subscribed clients, sourced from BitUnix's WebSocket feed.
> 4. Register `BitUnixMarketDataProvider` in the `BrokerRegistry` from Step 4.
>
> DO NOT implement trading/order logic here — that's `trading-service`.

**Expected output:** Live BitUnix OHLCV + price streaming, cached, discoverable via the shared registry.

------

### Step 6: Trading Service — Spot & Futures, Thread-Safe, Transactional

**Goal:** Order execution with the same abstraction discipline.

**Prompt:**

> In `/services/trading-service`:
>
> 1. Implement `BitUnixTradingProvider implements TradingProvider` per BitUnix's trading API docs (spot + futures order placement, cancellation, balances, positions).
> 2. Wrap all state-mutating operations (`Trade`, `Portfolio`, `Balance` writes) in `@Transactional`, scoped as narrowly as possible (no long-running transactions spanning broker HTTP calls).
> 3. Make the order-execution path thread-safe: use per-user or per-account locking (e.g., `ReentrantLock` keyed by userId+brokerType, or a queue-based executor) so concurrent orders for the same account can't race.
> 4. Register the provider in `BrokerRegistry`.
>
> DO NOT let broker network calls happen inside a `@Transactional` boundary — fetch/validate first, then commit the local transaction, then call the broker (or use the outbox pattern if you need both to be atomic-ish).

**Expected output:** Thread-safe, transactionally-sound spot/futures trading through the unified interface.

------

### Step 7: Notification Service — Kafka Fan-Out

**Goal:** Async notifications across channels, decoupled from trading/market services.

**Prompt:**

> 1. Add Kafka producers in `trading-service` (trade executed/closed events) and `market-service` (price-alert-triggered events).
>
> 2. In 
>
>    ```
>    /services/notification-service
>    ```
>
>    , consume those topics and fan out to:
>
>    - Web/mobile push notifications
>    - Email (SMTP)
>    - Telegram (Bot API)
>
> 3. Each channel is a separate `NotificationSender` implementation behind a common interface, so adding a channel later doesn't touch existing ones.
>
> DO NOT put channel-specific logic (SMTP config, Telegram bot tokens) inside trading-service or market-service — they only publish events.

**Expected output:** Kafka topics + notification-service consuming and dispatching to 3 channels.

------

## Phase C — Cross-Cutting Quality Passes (P1)

*(These were missing as explicit steps in both original guides — do them as dedicated passes, not "along the way," so they don't get skipped.)*

### Step 8: Centralized Structured Logging

**Prompt:**

> Add structured JSON logging (e.g., Logback + `logstash-logback-encoder`) to every service. Include a correlation/request ID propagated from `gateway-service` through all downstream calls (MDC + a `X-Correlation-Id` header). Document how logs would ship to a central store (ELK or Loki) — config only, don't stand up the full stack yet.

**Expected output:** Consistent structured logs with correlation IDs across all services.

### Step 9: Cache Verification Pass

**Prompt:**

> Audit every service for read-heavy endpoints not yet cached. Confirm Caffeine is configured with sensible max-size + TTL per use case (ticker data vs. historical OHLCV vs. user profile lookups). Add cache eviction on relevant writes (e.g., invalidate a symbol's ticker cache isn't needed, but invalidate user-profile cache on profile update).

**Expected output:** A short cache-coverage report + any missing `@Cacheable`/`@CacheEvict` additions.

### Step 10: Concurrency / Thread-Safety Audit

**Prompt:**

> Review `trading-service`'s order execution path, `market-service`'s WebSocket broadcast path, and any shared mutable state (in-memory maps, singletons) across all services. Flag and fix anything not thread-safe. Confirm the per-account locking from Step 6 actually prevents double-submission under concurrent requests (add a test).

**Expected output:** Audit notes + fixes + a concurrency test proving no race condition on order placement.

### Step 11: Transactional Boundary Audit

**Prompt:**

> Review every `@Transactional` method across all services: confirm boundaries are as narrow as possible, no broker/network calls happen inside a transaction, and rollback behavior is correct on partial failures (e.g., trade recorded but broker call fails, or vice versa — decide on compensating action or outbox pattern).

**Expected output:** Audit notes + corrected transaction boundaries.

------

## Phase D — Frontend & Desktop (P1)

### Step 12: Angular Workspace + Shared Chart Library

**Prompt:**

> In `/frontend/`, scaffold an Angular 17 standalone-components workspace (Angular Material, RxJS, ECharts). Build a tree-shakable `ChartLibraryModule` with a candlestick chart component supporting theming, indicator overlays (SMA/EMA/RSI/MACD/Bollinger), drawing tools, zoom/pan/crosshair, and WebSocket-driven live updates.
>
> DO NOT build feature modules (dashboard, live trading, journal) yet — chart library only.

**Expected output:** Standalone, reusable `ChartLibraryModule`.

### Step 13: Angular Feature Modules — One at a Time

**Prompt (repeat per module):**

> Build the `{ModuleName}` standalone module. For **LiveTradingModule** specifically: let the user pick a connected broker (BitUnix, etc.), fetch that broker's OHLCV via `market-service`, and place spot/futures orders via `trading-service` — using the `ChartLibraryModule` for display. Repeat for: `DashboardModule`, `LiveTradingModule`, `JournalModule`, `AlertsModule`, `SettingsModule`.
>
> DO NOT duplicate chart rendering code — always consume `ChartLibraryModule`.

**Expected output:** One PR-sized module per prompt.

### Step 14: JavaFX Desktop Client → Thin Client

**Prompt:**

> Refactor `/desktop/` JavaFX code:
>
> 1. Replace local DB/service calls with REST calls to the API Gateway only (never call a microservice directly).
> 2. Add a STOMP WebSocket client for live price updates.
> 3. Update the login screen to support the OAuth2/JWT flow from `identity-service` (Google + password now; broker-linking UI later).
> 4. Keep all network calls off the JavaFX UI thread.
>
> Do this one screen/service at a time, starting with auth, then portfolio/trades.

**Expected output:** JavaFX app fully functioning as a thin client against the microservices stack.

------

## Phase E — Productionization (P2)

### Step 15: Docker & Compose

**Prompt:**

> Create a `Dockerfile` per service and for the frontend. Fill in `docker-compose.yml` to run the full stack: discovery, gateway, config, identity, market, trading, notification, Kafka, Postgres, and frontend.

### Step 16: Kubernetes

**Prompt:**

> Generate K8s manifests (Deployments, Services, Ingress, ConfigMaps/Secrets) for every service, wired to use Eureka or K8s-native service discovery (your call, state which you chose). Include Actuator-based liveness/readiness probes.

### Step 17: Final Compatibility & Optimization Pass

**Prompt:**

> Everything was already built on Java 25 / Spring Boot 4.1 (verify all POMs still pin these — no drift back to older versions in any module, including `/desktop`). Confirm Angular is on its latest 17.x-compatible package set. Re-check that all dependencies (JavaFX, Lombok, Caffeine, Kafka clients, etc.) have versions compatible with Java 25 / Spring Boot 4.1. Provide a build/test report confirming everything still passes end to end.

------

## Priority Order Summary

| Order | Step                                                       | Priority |
| ----- | ---------------------------------------------------------- | -------- |
| 1     | Step 0–2: Scaffolding, infra, shared contracts             | P0       |
| 2     | Step 3: Identity (OAuth2 + JWT)                            | P0       |
| 3     | Step 4: Broker abstraction interfaces                      | P0       |
| 4     | Step 5: Market service (BitUnix + cache + streaming)       | P0       |
| 5     | Step 6: Trading service (thread-safe + transactional)      | P0       |
| 6     | Step 7: Notification service (Kafka)                       | P1       |
| 7     | Step 8–11: Logging, cache, concurrency, transaction audits | P1       |
| 8     | Step 12–13: Angular chart library + feature modules        | P1       |
| 9     | Step 14: JavaFX thin client                                | P1       |
| 10    | Step 15–16: Docker + Kubernetes                            | P2       |
| 11    | Step 17: Final compatibility & optimization pass           | P2       |

------

## General Rules for Token Efficiency

| Rule                                                     | Why                                                          |
| -------------------------------------------------------- | ------------------------------------------------------------ |
| One task per prompt                                      | Prevents redundant/hallucinated code                         |
| Reference existing patterns ("follow XService.java")     | Saves tokens vs. re-explaining conventions                   |
| Use explicit DO-NOT constraints                          | Keeps the agent from scope-creeping into unrelated files     |
| Ask for code only, no prose                              | Skip explanations unless requested                           |
| Keep `.cursorrules` loaded                               | Removes need to restate conventions every prompt             |
| Do cross-cutting audits (Steps 8–11) as dedicated passes | These get silently skipped if left as "along the way" asides |

------

## Agent Rules File (`.cursorrules`)

```text
You are an expert Java/Spring Cloud/Angular/JavaFX developer.

General rules:
- Java 25 + Spring Boot 4.1 from the start (verify these are GA at project start; do not scaffold on older versions with a plan to upgrade later).
- Angular 17+, standalone components only, Angular Material, ECharts, RxJS.
- JavaFX for desktop client — thin client only, no local business logic duplication.
- Lombok for boilerplate; JPA/Hibernate + PostgreSQL per-service.
- Write clean, minimally-commented code. Follow REST conventions. Add OpenAPI/Swagger annotations to controllers.

Microservices rules:
- Eureka for discovery, Spring Cloud Gateway as the single entry point, Spring Cloud Config for shared config.
- No service calls another service directly — only via Gateway or async via Kafka.
- All broker integrations (market data + trading) MUST implement the shared MarketDataProvider / TradingProvider interfaces in /shared/contracts. No direct broker SDK calls outside those implementations.

Security:
- OAuth2 (Google) + JWT (stateless), validated once at the Gateway.
- Broker connections (BitUnix, TradingView-partner, etc.) are separate "broker link" flows, not login providers.

Reliability rules:
- @Transactional boundaries must be narrow; never call an external broker inside a transaction.
- Order execution paths must be thread-safe per user/account (explicit locking or serialized queue).
- Caffeine Cache on all read-heavy, low-volatility endpoints; explicit eviction on relevant writes.
- Structured JSON logging with correlation IDs propagated from the Gateway.

DevOps:
- Kafka for all cross-service events (trade executed, alert triggered) feeding notification-service.
- Docker Compose for local full-stack runs; Kubernetes manifests for deployment.
```









## Provider API Reference — Concrete Implementations per Abstraction Interface

Companion to Step 4.5. For each interface, this shows 1–2 real providers with enough detail (endpoint, auth, request, response shape) to implement a first adapter. Verify current rate limits/pricing before committing to a provider — free-tier limits especially change often.

------

### 1. `OhlcvDataProvider`

#### Alpha Vantage (Stocks)

- **Auth:** query param `apikey=YOUR_KEY` (no header option for this one)
- **Endpoint:** `GET https://www.alphavantage.co/query`
- **Key params:** `function=TIME_SERIES_DAILY`, `symbol=AAPL`, `outputsize=compact|full`, `datatype=json`
- **Free tier:** 25 requests/day, 5/min — thin enough that Caffeine caching (Step 5) is not optional here.
- **Response shape:**

```json
{
  "Meta Data": { "2. Symbol": "AAPL", "5. Time Zone": "US/Eastern" },
  "Time Series (Daily)": {
    "2026-07-10": { "1. open": "210.50", "2. high": "212.10", "3. low": "208.90", "4. close": "211.30", "5. volume": "48000000" }
  }
}
```

- **Adapter note:** keys are numbered strings ("1. open") — normalize into your `OhlcvBar` DTO immediately, don't let this shape leak upstream.

#### Binance (Crypto)

- **Auth:** none required for public market data endpoints (only trading endpoints need `X-MBX-APIKEY` + HMAC signature — not relevant here)
- **Endpoint:** `GET https://api.binance.com/api/v3/klines`
- **Key params:** `symbol=BTCUSDT`, `interval=1h` (1m/5m/1h/1d etc.), `startTime`, `endTime`, `limit` (max 1000)
- **Response shape:** array-of-arrays, position-indexed, not field-named:

```json
[
  [1499040000000, "0.01634790", "0.80000000", "0.01575800", "0.01577100", "148976.11", 1499644799999, "2434.19", 308, "1756.87", "28.46", "0"]
]
```

Index order: open time, open, high, low, close, volume, close time, quote volume, trade count, taker-buy-base, taker-buy-quote, unused.

- **Rate limits:** weight-based, returned in `X-MBX-USED-WEIGHT-1M` response header — track this to avoid 429s.

#### OANDA (Forex)

- **Auth:** `Authorization: Bearer <personal-access-token>` header
- **Endpoint:** `GET https://api-fxpractice.oanda.com/v3/instruments/{instrument}/candles` (practice) or `api-fxtrade.oanda.com` (live)
- **Key params:** `instrument=EUR_USD`, `granularity=M5|H1|D` etc., `count` or `from`/`to`, `price=M|B|A` (mid/bid/ask)
- **Response shape:**

```json
{
  "instrument": "EUR_USD",
  "granularity": "M5",
  "candles": [
    { "time": "2026-07-10T19:35:00.000000000Z", "complete": true, "volume": 132,
      "mid": { "o": "1.0850", "h": "1.0855", "l": "1.0848", "c": "1.0852" } }
  ]
}
```

- **Adapter note:** requires an OANDA account (practice accounts are free) — accountID isn't needed for the candles endpoint itself, only for account-scoped pricing/trading calls.

------

### 2. `FundamentalsProvider`

#### Financial Modeling Prep (Stocks)

- **Auth:** query param `apikey=YOUR_KEY`
- **Endpoint:** `GET https://financialmodelingprep.com/api/v3/income-statement/{symbol}`
- **Key params:** `symbol=AAPL`, `limit=4` (quarters/years)
- **Free tier:** 250 requests/day
- **Response shape:** array of statements — `revenue`, `grossProfit`, `ebitda`, `netIncome`, `eps`, `period`, `calendarYear`, etc.

#### CoinGecko (Crypto tokenomics)

- **Auth:** header `x-cg-demo-api-key` (free) or `x-cg-pro-api-key` (paid) — also acceptable as query params `x_cg_demo_api_key` / `x_cg_pro_api_key`
- **Endpoint:** `GET https://api.coingecko.com/api/v3/coins/{id}` (e.g. `id=bitcoin`)
- **Response shape (trimmed to tokenomics fields):**

```json
{
  "id": "bitcoin",
  "market_data": {
    "current_price": { "usd": 61000 },
    "market_cap": { "usd": 1200000000000 },
    "circulating_supply": 19700000,
    "total_supply": 21000000,
    "max_supply": 21000000
  }
}
```

- **Free tier:** 100 calls/min, 10,000 calls/month on the Demo plan.

#### Forex macro (shared with `EconomicCalendarProvider`)

Forex doesn't have "company fundamentals" — treat macro indicators (GDP, CPI, rate decisions) as its fundamentals equivalent. Same FMP economic-data endpoints as Section 7 below cover this; don't build a separate forex-fundamentals path.

------

### 3. `NftDataProvider`

#### OpenSea API v2

- **Auth:** header `X-API-KEY: your_key` (server-side only — never expose client-side, per OpenSea's own guidance)
- **Collection stats endpoint:** `GET https://api.opensea.io/api/v2/collections/{slug}/stats`
- **Collection metadata endpoint:** `GET https://api.opensea.io/api/v2/collections/{slug}`
- **Response shape (stats):**

```json
{
  "total": { "volume": 850000.5, "sales": 12000, "average_price": 70.8, "floor_price": 12.4, "market_cap": 245000 },
  "intervals": [
    { "interval": "one_day", "volume": 320.1, "sales": 14, "average_price": 22.9 }
  ]
}
```

- **Adapter note:** OpenSea identifies collections by `slug`, not contract address. If your NFT tab lets users search by contract address, resolve to a slug first (via the search endpoint) before calling stats/metadata.

------

### 4. `NewsProvider`

#### NewsAPI.org

- **Auth:** query param `apiKey=YOUR_KEY` or header `X-Api-Key: YOUR_KEY`
- **Endpoint:** `GET https://newsapi.org/v2/everything`
- **Key params:** `q=Bitcoin` (supports boolean AND/OR/NOT and quoted phrases), `from`, `to` (ISO 8601), `language=en`, `sortBy=publishedAt|relevancy|popularity`, `pageSize` (max 100)
- **Response shape:**

```json
{
  "status": "ok",
  "totalResults": 2150,
  "articles": [
    { "source": { "id": "reuters", "name": "Reuters" }, "author": "...", "title": "...",
      "description": "...", "url": "...", "publishedAt": "2026-07-12T14:00:00Z", "content": "... [+1200 chars]" }
  ]
}
```

- **Free tier:** developer plan is limited to a rolling ~1-month article window and is for non-production/dev use only — budget for a paid plan before shipping this to real users.
- **Adapter note:** `content` is truncated (~200 chars on free tier) — don't rely on it for full-text AI summarization; use `url` + a separate fetch/scrape step if you need full text, subject to each source's terms.

------

### 5. `AiAnalysisProvider`

#### OpenAI-compatible Chat Completions (or Anthropic Messages API — same shape of integration)

- **Auth:** header `Authorization: Bearer sk-...`
- **Endpoint:** `POST https://api.openai.com/v1/chat/completions`
- **Request body:**

```json
{
  "model": "gpt-4.1",
  "messages": [
    { "role": "system", "content": "You are a market analysis assistant..." },
    { "role": "user", "content": "Summarize sentiment for BTC given this news and price action: ..." }
  ],
  "temperature": 0.3
}
```

- **Response shape:**

```json
{
  "choices": [ { "message": { "role": "assistant", "content": "..." }, "finish_reason": "stop" } ],
  "usage": { "prompt_tokens": 512, "completion_tokens": 180, "total_tokens": 692 }
}
```

- **Adapter note:** this is the one interface where the "provider" is interchangeable at the prompt/orchestration layer, not just the HTTP layer — keep prompt templates in `ai-service`, not scattered across tabs, so swapping model providers later doesn't mean rewriting prompts in six places.

------

### 6. `SentimentProvider`

#### Alternative.me Crypto Fear & Greed Index

- **Auth:** none — fully public, no API key
- **Endpoint:** `GET https://api.alternative.me/fng/`
- **Key params:** `limit` (default 1, use 0 for full history), `format=json|csv`
- **Response shape:**

```json
{
  "data": [
    { "value": "71", "value_classification": "Greed", "timestamp": "1752192000", "time_until_update": "18716" }
  ]
}
```

- **Rate limit:** 60 requests/min over a 10-minute window; data itself only refreshes every ~5 minutes, so cache accordingly — polling faster gains nothing.
- **Note:** this index is crypto-only (BTC-weighted). For stock-market sentiment (e.g., CNN Fear & Greed style), you'll need a separate stock-specific provider — don't present the crypto index as if it applies to equities/forex in the UI.

------

### 7. `EconomicCalendarProvider`

#### Financial Modeling Prep

- **Auth:** query param `apikey=YOUR_KEY`
- **Endpoint:** `GET https://financialmodelingprep.com/api/v3/economic_calendar`
- **Key params:** `from`, `to` (max 3-month window per request)
- **Response shape:** array of events — `event`, `date`, `country`, `actual`, `previous`, `estimate`, `impact` (typical fields; confirm exact field names against current docs since calendar providers rename these often).
- **Adapter note:** this is the interface most likely to need a second provider for redundancy (e.g., Trading Economics or Forex Factory as fallback) since calendar data quality varies a lot by source — good candidate for the `ProviderRegistry<T>` fallback chain from Step 4.5, not just a single hardcoded call.

------

### 8. `SymbolSearchProvider`

#### Financial Modeling Prep (Stocks/ETFs/Crypto/Forex — broadest single-provider coverage)

- **Auth:** query param `apikey=YOUR_KEY`
- **Endpoint:** `GET https://financialmodelingprep.com/api/v3/search`
- **Key params:** `query=AA`, `limit=10`, `exchange=NASDAQ|NYSE|CRYPTO|FOREX|...`
- **Response shape:**

```json
[ { "symbol": "AAPL", "name": "Apple Inc.", "currency": "USD", "exchangeShortName": "NASDAQ" } ]
```

#### Alpha Vantage (fallback / cross-check)

- **Endpoint:** `GET https://www.alphavantage.co/query?function=SYMBOL_SEARCH&keywords=tesla&apikey=YOUR_KEY`
- Useful as a secondary source in the registry's fallback chain, or for symbols FMP doesn't cover well.

------

### Implementation Checklist (maps back to Step 4.5)

| Interface                  | Primary provider                | Auth style                  | Needs Caffeine cache?                                       | Needs fallback chain?                                   |
| -------------------------- | ------------------------------- | --------------------------- | ----------------------------------------------------------- | ------------------------------------------------------- |
| `OhlcvDataProvider`        | Alpha Vantage / Binance / OANDA | query param / none / Bearer | Yes — Alpha Vantage's 25/day free tier makes this mandatory | Recommended (per asset class)                           |
| `FundamentalsProvider`     | FMP / CoinGecko                 | query param / header        | Yes (low volatility data)                                   | Optional                                                |
| `NftDataProvider`          | OpenSea v2                      | header (`X-API-KEY`)        | Yes (floor price is point-in-time)                          | Optional (Reservoir as backup)                          |
| `NewsProvider`             | NewsAPI.org                     | query param or header       | Short TTL cache                                             | Recommended for production volume                       |
| `AiAnalysisProvider`       | OpenAI/Anthropic                | Bearer                      | No (results are per-query, not cacheable the same way)      | Optional, at the orchestration layer                    |
| `SentimentProvider`        | Alternative.me                  | none                        | Yes (data refreshes every ~5 min)                           | Low priority, single source is fine                     |
| `EconomicCalendarProvider` | FMP                             | query param                 | Yes                                                         | Recommended — field naming varies a lot between vendors |
| `SymbolSearchProvider`     | FMP + Alpha Vantage             | query param                 | Yes                                                         | Recommended                                             |

**Reminder from Step 4.5:** none of these get called directly from a controller or tab service — every one goes through its interface + `ProviderRegistry<T>`, so swapping BitUnix's OHLCV for Binance's, or adding a second NFT provider later, never touches consuming code.

