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

# Chart Drawing & Trade Integration – Complete Feature Specifications

## Overview

This document defines the full specification for chart drawing tools, Fibonacci suites, annotation tools, trade integration, and related UI/UX improvements. It consolidates all corrections, clarifications, and enhancements requested.

---

# Feature Corrections & Clarifications

## 1. Fibonacci Drawing Tools – Incomplete or Faulty Behaviour

**Current Issue:**  
Most Fibonacci‑based drawing tools (Retracement, Extension, Fan, Time Zones, Channel, Speed Resistance) do not render correctly or produce incorrect levels. Some are completely non‑functional.

**Desired Behaviour:**

### Fib Retracement
- User clicks and drags from one swing point (low or high) to another.
- The tool should automatically compute and display the following retracement levels as horizontal lines:  
  `0%`, `23.6%`, `38.2%`, `50%`, `61.8%`, `78.6%`, `100%`.  
  *Optional extensions:* `127.2%`, `161.8%`, `261.8%` (if configured).
- Levels should be labelled with their percentage and price value.
- The user should be able to **drag any level line** vertically to adjust it manually (while the base swing points remain locked).

### Fib Extension
- User selects **three points**: the start of the move, the end of the move, and a retracement/extension point.
- The tool projects **price targets** at: `0.618`, `1.000`, `1.272`, `1.618`, `2.618`, `4.236`.
- Each target is drawn as a horizontal line with a label (e.g., `161.8%`).

### Fib Fan
- User drags from a pivot point to an opposing swing.
- The fan draws diagonal lines radiating from the pivot at angles corresponding to Fibonacci ratios (e.g., 23.6°, 38.2°, 50°, 61.8°, 78.6°).
- Lines should extend to the right edge of the visible chart.

### Fib Time Zones
- User clicks a start point (a significant low/high).
- Vertical bands are drawn at intervals based on the Fibonacci sequence: 1, 2, 3, 5, 8, 13, 21, 34, 55... (in bar counts, not absolute time).
- Bands are semi‑transparent coloured regions, with the first band starting at the click point.

### Fib Channel
- User draws a trendline (the base). The system automatically draws a **parallel channel** with width determined by the Fibonacci ratios (e.g., 0.382, 0.618, 1.000, 1.618) from the base line.
- The channel edges are parallel lines, and the width can be adjusted by dragging the base line’s endpoint.

### Fib Speed Resistance
- User selects two points (a swing low and a swing high).
- The tool draws angled lines from the start point at speeds corresponding to Fibonacci ratios (1×, 2×, 3×, 5×, 8×) relative to the price/time slope.

**All Fibonacci tools must:**
- Snap to OHLC when `Shift` is held during placement.
- Allow **anchor dragging** for fine‑tuning (e.g., move the base swing points).
- Persist across chart reloads.
- Be grouped under the **Fib Suite** dropdown in the toolbar.

---

## 2. Undo / Redo for Drawing Actions – Keyboard Shortcuts

**Current Issue:**  
There is no way to undo or redo drawing actions (creation, deletion, duplication, editing, moving). Users may accidentally delete or modify a drawing and cannot revert.

**Desired Behaviour:**

- **Undo** – `Ctrl+Z` (Windows/Linux) or `Cmd+Z` (macOS) – reverts the last drawing action.
- **Redo** – `Ctrl+Y` (Windows/Linux) or `Cmd+Shift+Z` (macOS) – reapplies the last undone action.

### Supported Actions
- Create drawing (any tool)
- Delete drawing
- Duplicate drawing
- Move drawing (drag anchors)
- Modify drawing (e.g., drag a level line)
- Lock/Unlock drawing
- Instant save / create trade from drawing (these actions should be **excluded** from undo/redo – they are separate operations).

### Implementation Expectations
- Undo/redo stack is **session‑based** (i.e., cleared when the chart view is closed).
- Each action stores enough state to fully restore the previous state of the drawing(s) involved.
- The shortcuts should work globally when the chart has focus.
- A visual indicator (e.g., button) showing that undo/redo is available is optional but recommended.

---

## 3. Circle, triangle , Parallel Channel & Flat Channel – Incorrect Rendering

**Current Issue:**  
The **Circle**, **triangle**, **Parallel Channel** and **Flat Channel** tools either do not draw correctly or produce unexpected shapes (e.g., non‑parallel lines, missing boundaries).

**Desired Behaviour:**

### Parallel Channel
- User draws a **center line** by clicking and dragging a trend line.
- The system automatically creates **two parallel lines** on either side of the center line.
- The user can adjust the **width** of the channel by dragging one of the outer lines (the other outer line moves sympathetically).
- The center line can be dragged to shift the whole channel.
- All three lines are rendered with the same style (colour, dash pattern), and the area between the outer lines may have a semi‑transparent fill.

### Flat Channel
- User draws a **horizontal rectangle** by clicking and dragging a horizontal range.
- The top and bottom edges are perfectly horizontal (price levels are constant).
- The user can drag the top or bottom edge to change the channel height.
- The left and right edges are vertical lines marking the time span.
- The area inside the channel may have a light fill.

### Both Tools
- Must respect snap‑to‑OHLC when `Shift` is pressed.
- Must persist and reload correctly.
- Should be listed under the **Shapes** dropdown group.

---

## 4. Annotation Tools – Text / Label Editing

**Current Issue:**  
The **Note**, **Callout**, and **Text** tools create a static label on the chart, but the user **cannot edit the text** after placement. There is no way to type into them or change the label content.

**Desired Behaviour:**

### Text Label
- After drawing, the label displays a default text (e.g., *"Text"*).
- The user can **double‑click** on the label to enter **in‑place editing mode** – the text becomes editable (a caret appears).
- The user types new text and presses `Enter` to confirm, or clicks away to save.
- The label’s font, size, colour, and background can be modified via a context‑menu option (if implemented).

### Note (Sticky Note)
- A small square/rectangular note with a yellow (or custom) background.
- Double‑clicking opens a **pop‑up text area** where the user can write longer notes.
- The note icon shows a preview of the first few words or a pencil icon.
- The note content is saved and displayed on hover or when expanded.

### Callout
- A text box with a leader line pointing to a specific price/time location.
- Double‑click the text box to edit the text (similar to Text Label).
- The leader line’s endpoint can be dragged to reposition the arrow.
- The text box can be dragged independently, and the leader line updates automatically.

### General Rules for All Annotations
- Editing mode is triggered by **double‑click**.
- The text is saved immediately when editing ends (no separate "Save" button needed – but changes are committed on `Enter` or focus lost).
- The annotation must persist with the updated text across chart reloads.

---

## Summary of Corrections

| # | Issue | Correction |
|---|-------|------------|
| 1 | Fib tools faulty | Fix all Fib tools: retracement, extension, fan, time zones, channel, speed resistance. Ensure accurate levels, snap, and persistence. |
| 2 | No undo/redo | Add `Ctrl+Z` (Undo) and `Ctrl+Y` (Redo) for all drawing actions (create, delete, modify, duplicate, move). |
| 3 | Parallel & Flat channels broken | Fix rendering: parallel channel = 3 parallel lines, flat channel = horizontal rectangle. Both with anchor dragging and snap. |
| 4 | Annotations not editable | Allow double‑click to edit text in Text, Note, and Callout. Save changes immediately. Note uses pop‑up for longer text. |

