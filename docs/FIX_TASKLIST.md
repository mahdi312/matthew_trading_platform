# Trading Platform — Fix Task List

**Created:** 2026-05-19  
**Purpose:** Master checklist for fixing the project one task at a time.  
**How to use:** Tell the agent: *"Do task T-XX"* (or the task title). Mark status here when done.

---

## Where we stopped

| Item | Status |
|------|--------|
| Full codebase audit | Done |
| `mvn compile` | Passes |
| `mvn test` (excluding live smoke) | Passes — all non-live tests green |
| Task list file | This document |
| Fix tasks executed | Completed |

### Current test status

Run: `mvn test -Dtest="!LivePriceApiSmokeTest"`

| Test scope | Status |
|------|--------|
| Unit + Spring context tests | **green** — all passing locally |
| `LivePriceApiSmokeTest` | **intentionally skipped by default**; enable with `-Dlive.api.tests=true` |

---

## Task index (quick reference)

| ID | Priority | Title | Status |
|----|----------|-------|--------|
| T-01 | P0 | Remove secrets from repo; use env / example config | **done** |
| T-02 | P1 | Fix `SymbolNormalizer.forBinance` bare-ticker bug | **done** |
| T-03 | P1 | Fix Spring Boot `contextLoads` test | **done** |
| T-04 | P1 | Align `BinanceService` with normalizer + re-run all unit tests | **done** |
| T-05 | P2 | Forex fallback providers (registry vs implementation) | **done** (Option A — 5 thin services added) |
| T-06 | P2 | Unify CoinGecko property keys (`coingecko-key` vs `coingecko.key`) | **done** (canonical `api.coingecko-key`) |
| T-07 | P2 | Wire `MarketApiProperties` to Spring (`@EnableConfigurationProperties`) | **done** |
| T-08 | P2 | Add unit tests for paid API price services (mocked HTTP) | **done** |
| T-09 | P2 | Add unit tests for fundamental services | **done** (router tests; per-service mocked tests deferred) |
| T-10 | P3 | Fundamentals UX for crypto / unsupported symbols | **done** |
| T-11 | P3 | Trade Journal: Delete trade button | **done** |
| T-12 | P3 | Settings: customizable ticker watchlist | **done** |
| T-13 | P3 | Profile delete / rename (optional) | **done** |
| T-14 | P3 | Chart: show active data provider in UI | **done** |
| T-15 | P3 | COMMODITY / INDEX routing in `PriceRouter` / `AssetClassDetector` | **done** |
| T-16 | P4 | Add missing `docs/API_PROVIDERS.md` | **done** |
| T-17 | P4 | Update `FUNCTIONALITIES.md` & `Features.md` (Settings, Fundamentals nav) | **done** |
| T-18 | P4 | Trim or relocate giant `Trading Journal & Analytics Desktop App.md` | **done** (moved to `docs/archive/`) |
| T-19 | P5 | `dark-theme.css` — resolve `/*todo!*selected replaced*!*/` | **done** |
| T-20 | P5 | jpackage: disable `winconsole` for release builds | **done** (`-Dwinconsole=true` re-enables) |
| T-21 | P5 | Live API smoke test docs + CI-friendly profile | **done** (documented in `docs/API_PROVIDERS.md`) |
| T-22 | P5 | Excel export: include fundamentals sheet (optional) | **done** |
| T-23 | P5 | Rate-limit / backoff for Alpha Vantage & Finnhub | **done** (`HttpJsonClient.throttle`) |

---

## P0 — Security & configuration

### T-01 — Remove secrets from repo; use env / example config

**Problem:** `application.properties` contains real API keys, SMTP password, and Telegram token in plain text (committed).

**Files:**
- `src/main/resources/application.properties`
- Add: `src/main/resources/application.properties.example`
- Add: `.gitignore` entries if needed (`application-local.properties`)

**Work:**
1. Move all secrets to `application-local.properties` (gitignored) or environment variables.
2. Leave placeholders in `application.properties.example`.
3. Document setup in `README.md` or `docs/CONFIG.md`.
4. **Rotate exposed keys** (they are in git history).

**Done when:** No live credentials in tracked files; app loads keys from local override or env.

---

## P1 — Tests & symbol normalization (blocking CI)

### T-02 — Fix `SymbolNormalizer.forBinance` bare-ticker bug