---

## 1. Chart Data Aggregation – On‑Demand & Timeframe‑Aware

**Requirement:**  
Market data fetching (OHLCV, indicators, analysis) must happen **only for the chart currently open** and **only for its active timeframe**.

- When a user opens the **Live Chart**, aggregation starts for that symbol + timeframe.
- When the user changes the timeframe, the aggregation switches to the new timeframe immediately.
- Background refresh intervals depend on the timeframe (e.g., 1m → every 60s, 1h → every 5 min, 1D → once per day).
- When the user navigates away from the chart (e.g., to Dashboard, Journal), all polling and fetching for that symbol/timeframe **pause or stop** entirely.
- On returning, the latest cached data is shown instantly; a fresh fetch occurs only if the data is stale (based on timeframe TTL).
- **No unnecessary API calls** for symbols/timeframes the user has not loaded.

---

## 2. Drawing Tools – UI & Organisation

### Toolbar Layout
- Compact vertical toolbar on the left side of the chart (max 48px wide).
- **Icons only** (no text labels) to save space.
- Tools grouped into **dropdown flyouts** by category (see table below).
- Hovering over any icon shows a **tooltip** with the tool’s full name.
- The toolbar is **draggable** to any corner of the chart (top‑left, top‑right, bottom‑right) and can be **collapsed** to a thin strip via a small arrow button.

### Tool Groups

| Group | Icon | Tools |
|-------|------|-------|
| **Select / Pan** | ↖ | Selection, pan, and anchor editing |
| **Line** | 📐 | Trend Line, Ray, Extended Line, Horizontal Line, Vertical Line |
| **Fibonacci** | 🔢 | Fib Retracement, Fib Extension, Fib Fan, Fib Time Zones, Fib Channel, Fib Speed Resistance |
| **Positions** | 📈 / 📉 | Long Position, Short Position |
| **Shapes** | ▭ | Rectangle, Triangle, Ellipse, Parallel Channel, Flat Channel |
| **Annotations** | ✏️ | Text Label, Callout, Note, Arrow, Ruler |
| **Utility** | 🛠️ | Parallel Lines (copy offset), Mirror Tool |

### Keyboard Shortcuts
- `Ctrl+Z` (Cmd+Z) – Undo last drawing action.
- `Ctrl+Y` (Cmd+Shift+Z) – Redo last undone action.
- `Shift` – Toggles **OHLC snap** (snap to nearest high/low/open/close) while placing or editing.
- `Ctrl+Shift+T` – Instant save the selected Long/Short position as a trade.

---

## 3. Drawing Tools – Core Behaviour

### General Requirements for All Drawing Tools
- **Creation:** Click‑and‑drag (or click‑click for some tools) on the chart to place the drawing.
- **Editing:** After creation, **anchors** (small circles) appear at key points. Dragging an anchor adjusts the drawing in real time.
- **Selection:** A click on the drawing selects it; selected drawings show their anchors and a small border.
- **Persistence:** All drawings are saved to the database per `profileId`, `symbol`, and `timeframe`. They reload automatically when the same symbol/timeframe is opened again.
- **Snap:** Holding `Shift` while placing or moving anchors snaps to the nearest OHLC (high/low/open/close) of the candle under the cursor.
- **Hover Indicator:** When the mouse hovers over a drawing, the drawing **highlights** (e.g., thicker line or glow) and a small **delete ×** button appears near the centre or at a corner for quick deletion (instead of right‑click context menu). This speeds up deletion.
- **Context Menu:** Right‑click on a drawing opens a menu with: *Delete, Duplicate, Lock, Create Trade from Drawing, Instant Save, Properties* (for color/weight changes).

### Long/Short Position Tools – Trade Integration
- Dragging a position creates **three horizontal lines**: Entry (green), Stop Loss (red), Take Profit (blue).
- The area between Entry and SL/TP shows a **Risk:Reward label** (e.g., "R:R = 1:2.5").
- **Right‑click → Create Trade from Drawing**:
  - Opens the trade entry form (full dialog) pre‑filled with:
    - Symbol (from chart)
    - Direction (LONG/SHORT)
    - Entry price (Entry line level)
    - Stop Loss (SL line level)
    - Take Profit (TP line level)
    - R:R (computed)
  - User can adjust all fields, add quantity/strategy/notes, then save.
- **Right‑click → Instant Save** (or `Ctrl+Shift+T`):
  - Saves the trade immediately with defaults: quantity = 1, status = OPEN, no exit price, no notes.
- **Screenshot Button** – When a Long/Short position is active, a small **camera icon** appears near the position. Clicking it takes a **screenshot of the current chart** (including the position drawing) and attaches it as an image to the trade journal entry (either in the full form or automatically for instant save). The image is stored as a PNG and referenced in the trade record.

### Annotation Tools – Text Editing
- **Text Label:** Double‑click to enter inline editing mode (caret appears). Type new text, press `Enter` or click away to save.
- **Callout:** Text box with a leader line. Double‑click the text box to edit text. Drag the arrow endpoint to reposition the leader.
- **Note:** A sticky‑note shape. Double‑click opens a pop‑up text area for longer notes. The note icon shows a preview of the text.

---

## 4. Fibonacci Tools – Detailed Specifications

All Fibonacci tools must produce accurate levels and allow anchor dragging for fine‑tuning.

### Fib Retracement
- Click‑drag from a swing low to a swing high (or vice versa).
- Draws horizontal lines at: `0%`, `23.6%`, `38.2%`, `50%`, `61.8%`, `78.6%`, `100%`.
- Optionally, extension levels at `127.2%`, `161.8%`, `261.8%` (user‑configurable).
- Labels show both percentage and price.

### Fib Extension
- Requires three clicks: start of move, end of move, and a retracement point.
- Draws horizontal lines at: `0.618`, `1.000`, `1.272`, `1.618`, `2.618`, `4.236`.
- Labels show ratio and price.

### Fib Fan
- Click‑drag from a pivot point to an opposing swing.
- Radiating lines from the pivot at angles corresponding to Fibonacci ratios (`23.6°`, `38.2°`, `50°`, `61.8°`, `78.6°`).
- Lines extend to the right edge of the chart.

### Fib Time Zones
- Click on a significant low/high (start point).
- Vertical bands are drawn at intervals based on Fibonacci sequence: **1, 2, 3, 5, 8, 13, 21, 34, 55...** (in bar count, not absolute time).
- Bands have semi‑transparent fill and alternate colours.

### Fib Channel
- Draw a base trendline (two clicks). The system automatically creates **parallel lines** at distances determined by Fibonacci ratios (0.382, 0.618, 1.000, 1.618) from the base line.
- The width can be adjusted by dragging the base line’s endpoint; all parallel lines move sympathetically.

### Fib Speed Resistance
- Click two points (a swing low and high). The tool draws angled lines from the start point at slopes corresponding to speeds `1×`, `2×`, `3×`, `5×`, `8×` relative to the price/time slope of the original move.

---

## 5. Channel Tools – Parallel & Flat

### Parallel Channel
- User draws a **center line** (two clicks).
- Two outer lines are automatically created **parallel** to the center line, equidistant.
- The user can **drag** either outer line to change the channel width; the other outer line moves sympathetically.
- The center line can be dragged to shift the entire channel.
- Semi‑transparent fill between the outer lines (optional, user‑toggled).

### Flat Channel
- User draws a **horizontal rectangle** by clicking and dragging a horizontal range.
- Top and bottom edges are perfectly horizontal (constant price).
- Drag the top or bottom edge to change height; drag the left/right edges to change the time span.
- Light fill inside the rectangle.

---

## 6. Utility Tools – Parallel Lines Copy & Mirror

### Parallel Lines (Copy Offset)
- User selects an existing line (trend line, ray, etc.) and drags to create a **copy** at a specified offset distance (price or time).
- Offset is displayed and adjustable via a slider or input box.
- The copy maintains the same slope and style as the original.
- user can right-click on any shape and see the copy option.

### Mirror Tool
- User selects a drawing and defines a mirror axis (vertical or horizontal line).
- The system creates a **reflected copy** of the drawing across that axis.
- Both the original and the copy remain editable independently.
- user can right-click on any shape and see the mirror option.

---

## 7. Drawing Properties & Configuration

### Per‑Drawing Customisation
Each drawing must support:
- **Color** – User can change the line/fill colour via a colour picker (right‑click → Properties or a dedicated button).
- **Line Weight** – Stroke thickness (1px to 5px).
- **Line Style** – Solid, dashed, dotted, dash‑dot.
- **Fill Opacity** – For shapes and channels (0% to 100%).

These properties are saved with the drawing and restored on reload.

### Global Drawing Settings (per profile)
- Default colours for each tool type.
- Default line weights and styles.
- Option to **show/hide all drawings** on the chart.
- Option to **lock all drawings** to prevent accidental edits.

---

## 8. Save / Load Drawing Sets

**Requirement:**  
Users can save the current set of drawings for a given symbol + timeframe as a **named layout** (e.g., *"BTC Breakout Plan"*).

- A **Save Layout** button in the drawing toolbar (e.g., a floppy‑disk icon) saves the current drawings into a **named set**.
- A **Load Layout** button opens a list of previously saved layouts for that symbol/timeframe; selecting one loads all drawings from that layout, replacing the current ones.
- Layouts are stored per profile and can be deleted or renamed.
- This allows users to maintain multiple analysis scenarios for the same chart.

---

## 9. Performance & Error Handling

### Unnecessary API Calls
- As described in Section 1, only the active chart triggers data fetching.
- The system **must not** pre‑fetch or refresh data for symbols/timeframes not currently viewed.
- All background polling is paused when the chart view is not active.

### Error: InaccessibleObjectException (java.time)
**Issue:**  
`java.lang.reflect.InaccessibleObjectException: Unable to make field private final java.time.LocalDate java.time.LocalDateTime.date accessible`

**Fix:**  
This error occurs when serialising `LocalDateTime` via Gson (or similar) and the Java module system blocks access. The solution is to **switch to using `java.time.Instant`** for all time‑related fields in the `ChartDrawing` entity, or to register a Gson type adapter that handles `LocalDateTime` without reflection. For simplicity, use `Long` (epoch milliseconds) for all timestamps in the drawing points. This avoids module‑access issues entirely.

**Recommendation:**  
Store all time coordinates as `long` values (milliseconds since epoch) in the `points` JSON array. Convert to `Instant` or `ZonedDateTime` for display only.

### Performance Optimisation
- For drawings with many points (e.g., Fibonacci fans), use efficient rendering (e.g., caching the drawn shapes).
- Limit the number of drawings per chart to a reasonable maximum (e.g., 200) – warn the user if exceeded.

---

## 10. Screenshot Integration with Trade Journal

- When a Long/Short position is active, a **camera icon** appears near the position (or in the toolbar).
- Clicking it:
  - Captures the current chart view (including all drawings, indicators, and candlesticks) as a **PNG image**.
  - If the user is in the middle of creating a trade (full form), the image is **attached** to the trade entry automatically.
  - For Instant Save, the image is saved alongside the trade record and can be viewed later in the Journal.
- The image is stored in the profile’s data folder (e.g., `~/.trading-platform/screenshots/`) and its path is stored in the `trades` table (optional column `screenshot_path`).

---

## 11. Undo / Redo – Full Drawing History

- `Ctrl+Z` (Cmd+Z) undoes the last drawing action.
- `Ctrl+Y` (Cmd+Shift+Z) redoes the last undone action.
- Actions included: creation, deletion, duplication, move, modify (anchor drag), lock/unlock, colour/weight changes.
- Actions **excluded**: trade creation (full form or instant save) – these are permanent.
- The undo stack is session‑based and cleared when the chart view is closed.
- Each action stores the previous state of the affected drawing(s) to allow full restoration.

---

## 12. Hover & Quick Delete

- When the mouse hovers over any drawing, it **highlights** (e.g., thicker line + glow).
- A small **×** (close) button appears near the drawing (at the centre or near the first anchor) – clicking it deletes the drawing immediately **without confirmation** (or with a brief confirmation if preferred by user).
- This speeds up the cleaning of unwanted drawings.
- it can be changed through setting part per user. 

---

## Summary of All Fixes & Additions

| # | Issue / Feature | Status / Action |
|---|-----------------|-----------------|
| 1 | Fib tools faulty | Complete re‑implementation of all Fib tools with correct levels and snap. |
| 2 | No undo/redo | Add `Ctrl+Z` / `Ctrl+Y` for all drawing actions. |
| 3 | Parallel / Flat channel broken | Fix rendering and anchor editing. |
| 4 | Annotations not editable | Double‑click to edit text, save on exit. |
| 5 | InaccessibleObjectException | Use `long` timestamps (epoch ms) instead of `LocalDateTime`. |
| 6 | Parallel Lines & Mirror tools not working | Implement correctly with offset and reflection. |
| 7 | Hover indicator & quick delete | Highlight on hover, show delete × for fast removal. |
| 8 | Per‑drawing config (color, weight, style) | Add properties panel and save with drawing. |
| 9 | Save/Load drawing sets | Add Save Layout / Load Layout buttons per symbol/timeframe. |
| 10 | Unnecessary API calls | Aggregate data only for active chart/timeframe; pause on navigation. |
| 11 | Screenshot for trades | Camera button on positions → attach screenshot to trade. |

---

*End of Specifications*



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

# Chart Drawing & Trade Integration – Complete Feature Specifications

## Overview

This document defines the full specification for chart drawing tools, Fibonacci suites, annotation tools, trade integration, and related UI/UX improvements. It consolidates all corrections, clarifications, and enhancements requested.