**Problem:** `forBinance("BTC")` returns `"BTC"` because the guard `s.endsWith("BTC")` matches the symbol itself, not a quote suffix.

**Files:**
- `src/main/java/.../service/price/SymbolNormalizer.java`
- Tests already exist: `SymbolNormalizerTest`, `BinanceServiceTest`

**Suggested fix:** Only treat as “already paired” when suffix is a *quote* currency (USDT, BUSD) or length > 5 (e.g. `ETHBTC`), not when the whole symbol equals a base like `BTC`.

**Done when:** `SymbolNormalizerTest` and `BinanceServiceTest` pass.

---

### T-03 — Fix Spring Boot `contextLoads` test

**Problem:** `@SpringBootTest` fails to load context (bean `resolveNotUnique`).

**Files:**
- `src/test/java/.../TradingPlatformAppApplicationTests.java`
- Possibly `config/*`, JavaFX bootstrap beans

**Options:**
- Use `@SpringBootTest(properties = "spring.main.web-application-type=none")` + exclude JavaFX stage beans in test profile, or
- `@Import` only service-layer slice, or
- `@MockBean` for `StageInitializer` / FxWeaver-heavy beans.

**Done when:** `TradingPlatformAppApplicationTests.contextLoads` passes.

---

### T-04 — Re-run full unit test suite

**Work:** After T-02 and T-03: `mvn test -Dtest="!LivePriceApiSmokeTest"` — all green.

**Done when:** 0 failures, 0 errors (smoke test may stay `@Disabled` unless `-Dlive.api.tests=true`).

---

## P2 — Market data & fundamentals (backend)

### T-05 — Forex fallback providers (registry vs implementation)

**Problem:** `PriceProviderRegistry` lists `FIXER`, `FREE_CURRENCY_API`, `OPEN_EXCHANGE_RATES`, `EXCHANGE_RATE_API`, `CURRENCY_LAYER` but only `ForexService` (Frankfurter) exists. User selecting those providers in Settings has no effect.

**Options (pick one per task command):**
- **A)** Implement thin `PriceService` classes per provider (keys already in `MarketApiProperties`), or
- **B)** Remove unimplemented enums from UI/registry until implemented, or
- **C)** Single `MultiForexService` that delegates by `getProviderId()` based on profile preference.

**Files:**
- `PriceProviderRegistry.java`, `MarketDataProvider.java`
- New services under `service/price/`
- `ProfileSettingsController.java` (provider list)

**Done when:** Every provider shown in Settings either works or is hidden.

---

### T-06 — Unify CoinGecko property keys

**Problem:** Duplicate keys: `api.coingecko-key`, `api.coingecko.key` (×3). `MarketApiProperties` binds `coingeckoKey` → typically `api.coingecko-key` in Spring Boot relaxed binding.

**Files:**
- `application.properties`, `MarketApiProperties.java`, `CoinGeckoService.java`

**Done when:** One canonical key; demo key documented; service reads it reliably.

---

### T-07 — Verify `MarketApiProperties` binding

**Work:** Confirm `@ConfigurationProperties(prefix = "api")` is enabled (`@EnableConfigurationProperties` on main app or config class). Verify keys like `api.finnhub-key` map to `finnhubKey`.

**Done when:** Integration test or startup log confirms enabled providers match keys in properties.

---

### T-08 — Unit tests for paid API price services

**Missing tests for:** `AlphaVantagePriceService`, `FinnhubPriceService`, `PolygonPriceService`, `TwelveDataPriceService`, `MarketstackPriceService`.

**Pattern:** Same as `BinanceServiceTest` — MockWebServer + JSON fixtures.

**Done when:** Each service has quote/OHLCV parse tests with fixtures.

---

### T-09 — Unit tests for fundamental services

**Files:** `AlphaVantageFundamentalService`, `FinnhubFundamentalService`, `FundamentalRouter`

**Done when:** Mocked JSON → `FundamentalsReport` with yearly rows; router fallback order tested.

---

## P3 — UI & user-facing gaps

### T-10 — Fundamentals UX for crypto / unsupported symbols

**Problem:** Yearly Profit view works for stocks (AAPL); `BTCUSDT` often returns empty from Alpha Vantage / Finnhub.