---

# Feature Corrections & Clarifications

## 1. Fibonacci Drawing Tools – Incomplete or Faulty Behaviour

**Current Issue:**  
Most Fibonacci‑based drawing tools (Retracement, Extension, Fan, Time Zones, Channel, Speed Resistance) do not render correctly or produce incorrect levels. Some are completely non‑functional.

**Desired Behaviour:**

### Fib Retracement
- User clicks and drags from one swing point (low or high) to another.
- The tool should automatically compute and display the following retracement levels as horizontal lines:  
  `0%`, `23.6%`, `38.2%`, `50%`, `61.8%`, `78.6%`, `100%`.  
  *Optional extensions:* `127.2%`, `161.8%`, `261.8%` (if configured).
- Levels should be labelled with their percentage and price value.
- The user should be able to **drag any level line** vertically to adjust it manually (while the base swing points remain locked).

### Fib Extension
- User selects **three points**: the start of the move, the end of the move, and a retracement/extension point.
- The tool projects **price targets** at: `0.618`, `1.000`, `1.272`, `1.618`, `2.618`, `4.236`.
- Each target is drawn as a horizontal line with a label (e.g., `161.8%`).

### Fib Fan
- User drags from a pivot point to an opposing swing.
- The fan draws diagonal lines radiating from the pivot at angles corresponding to Fibonacci ratios (e.g., 23.6°, 38.2°, 50°, 61.8°, 78.6°).
- Lines should extend to the right edge of the visible chart.

### Fib Time Zones
- User clicks a start point (a significant low/high).
- Vertical bands are drawn at intervals based on the Fibonacci sequence: 1, 2, 3, 5, 8, 13, 21, 34, 55... (in bar counts, not absolute time).
- Bands are semi‑transparent coloured regions, with the first band starting at the click point.

### Fib Channel
- User draws a trendline (the base). The system automatically draws a **parallel channel** with width determined by the Fibonacci ratios (e.g., 0.382, 0.618, 1.000, 1.618) from the base line.
- The channel edges are parallel lines, and the width can be adjusted by dragging the base line’s endpoint.

### Fib Speed Resistance
- User selects two points (a swing low and a swing high).
- The tool draws angled lines from the start point at speeds corresponding to Fibonacci ratios (1×, 2×, 3×, 5×, 8×) relative to the price/time slope.

**All Fibonacci tools must:**
- Snap to OHLC when `Shift` is held during placement.
- Allow **anchor dragging** for fine‑tuning (e.g., move the base swing points).
- Persist across chart reloads.
- Be grouped under the **Fib Suite** dropdown in the toolbar.

---

## 2. Undo / Redo for Drawing Actions – Keyboard Shortcuts

**Current Issue:**  
There is no way to undo or redo drawing actions (creation, deletion, duplication, editing, moving). Users may accidentally delete or modify a drawing and cannot revert.

**Desired Behaviour:**

- **Undo** – `Ctrl+Z` (Windows/Linux) or `Cmd+Z` (macOS) – reverts the last drawing action.
- **Redo** – `Ctrl+Y` (Windows/Linux) or `Cmd+Shift+Z` (macOS) – reapplies the last undone action.

### Supported Actions
- Create drawing (any tool)
- Delete drawing
- Duplicate drawing
- Move drawing (drag anchors)
- Modify drawing (e.g., drag a level line)
- Lock/Unlock drawing
- Instant save / create trade from drawing (these actions should be **excluded** from undo/redo – they are separate operations).

### Implementation Expectations
- Undo/redo stack is **session‑based** (i.e., cleared when the chart view is closed).
- Each action stores enough state to fully restore the previous state of the drawing(s) involved.
- The shortcuts should work globally when the chart has focus.
- A visual indicator (e.g., button) showing that undo/redo is available is optional but recommended.

---

## 3. Parallel Channel & Flat Channel – Incorrect Rendering

**Current Issue:**  
The **Parallel Channel** and **Flat Channel** tools either do not draw correctly or produce unexpected shapes (e.g., non‑parallel lines, missing boundaries).

**Desired Behaviour:**

### Parallel Channel
- User draws a **center line** by clicking and dragging a trend line.
- The system automatically creates **two parallel lines** on either side of the center line.
- The user can adjust the **width** of the channel by dragging one of the outer lines (the other outer line moves sympathetically).
- The center line can be dragged to shift the whole channel.
- All three lines are rendered with the same style (colour, dash pattern), and the area between the outer lines may have a semi‑transparent fill.

### Flat Channel
- User draws a **horizontal rectangle** by clicking and dragging a horizontal range.
- The top and bottom edges are perfectly horizontal (price levels are constant).
- The user can drag the top or bottom edge to change the channel height.
- The left and right edges are vertical lines marking the time span.
- The area inside the channel may have a light fill.

### Both Tools
- Must respect snap‑to‑OHLC when `Shift` is pressed.
- Must persist and reload correctly.
- Should be listed under the **Shapes** dropdown group.

---

## 4. Annotation Tools – Text / Label Editing

**Current Issue:**  
The **Note**, **Callout**, and **Text** tools create a static label on the chart, but the user **cannot edit the text** after placement. There is no way to type into them or change the label content.

**Desired Behaviour:**

### Text Label
- After drawing, the label displays a default text (e.g., *"Text"*).
- The user can **double‑click** on the label to enter **in‑place editing mode** – the text becomes editable (a caret appears).
- The user types new text and presses `Enter` to confirm, or clicks away to save.
- The label’s font, size, colour, and background can be modified via a context‑menu option (if implemented).

### Note (Sticky Note)
- A small square/rectangular note with a yellow (or custom) background.
- Double‑clicking opens a **pop‑up text area** where the user can write longer notes.
- The note icon shows a preview of the first few words or a pencil icon.
- The note content is saved and displayed on hover or when expanded.

### Callout
- A text box with a leader line pointing to a specific price/time location.
- Double‑click the text box to edit the text (similar to Text Label).
- The leader line’s endpoint can be dragged to reposition the arrow.
- The text box can be dragged independently, and the leader line updates automatically.

### General Rules for All Annotations
- Editing mode is triggered by **double‑click**.
- The text is saved immediately when editing ends (no separate "Save" button needed – but changes are committed on `Enter` or focus lost).
- The annotation must persist with the updated text across chart reloads.

---

## Summary of Corrections

| # | Issue | Correction |
|---|-------|------------|
| 1 | Fib tools faulty | Fix all Fib tools: retracement, extension, fan, time zones, channel, speed resistance. Ensure accurate levels, snap, and persistence. |
| 2 | No undo/redo | Add `Ctrl+Z` (Undo) and `Ctrl+Y` (Redo) for all drawing actions (create, delete, modify, duplicate, move). |
| 3 | Parallel & Flat channels broken | Fix rendering: parallel channel = 3 parallel lines, flat channel = horizontal rectangle. Both with anchor dragging and snap. |
| 4 | Annotations not editable | Allow double‑click to edit text in Text, Note, and Callout. Save changes immediately. Note uses pop‑up for longer text. |