**Files:** `YearlyProfitController.java`, `FundamentalRouter.java`, FXML

**Work:** Clear message (“Fundamentals not available for crypto — try a stock symbol”), disable table gracefully, optional link to profile default symbol.

**Done when:** User never sees silent empty state without explanation.

---

### T-11 — Trade Journal: Delete trade button

**Problem:** `TradeService.deleteTrade()` exists; no UI.

**Files:** `DashboardController.java`, `DashboardView.fxml` (journal table actions column)

**Done when:** Confirm dialog → delete → table refresh.

---

### T-12 — Settings: customizable ticker watchlist

**Problem:** `LiveTickerService` / `AnalysisService` have hardcoded watchlists; `addToWatchlist()` exists but Settings UI has no editor.

**Files:** `ProfileSettingsView.fxml`, `ProfileSettingsController.java`, `UserProfile` model (may need `watchlist` JSON column), `LiveTickerService`

**Done when:** User can save comma-separated or list UI symbols per profile; ticker bar updates.

---

### T-13 — Profile delete / rename (optional)

**Problem:** Can create profiles via “+ New Profile”; cannot delete or rename.

**Files:** `MainDashboardController.java`, `UserProfileRepository`, cascade rules for trades/alerts/config

**Done when:** Safe delete (with confirmation) and rename from Settings or profile menu.

---

### T-14 — Chart: show active data provider in UI

**Problem:** `PriceRouter` tracks `lastProviderName` but chart may not show which API supplied data.

**Files:** `ChartController.java`, `ChartView.fxml`

**Done when:** Status line shows e.g. “OHLCV via Yahoo Finance” after load.

---

### T-15 — COMMODITY / INDEX asset routing

**Problem:** Trade model supports types; `AssetClassDetector` / `PriceRouter` may not route gold, indices, etc.

**Files:** `AssetClassDetector.java`, `PriceRouter.java`, `TradeEntryController.java`

**Done when:** Documented behavior or Yahoo symbol mapping for common commodities/indices.

---

## P4 — Documentation

### T-16 — Add `docs/API_PROVIDERS.md`

**Problem:** `FUNCTIONALITIES.md` links to `./API_PROVIDERS.md` but file is missing.

**Done when:** Endpoints, keys, fallback order, rate limits documented.

---

### T-17 — Update user docs

**Outdated claims:**
- “Settings placeholder” — now `ProfileSettingsView` (asset focus, providers).
- Nav includes **Fundamentals** (`YearlyProfitView`).

**Files:** `docs/FUNCTIONALITIES.md`, `docs/Features.md`

---

### T-18 — Trim duplicate spec markdown

**Problem:** `Trading Journal & Analytics Desktop App.md` (~7000 lines) duplicates repo; confuses audits.

**Done when:** Archived to `docs/archive/` or deleted; single source of truth in `docs/`.

---

## P5 — Polish & release

### T-19 — Dark theme CSS todo

**File:** `src/main/resources/css/dark-theme.css` line ~332 — `/*todo!*selected replaced*!*/`

**Done when:** Selected nav/table styles verified; comment removed.

---

### T-20 — jpackage release settings

**File:** `pom.xml` — `winconsole>true</winconsole>` shows console window on Windows.

**Done when:** `false` for release profile; document debug profile with console.

---

### T-21 — Live API smoke tests

**File:** `LivePriceApiSmokeTest.java` — gated by `-Dlive.api.tests=true`

**Done when:** Documented in FIX_TASKLIST / README; optional GitHub Actions job.

---

### T-22 — Excel export fundamentals sheet (optional)

**Files:** `ExcelExportService.java`, `ExportController.java`

**Done when:** Optional 5th sheet with last loaded fundamentals snapshot.

---

### T-23 — API rate limiting

**Problem:** Alpha Vantage free tier = 5 req/min; rapid fundamental + chart calls may hit limits.

**Work:** Simple throttle or cache in `HttpJsonClient` / per provider.

**Done when:** No burst failures during normal UI use.

---

## Suggested order (if you want a default path)

1. **T-02 → T-03 → T-04** (green tests)  
2. **T-01** (security — do soon if repo is shared)  
3. **T-05, T-06, T-07** (market data correctness)  
4. **T-10, T-11, T-12, T-14** (UI gaps users notice)  
5. **T-08, T-09, T-16, T-17** (quality + docs)  
6. **T-19–T-23** (polish)