---

## 1. Chart Data Aggregation – On‑Demand & Timeframe‑Aware

**Requirement:**  
Market data fetching (OHLCV, indicators, analysis) must happen **only for the chart currently open** and **only for its active timeframe**.

- When a user opens the **Live Chart**, aggregation starts for that symbol + timeframe.
- When the user changes the timeframe, the aggregation switches to the new timeframe immediately.
- Background refresh intervals depend on the timeframe (e.g., 1m → every 60s, 1h → every 5 min, 1D → once per day).
- When the user navigates away from the chart (e.g., to Dashboard, Journal), all polling and fetching for that symbol/timeframe **pause or stop** entirely.
- On returning, the latest cached data is shown instantly; a fresh fetch occurs only if the data is stale (based on timeframe TTL).
- **No unnecessary API calls** for symbols/timeframes the user has not loaded.

---

## 2. Drawing Tools – UI & Organisation

### Toolbar Layout
- Compact vertical toolbar on the left side of the chart (max 48px wide).
- **Icons only** (no text labels) to save space.
- Tools grouped into **dropdown flyouts** by category (see table below).
- Hovering over any icon shows a **tooltip** with the tool’s full name.
- The toolbar is **draggable** to any corner of the chart (top‑left, top‑right, bottom‑right) and can be **collapsed** to a thin strip via a small arrow button.

### Tool Groups

| Group | Icon | Tools |
|-------|------|-------|
| **Select / Pan** | ↖ | Selection, pan, and anchor editing |
| **Line** | 📐 | Trend Line, Ray, Extended Line, Horizontal Line, Vertical Line |
| **Fibonacci** | 🔢 | Fib Retracement, Fib Extension, Fib Fan, Fib Time Zones, Fib Channel, Fib Speed Resistance |
| **Positions** | 📈 / 📉 | Long Position, Short Position |
| **Shapes** | ▭ | Rectangle, Triangle, Ellipse, Parallel Channel, Flat Channel |
| **Annotations** | ✏️ | Text Label, Callout, Note, Arrow, Ruler |
| **Utility** | 🛠️ | Parallel Lines (copy offset), Mirror Tool |

### Keyboard Shortcuts
- `Ctrl+Z` (Cmd+Z) – Undo last drawing action.
- `Ctrl+Y` (Cmd+Shift+Z) – Redo last undone action.
- `Shift` – Toggles **OHLC snap** (snap to nearest high/low/open/close) while placing or editing.
- `Ctrl+Shift+T` – Instant save the selected Long/Short position as a trade.

---

## 3. Drawing Tools – Core Behaviour

### General Requirements for All Drawing Tools
- **Creation:** Click‑and‑drag (or click‑click for some tools) on the chart to place the drawing.
- **Editing:** After creation, **anchors** (small circles) appear at key points. Dragging an anchor adjusts the drawing in real time.
- **Selection:** A click on the drawing selects it; selected drawings show their anchors and a small border.
- **Persistence:** All drawings are saved to the database per `profileId`, `symbol`, and `timeframe`. They reload automatically when the same symbol/timeframe is opened again.
- **Snap:** Holding `Shift` while placing or moving anchors snaps to the nearest OHLC (high/low/open/close) of the candle under the cursor.
- **Hover Indicator:** When the mouse hovers over a drawing, the drawing **highlights** (e.g., thicker line or glow) and a small **delete ×** button appears near the centre or at a corner for quick deletion (instead of right‑click context menu). This speeds up deletion.
- **Context Menu:** Right‑click on a drawing opens a menu with: *Delete, Duplicate, Lock, Create Trade from Drawing, Instant Save, Properties* (for color/weight changes).

### Long/Short Position Tools – Trade Integration
- Dragging a position creates **three horizontal lines**: Entry (green), Stop Loss (red), Take Profit (blue).
- The area between Entry and SL/TP shows a **Risk:Reward label** (e.g., "R:R = 1:2.5").
- **Right‑click → Create Trade from Drawing**:
  - Opens the trade entry form (full dialog) pre‑filled with:
    - Symbol (from chart)
    - Direction (LONG/SHORT)
    - Entry price (Entry line level)
    - Stop Loss (SL line level)
    - Take Profit (TP line level)
    - R:R (computed)
  - User can adjust all fields, add quantity/strategy/notes, then save.
- **Right‑click → Instant Save** (or `Ctrl+Shift+T`):
  - Saves the trade immediately with defaults: quantity = 1, status = OPEN, no exit price, no notes.
- **Screenshot Button** – When a Long/Short position is active, a small **camera icon** appears near the position. Clicking it takes a **screenshot of the current chart** (including the position drawing) and attaches it as an image to the trade journal entry (either in the full form or automatically for instant save). The image is stored as a PNG and referenced in the trade record.

### Annotation Tools – Text Editing
- **Text Label:** Double‑click to enter inline editing mode (caret appears). Type new text, press `Enter` or click away to save.
- **Callout:** Text box with a leader line. Double‑click the text box to edit text. Drag the arrow endpoint to reposition the leader.
- **Note:** A sticky‑note shape. Double‑click opens a pop‑up text area for longer notes. The note icon shows a preview of the text.

---

## 4. Fibonacci Tools – Detailed Specifications

All Fibonacci tools must produce accurate levels and allow anchor dragging for fine‑tuning.

### Fib Retracement
- Click‑drag from a swing low to a swing high (or vice versa).
- Draws horizontal lines at: `0%`, `23.6%`, `38.2%`, `50%`, `61.8%`, `78.6%`, `100%`.
- Optionally, extension levels at `127.2%`, `161.8%`, `261.8%` (user‑configurable).
- Labels show both percentage and price.

### Fib Extension
- Requires three clicks: start of move, end of move, and a retracement point.
- Draws horizontal lines at: `0.618`, `1.000`, `1.272`, `1.618`, `2.618`, `4.236`.
- Labels show ratio and price.

### Fib Fan
- Click‑drag from a pivot point to an opposing swing.
- Radiating lines from the pivot at angles corresponding to Fibonacci ratios (`23.6°`, `38.2°`, `50°`, `61.8°`, `78.6°`).
- Lines extend to the right edge of the chart.

### Fib Time Zones
- Click on a significant low/high (start point).
- Vertical bands are drawn at intervals based on Fibonacci sequence: **1, 2, 3, 5, 8, 13, 21, 34, 55...** (in bar count, not absolute time).
- Bands have semi‑transparent fill and alternate colours.

### Fib Channel
- Draw a base trendline (two clicks). The system automatically creates **parallel lines** at distances determined by Fibonacci ratios (0.382, 0.618, 1.000, 1.618) from the base line.
- The width can be adjusted by dragging the base line’s endpoint; all parallel lines move sympathetically.

### Fib Speed Resistance
- Click two points (a swing low and high). The tool draws angled lines from the start point at slopes corresponding to speeds `1×`, `2×`, `3×`, `5×`, `8×` relative to the price/time slope of the original move.

---