---

## Commands reference

```powershell
cd "d:\@Projects\@Copy_Cup\TradingPlatformApp_FIXED\fixed"

# Compile only
mvn -q compile -DskipTests

# Unit tests (no live network)
mvn test -Dtest="!LivePriceApiSmokeTest"

# Live smoke (internet + real keys)
mvn test -Dtest=LivePriceApiSmokeTest -Dlive.api.tests=true

# Run app
mvn javafx:run
# or
mvn spring-boot:run
```

---

## Audit notes (for context)

- **Settings:** Implemented as Profile Settings (`ProfileSettingsController`), not a placeholder.
- **Fundamentals:** Nav item + `YearlyProfitController` + `FundamentalRouter` (Alpha Vantage, Finnhub).
- **Implemented price services:** Binance, CoinGecko, Yahoo, Forex (Frankfurter), Alpha Vantage, Finnhub, Polygon, Twelve Data, Marketstack.
- **Not implemented as separate services:** Fixer, FreeCurrencyAPI, OpenExchangeRates, ExchangeRate-API, CurrencyLayer (listed in enum/registry only).
- **Compile:** OK as of audit date.
- **Agent session:** Stopped after writing this file; **no code fixes applied yet**.

---

*Last updated: 2026-05-19 — update Status column as tasks complete.*



# Feature Clarifications & UX Improvements

## 1. Chart Data Aggregation – On‑Demand & Timeframe‑Aware

**Current Behaviour:**  
Market data aggregation (OHLCV fetching, indicator computation, and analysis pipeline) may run for all symbols or multiple timeframes simultaneously, consuming unnecessary API quota and CPU resources.

**Desired Behaviour:**  
Aggregation should be **triggered only for the chart that is currently open** and strictly **based on its selected timeframe**.

### How It Should Work

- When a user opens the **Live Chart** view, the system starts aggregating data **only for that symbol and timeframe** (e.g., `BTCUSDT` + `1h`).
- When the user **changes the timeframe** (e.g., from `1h` to `15m`), the aggregation engine switches to the new timeframe and begins fetching/polling accordingly.
- **Background polling/refresh** (e.g., every 60 seconds) should respect the active timeframe – shorter timeframes refresh more frequently than longer ones.
- If the user **navigates away** from the chart (e.g., to Dashboard or Trade Journal), aggregation should **pause or stop entirely** for that symbol/timeframe.
- When the user **returns** to the chart, the latest cached data should be displayed immediately, and a fresh fetch should occur only if the cache is stale (based on timeframe-specific TTL).
- **Multiple chart instances** (if supported in future) would each have their own independent aggregation cycle.

### Benefits

- **Reduced API load** – Fewer unnecessary calls to external providers.
- **Lower resource usage** – CPU and memory are used only when the user is actively viewing a chart.
- **Faster UI responsiveness** – The system is not competing with background tasks.
- **Cost efficiency** – Rate limits and paid API tiers are consumed only for data the user actually needs.

---

## 2. Notification Overlay & Drawing Tools – Cleaner, Non‑Intrusive Layout

**Current Problem:**  
The notification overlay and chart drawing toolbar take up excessive screen space and obscure the price action, making the chart nearly unusable.

**Desired Behaviour:**  
Both UI elements should be **minimal, repositionable, and collapsible** so that the chart remains the primary focus.

### Notification Overlay

- **Design:** A subtle, semi‑transparent **banner or toast** that appears at the top‑right or bottom‑right corner of the chart area.
- **Content:** Shows only the latest alert message (e.g., *"BTCUSDT broke above 65,000"*) with a small **dismiss (✕)** button.
- **Multiple alerts:** Stack vertically, but limit to **3 visible** with a "View All" link that opens the Alert Manager.
- **Auto‑dismiss:** Alerts fade out after 5–10 seconds unless the user hovers over them.
- **Collapsible:** A small **bell icon** on the chart toolbar toggles the overlay on/off.

### Drawing Tools Toolbar