## 5. Channel Tools – Parallel & Flat

### Parallel Channel
- User draws a **center line** (two clicks).
- Two outer lines are automatically created **parallel** to the center line, equidistant.
- The user can **drag** either outer line to change the channel width; the other outer line moves sympathetically.
- The center line can be dragged to shift the entire channel.
- Semi‑transparent fill between the outer lines (optional, user‑toggled).

### Flat Channel
- User draws a **horizontal rectangle** by clicking and dragging a horizontal range.
- Top and bottom edges are perfectly horizontal (constant price).
- Drag the top or bottom edge to change height; drag the left/right edges to change the time span.
- Light fill inside the rectangle.

---

## 6. Utility Tools – Parallel Lines Copy & Mirror

### Parallel Lines (Copy Offset)
- User selects an existing line (trend line, ray, etc.) and drags to create a **copy** at a specified offset distance (price or time).
- Offset is displayed and adjustable via a slider or input box.
- The copy maintains the same slope and style as the original.

### Mirror Tool
- User selects a drawing and defines a mirror axis (vertical or horizontal line).
- The system creates a **reflected copy** of the drawing across that axis.
- Both the original and the copy remain editable independently.

---

## 7. Drawing Properties & Configuration

### Per‑Drawing Customisation
Each drawing must support:
- **Color** – User can change the line/fill colour via a colour picker (right‑click → Properties or a dedicated button).
- **Line Weight** – Stroke thickness (1px to 5px).
- **Line Style** – Solid, dashed, dotted, dash‑dot.
- **Fill Opacity** – For shapes and channels (0% to 100%).

These properties are saved with the drawing and restored on reload.

### Global Drawing Settings (per profile)
- Default colours for each tool type.
- Default line weights and styles.
- Option to **show/hide all drawings** on the chart.
- Option to **lock all drawings** to prevent accidental edits.

---

## 8. Save / Load Drawing Sets

**Requirement:**  
Users can save the current set of drawings for a given symbol + timeframe as a **named layout** (e.g., *"BTC Breakout Plan"*).

- A **Save Layout** button in the drawing toolbar (e.g., a floppy‑disk icon) saves the current drawings into a **named set**.
- A **Load Layout** button opens a list of previously saved layouts for that symbol/timeframe; selecting one loads all drawings from that layout, replacing the current ones.
- Layouts are stored per profile and can be deleted or renamed.
- This allows users to maintain multiple analysis scenarios for the same chart.

---

## 9. Performance & Error Handling

### Unnecessary API Calls
- As described in Section 1, only the active chart triggers data fetching.
- The system **must not** pre‑fetch or refresh data for symbols/timeframes not currently viewed.
- All background polling is paused when the chart view is not active.

### Error: InaccessibleObjectException (java.time)
**Issue:**  
`java.lang.reflect.InaccessibleObjectException: Unable to make field private final java.time.LocalDate java.time.LocalDateTime.date accessible`

**Fix:**  
This error occurs when serialising `LocalDateTime` via Gson (or similar) and the Java module system blocks access. The solution is to **switch to using `java.time.Instant`** for all time‑related fields in the `ChartDrawing` entity, or to register a Gson type adapter that handles `LocalDateTime` without reflection. For simplicity, use `Long` (epoch milliseconds) for all timestamps in the drawing points. This avoids module‑access issues entirely.

**Recommendation:**  
Store all time coordinates as `long` values (milliseconds since epoch) in the `points` JSON array. Convert to `Instant` or `ZonedDateTime` for display only.

### Performance Optimisation
- For drawings with many points (e.g., Fibonacci fans), use efficient rendering (e.g., caching the drawn shapes).
- Limit the number of drawings per chart to a reasonable maximum (e.g., 200) – warn the user if exceeded.

---

## 10. Screenshot Integration with Trade Journal

- When a Long/Short position is active, a **camera icon** appears near the position (or in the toolbar).
- Clicking it:
  - Captures the current chart view (including all drawings, indicators, and candlesticks) as a **PNG image**.
  - If the user is in the middle of creating a trade (full form), the image is **attached** to the trade entry automatically.
  - For Instant Save, the image is saved alongside the trade record and can be viewed later in the Journal.
- The image is stored in the profile’s data folder (e.g., `~/.trading-platform/screenshots/`) and its path is stored in the `trades` table (optional column `screenshot_path`).

---

## 11. Undo / Redo – Full Drawing History

- `Ctrl+Z` (Cmd+Z) undoes the last drawing action.
- `Ctrl+Y` (Cmd+Shift+Z) redoes the last undone action.
- Actions included: creation, deletion, duplication, move, modify (anchor drag), lock/unlock, colour/weight changes.
- Actions **excluded**: trade creation (full form or instant save) – these are permanent.
- The undo stack is session‑based and cleared when the chart view is closed.
- Each action stores the previous state of the affected drawing(s) to allow full restoration.

---

## 12. Hover & Quick Delete

- When the mouse hovers over any drawing, it **highlights** (e.g., thicker line + glow).
- A small **×** (close) button appears near the drawing (at the centre or near the first anchor) – clicking it deletes the drawing immediately **without confirmation** (or with a brief confirmation if preferred by user).
- This speeds up the cleaning of unwanted drawings.
- it can be changed through setting part per user. 

---

## Summary of All Fixes & Additions

| # | Issue / Feature | Status / Action |
|---|-----------------|-----------------|
| 1 | Fib tools faulty | Complete re‑implementation of all Fib tools with correct levels and snap. |
| 2 | No undo/redo | Add `Ctrl+Z` / `Ctrl+Y` for all drawing actions. |
| 3 | Parallel / Flat channel broken | Fix rendering and anchor editing. |
| 4 | Annotations not editable | Double‑click to edit text, save on exit. |
| 5 | InaccessibleObjectException | Use `long` timestamps (epoch ms) instead of `LocalDateTime`. |
| 6 | Parallel Lines & Mirror tools not working | Implement correctly with offset and reflection. |
| 7 | Hover indicator & quick delete | Highlight on hover, show delete × for fast removal. |
| 8 | Per‑drawing config (color, weight, style) | Add properties panel and save with drawing. |
| 9 | Save/Load drawing sets | Add Save Layout / Load Layout buttons per symbol/timeframe. |
| 10 | Unnecessary API calls | Aggregate data only for active chart/timeframe; pause on navigation. |
| 11 | Screenshot for trades | Camera button on positions → attach screenshot to trade. |

---
Fix 1 – Delete Button Position Consistency
File: ChartDrawingEngine.java
hoverDeletePos() now delegates to selectionDeletePos() so both hover and selection delete buttons appear at the exact
same bounding-box top-right position. No more jumping. Hit area enlarged to HOVER_DELETE_RADIUS + 4 for easier clicking.

Fix 2 – Screenshot Preview Click-to-Enlarge
File: TradeEntryController.java
Click on any screenshot thumbnail opens a dedicated Stage dialog with a zoomable, scrollable preview. Features: Zoom
In/Out/Fit buttons, mouse-scroll zoom, keyboard shortcuts (+/-/F/Esc), hand cursor on the thumbnail, and "🔍 Click to
enlarge" hint.