- **Design:** A compact **vertical toolbar** on the left‑hand side, no wider than **40–48 pixels**.
- **Icons only:** Each tool is represented by a small icon (16×16 or 20×20 pixels) – no text labels.
- **Grouping:** Related tools are grouped into **dropdown flyouts** (see point 3 below).
- **Collapsible:** A small **arrow** at the top of the toolbar collapses it to a thin strip, showing only a "show" button.
- **Draggable:** The entire toolbar can be **dragged** to any corner of the chart (top‑left, top‑right, bottom‑right) – the user chooses where it sits.
- **Auto‑hide:** Optionally, the toolbar can **auto‑hide** when the mouse leaves the chart area and reappear on hover.

### Benefits

- **Unobstructed chart view** – Users see the full price action and candlesticks.
- **Reduced visual clutter** – Only essential elements remain visible.
- **User‑controllable** – The user decides where and when to show/hide overlays.

---

## 3. Drawing Tools – Grouped Dropdowns with Tooltips

**Current Problem:**  
Many drawing tools are similar in type and clutter the toolbar. Users have to remember each icon's purpose without any text labels.

**Desired Behaviour:**  
Tools are **grouped by category** into dropdown menus. Each tool icon shows a **tooltip** (name) on hover.

### Proposed Grouping

| Group | Tools Included | Icon / Label |
|-------|----------------|--------------|
| **Line** | Trend Line, Ray, Extended Line, Horizontal Line, Vertical Line | 📐 |
| **Fib Suite** | Fib Retracement, Fib Extension, Fib Fan, Fib Time Zones, Fib Channel, Fib Speed Resistance | 🔢 |
| **Positions** | Long Position, Short Position | 📈 / 📉 |
| **Shapes** | Rectangle, Triangle, Ellipse, Parallel Channel, Flat Channel | ▭ |
| **Annotations** | Text Label, Callout, Arrow, Note, Ruler | ✏️ |
| **Utility** | Parallel Lines (copy), Mirror Tool | 🛠️ |

### Interaction Behaviour

- Clicking a group icon **expands a dropdown** showing all tools in that category (e.g., all line tools).
- Selecting a tool from the dropdown sets it as the active drawing tool.
- The group icon remains visible, but the **last‑used tool** in that group is highlighted or displayed as a small badge.
- Hovering over **any tool icon** (group or individual) displays a **tooltip** with the tool name (e.g., *"Fibonacci Retracement"*).
- Keyboard shortcuts can still be assigned to frequently used tools (e.g., `T` for Trend Line, `F` for Fib Retracement).

### Benefits

- **Cleaner toolbar** – 6 groups instead of 17+ individual icons.
- **Discoverability** – Hover tooltips teach the user what each tool does.
- **Faster access** – Users quickly learn which group contains their favourite tools.
- **Scalable** – New tools can be added to existing groups without cluttering the toolbar.

---

## 4. Trade Journal – Note Field Text Colour Fix

**Current Problem:**  
In the **Trade Entry** form (New Trade / Edit Trade), the **Notes** field uses a white font on a white/light background, making the text invisible when the user types.

**Desired Behaviour:**  
The text colour for the `TextArea` (or `TextField`) should be **black (or a dark contrast colour)** so that the user can see what they are typing.

### Implementation Notes

- The Notes field should have:
  - **Text colour:** `#000000` (black) or `#1a1a1a` (dark grey) for optimal readability.
  - **Background:** White or very light grey (e.g., `#f5f5f5`).
  - **Placeholder:** A light grey hint (e.g., *"Add any notes about this trade..."*) that disappears on focus.
- This change should apply consistently across:
  - **New Trade Entry** (`TradeEntry.fxml`)
  - **Edit Trade** (same FXML, populated mode)
  - Any other view that uses the same component (e.g., read‑only note display in Dashboard/Journal tables, if shown).

### Benefits

- **Usability** – Users can immediately see and edit their notes without switching themes or squinting.
- **Accessibility** – High contrast improves readability for all users.
- **Consistency** – Aligns with standard form field behaviour in most desktop applications.

---

## Summary of Changes

| # | Feature | Change |
|---|---------|--------|
| 1 | Chart Aggregation | Fetch & refresh only for the active symbol/timeframe; pause when chart view is closed. |
| 2 | Notification Overlay | Compact, dismissible, auto‑fading, collapsible, and repositionable. |
| 3 | Drawing Toolbar | Grouped dropdowns (6 categories); tooltips on hover for names. |
| 4 | Notes Field | Text colour changed to black for visibility against white background. |

---