Fix 3 – Note Box Full Text + Multi-line Editor
Files: DrawingRenderer.java, ChartDrawingEngine.java
Note preview now renders word-wrapped lines that fill the box height with a … overflow indicator. Double-click opens a
proper multi-line TextArea popup (not single-line TextInputDialog), with Ctrl+Enter=Save / Esc=Cancel shortcuts. Default
size 120×80 px.

Fix 4 – TextBox/TextLabel Word Wrap
Files: DrawingRenderer.java, ChartDrawingEngine.java
New wrapText() utility wraps text to fit box width, honouring \n and splitting overlong words. drawTextLabel(),
drawCallout(), and drawNoteIcon() all use it. getTextBoxHeight() auto-calculates from line count. … shown when clipped.

Fix 5 – Profile Switching Immediate Refresh
File: MainDashboardController.java
switchProfile() detects the currently visible view and refreshes in-place: Chart→prepareView(),
Alerts/Export/Mixer→re-push profile, Dashboard/Journal/Portfolio→loadProfile()+showView(). No more stale data after
switching profiles.

Fix 6 – Chart Toolbox Layout & Positioning
File: ChartView.fxml
Redesigned toolbar with logical groups: Symbol | TF Favorites | Bars | Indicators | Bell | Source → Actions. Pane(
hgrow=ALWAYS) spacer right-aligns Refresh/Analyze. Compact padding (3px vertical), smaller ComboBoxes (110/72/140px),
ScrollPane wrapper prevents clipping at any width.

Fix 7 – Per-User Drawing Settings & Persistence
Files: UserProfile.java, ChartController.java, ChartDrawingProperties.java, ChartDrawingEngine.java,
GlobalDrawingSettingsDialog.java

7.1: Added drawingSettingsJson TEXT column to user_profiles (auto-migrated). Settings now saved/loaded per-profile
instead of machine-wide. Profile switch loads new profile's settings.
7.2: ChartDrawingProperties.defaultsFor(type, settings) overload applies profile defaults at creation time.
Position-tool semantic colours (green/red) always preserved. Existing drawings unaffected.

🖐️ NEW: Touch Support for Chart

T‑1 -->    Pinch‑to‑zoom -->    Two‑finger gesture zooms in/out on the chart (both time and price axes).

T‑2 -->        Two‑finger pan -->    Drag with two fingers to pan the chart view (time and price).

T‑3 -->     Touch drag for drawing objects -->    When in Select mode, dragging a drawing with one finger moves it (or
its anchors).

T‑4 -->    Scrolling the chart axes -->    Swipe up/down on price axis, left/right on time axis to scroll independently.

T‑5 -->  Touch‑friendly toolbar -->    Buttons and controls must be large enough (≥44×44px) for finger taps.

```markdown
# Issues to Fix (Immediate)

## 1. Profile Bar (with Admin) Hidden in Chart View

**Issue:** The profile selector/admin bar at the top of the chart view is no longer visible after recent changes.

**Fix:** Restore the profile bar visibility in `ChartView.fxml` (or `MainDashboard.fxml`). Ensure the profile selector,
admin controls, and any associated header elements are rendered above the chart area.

---

## 2. Chart Volume Sub‑Pane Not Visible

**Issue:** The volume indicator is hidden because the chart canvas is too large and pushes the volume pane off‑screen.

**Fix:**

- Reduce the default chart canvas size or make the volume sub‑pane resizable.
- Ensure the volume pane has a **fixed minimum height** (e.g., 60–80px) and is positioned below the main chart.
- If necessary, add a toggle to show/hide the volume pane.

---

## 2.1 Fullscreen Mode for Chart

**Requirement:** Add a **fullscreen toggle** for the chart view, accessible via:

- A **fullscreen icon** in the chart toolbar.
- A **keyboard shortcut** (e.g., `F11`).

**In Fullscreen Mode:**

- The chart occupies the entire screen (no window borders, no navigation sidebar).
- The **timeframe buttons** and **bar count control** must remain visible as an overlay (e.g., a floating toolbar) at
  the top or bottom of the chart.
- Press `F11` or the icon again to exit fullscreen.

---

## 3. Duplicate Children Error in `ScrollingTickerPane`

**Error:**
```

java.lang.IllegalArgumentException: Children: duplicate children added: parent = HBox@4dce43ad
at com.mst.matt.tradingplatformapp.ui.ScrollingTickerPane.rebuildLoop(ScrollingTickerPane.java:98)
at com.mst.matt.tradingplatformapp.ui.ScrollingTickerPane.setSymbols(ScrollingTickerPane.java:78)

```

**Fix:** In `ScrollingTickerPane.rebuildLoop()`, ensure the `HBox` children are **cleared** before adding new nodes.

**Implementation:**
```java
private void rebuildLoop() {
    hbox.getChildren().clear(); // Clear before adding
    // ... add new children
}
```

---

## 4. Touch Interaction Broken on Chart

**Current Issues:**

- Touching **up** zooms in; touching **down** zooms out – incorrect gesture mapping.
- When trying to **draw a shape** with touch, zooming also triggers simultaneously.
- Scrolling the chart axes (swipe up/down on price axis, left/right on time axis) does **not** work.

**Desired Touch Behaviour:**

| Gesture                                      | Action                                      |
|----------------------------------------------|---------------------------------------------|
| **Pinch (two fingers out)**                  | Zoom in                                     |
| **Pinch (two fingers in)**                   | Zoom out                                    |
| **Single‑finger drag**                       | Pan the chart (both axes)                   |
| **Swipe up/down on price axis**              | Scroll the price axis independently         |
| **Swipe left/right on time axis**            | Scroll the time axis independently          |
| **Single‑finger tap + drag (on shape)**      | Move the selected shape (if in Select mode) |
| **Single‑finger drag (drawing tool active)** | Create the shape (no zoom interference)     |

**Fix:**

- Separate touch events: **pinch** for zoom, **single finger** for pan and drawing.
- Ensure **drawing tools** do not trigger zoom or pan gestures.
- Map axis swipes to scroll independently.

---

## Summary of Tasks

| #   | Task                                                                                                    |
|-----|---------------------------------------------------------------------------------------------------------|
| 1   | Restore profile bar visibility in chart view.                                                           |
| 2   | Fix volume sub‑pane size/position.                                                                      |
| 2.1 | Add fullscreen toggle with `F11` and floating toolbar.                                                  |
| 3   | Clear `HBox` children before rebuilding `ScrollingTickerPane`.                                          |
| 4   | Fix touch gestures: pinch for zoom, single‑finger for pan/drawing, axis swipes to scroll independently. |

---

5. # Feature: Show Percentages on Long/Short Position Lines

## Requirement

When a **Long Position** or **Short Position** is drawn on the chart, the **Stop Loss** and **Take Profit** lines should
display **both the price level and the percentage difference from the entry price**.

---

## Desired Behaviour

### Long Position (Entry = Green, SL = Red, TP = Blue)

- Each line label shows:
    - **Entry:** `Entry: 65,000.00` (no percentage needed)
    - **Stop Loss:** `SL: 63,000.00 (-3.08%)`
    - **Take Profit:** `TP: 68,000.00 (+4.62%)`

### Short Position (Entry = Red, SL = Blue, TP = Green)

- **Entry:** `Entry: 65,000.00`
- **Stop Loss:** `SL: 67,000.00 (+3.08%)`
- **Take Profit:** `TP: 62,000.00 (-4.62%)`

---

## Calculation

| Line              | Percentage Formula                                                             |
|-------------------|--------------------------------------------------------------------------------|
| **Take Profit %** | `((TP - Entry) / Entry) × 100` (Long) / `((Entry - TP) / Entry) × 100` (Short) |
| **Stop Loss %**   | `((SL - Entry) / Entry) × 100` (Long) / `((Entry - SL) / Entry) × 100` (Short) |

- Positive percentage = profit side.
- Negative percentage = loss side.
- Show with **two decimal places** and a `+`/`‑` sign.

---

All 6 issues have been implemented and pushed to `genspark_ai_developer`. Here's a summary of every change made:

---

## Summary of Changes

### Issue #1 — Profile Bar Visibility in Chart View

**File:** `MainDashboardController.java`

- Modified `showView()` to **remove the 24px contentArea padding** when the chart view is active, and restore it for all
  other views. This ensures the ChartView toolbar (symbol, TF controls) is never clipped or pushed off-screen.

---

### Issue #2 — Volume Sub-Pane Min Height

**File:** `CandlestickChartCanvas.java`

- Added `VOL_MIN_HEIGHT = 70.0` constant.
- Changed the volume height calculation to `Math.max(VOL_MIN_HEIGHT, totalH * VOL_FRAC)` — the volume pane is now
  guaranteed at least **70px** regardless of chart height or number of sub-pane indicators.

---

### Issue #2.1 — Fullscreen Mode

**Files:** `ChartView.fxml`, `ChartController.java`

- Added **⛶ fullscreen button** to the chart toolbar with `onToggleFullscreen` handler.
- `enterFullscreen()`: replaces the scene root with a new `StackPane` overlay containing the chart + a **floating HBox
  toolbar** (timeframe favorites, bars combo, exit button). Sets `Stage.setFullScreen(true)`.
- `exitFullscreen()`: restores the original scene root, moves `chartStack` back into the ChartView VBox, rebinds canvas
  size.
- **F11** key triggers toggle; **Escape** exits fullscreen.

---

### Issue #3 — ScrollingTickerPane Duplicate Children

**File:** `ScrollingTickerPane.java`

- `rebuildLoop()` now creates **fresh cloned `Label` nodes** for the second scrolling loop copy instead of re-adding
  existing children. This eliminates the `IllegalArgumentException: Children: duplicate children added`.

---

### Issue #4 — Touch Interaction

**File:** `CandlestickChartCanvas.java`

- Added imports: `TouchEvent`, `TouchPoint`, `ZoomEvent`.
- Added `setupTouchHandlers()` called from `init()`:
    - **`ZoomEvent`** (JavaFX pinch gesture): fingers apart = zoom in (fewer bars), fingers together = zoom out —
      correct direction.
    - **Single-finger `TouchMoved`**: pans the chart (same logic as mouse drag).
    - **Drawing tool active**: touch does NOT trigger pan/zoom — lets drawing engine handle input cleanly.

---

### Issue #5 — Long/Short Position SL/TP Labels with % Difference

**File:** `DrawingRenderer.java`

- `drawPosition()` now formats SL and TP labels with both **price level and percentage from entry**:
    - SL: `SL  49000.00  (-2.00%)`
    - TP: `TP  52000.00  (+4.00%)`

---

### Branch & Commit

- Branch: **`genspark_ai_developer`**
- Commit: `fix: resolve 6 chart/UI issues`
- Pushed to: `https://github.com/mahdi312/matthew_trading_platform.git`

# Issues to Fix

---

## 1. Fullscreen Mode – Chart Disappears After Exit

**Issue:** After exiting fullscreen mode, the chart area becomes blank or hidden.

**Fix:** Ensure the chart container is **re‑attached** or **re‑sized** properly when exiting fullscreen. Verify that the
`CandlestickChartCanvas` is still part of the scene graph and has the correct width/height bindings restored.

---

## 2. Recent Trades Table – Not Fitting & Not Scrollable

**Issue:** The recent trades table in the Dashboard view:

- Does not fit within the window.
- Trades are not scrollable (user cannot see all trades).

**Fix:**

- Place the `TableView` inside a **ScrollPane**.
- Set `TableView.setPrefHeight(Region.USE_COMPUTED_SIZE)` and allow vertical scrolling.
- Ensure the table column widths are **responsive** (e.g., use percentage widths or `ColumnConstraints`).
- The table should occupy available space without overflowing the window.

---

## 3. Touch Sensitivity Too High

**Issue:** Touch gestures (pinch, pan, drag) are overly sensitive – small movements cause large zoom/pan changes.

**Fix:**

- Reduce the **zoom sensitivity multiplier** (e.g., from 1.2× to 1.05× per pinch step).
- Reduce **pan sensitivity** – scale the drag delta by a factor (e.g., 0.8×).
- Add a **configurable sensitivity slider** in Settings for user preference.

---

## 4. Header Bar (Title, Admin, Profile Selector, Settings) Hidden

**Issue:** The top header bar containing:

- "Trading Intelligence Platform" title
- Admin controls
- Profile selector
- Settings button

…is not visible in the window or chart panel.

**Fix:**

- Ensure the header bar is **not overridden** or set to `visible="false"` in FXML.
- Check that the header is **not hidden behind** the chart or toolbar.
- Verify that the `VBox`/`BorderPane` layout correctly stacks:
    - **Top:** Header bar
    - **Center:** Chart / content
- If the header was inadvertently removed, restore it from the original FXML or version control.

---

5. # Bug Fix: Mouse Panning (Scroll Axes) Not Working

## Issue

When clicking and dragging on the chart, the **time axis** and **price axis** do **not** scroll/pan. The chart remains
fixed, and no mouse‑driven panning occurs.

## Desired Behaviour

- **Click + Drag** on the chart should **pan** the view (both axes).
- If the user drags **horizontally**, the time axis scrolls left/right.
- If the user drags **vertically**, the price axis scrolls up/down.
- Panning should only work in **Select mode** (when no drawing tool is active).
- Mouse wheel should still zoom (scroll to zoom) – this is already working.

## Summary of Tasks

| # | Task                                                                        |
|---|-----------------------------------------------------------------------------|
| 1 | Fix chart re‑attachment after fullscreen exit.                              |
| 2 | Add `ScrollPane` to recent trades table; make columns responsive.           |
| 3 | Reduce touch sensitivity multiplier for zoom and pan.                       |
| 4 | Restore visibility of the top header bar (title, admin, profile, settings). |

---

*End of issues*