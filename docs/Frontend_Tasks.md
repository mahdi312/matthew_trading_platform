# Trading Intelligence Platform — Angular 21 Frontend Prompt Pack

A ready-to-paste set of prompts for rebuilding every tab of the **Matthew Trading Platform** frontend into something
beautiful, cohesive, and genuinely useful — on the Angular 21 / Material 21 stack the repo already runs.

This pack is grounded in your actual codebase (`webapp/frontend/`): real routes, real DTOs, real endpoints, real service
names. Nothing here is generic scaffolding — every prompt tells the agent exactly which service to call, which fields
exist, and which existing component to reuse instead of reinventing.

---

## How to use this document

1. **Feed prompts to an agentic coding tool** (Claude Code, Cursor, etc.) pointed at your `frontend/` workspace, one at
   a time, in order. Each is self-contained but they build on each other — Prompt 1 (design tokens) and Prompt 2 (
   shell + shared states) are foundations everything else depends on.
2. **Verify before advancing.** Build after each prompt (`ng build`), glance at the screen, then move to the next. Don't
   batch multiple feature prompts into one agent turn — you lose the ability to catch drift early.
3. **Every prompt assumes the agent can read the repo.** They reference real file paths so the agent greps for them
   rather than guessing.
4. **Non-negotiable conventions** — repeated in every prompt so nothing silently breaks them:
    - Every HTTP/WebSocket call goes through `environment.gatewayBaseUrl` and the path constants in
      `core/api/api-paths.ts` — never a microservice's own port, never a hardcoded path string.
    - The JWT lives in `localStorage['mtp_auth_token']` (`AuthSessionService.TOKEN_STORAGE_KEY`). Never introduce a
      second token store.
    - STOMP/WebSocket auth is a **connect header** (`Authorization: Bearer …`), not a URL param — follow
      `core/notification/notification.service.ts`'s pattern.
    - Standalone components + `signal()`/`computed()` for state — this codebase does not use NgModules or heavy
      `BehaviorSubject` chains.
    - `shared/chart-library/` (`CandlestickChartComponent`) is presentation-only — it takes data as `@Input()`/emits
      events, and never makes its own HTTP or WebSocket calls. Every screen that shows a chart consumes it; none
      re-implement charting.

---

## At a glance — the 12 screens this pack covers

| #  | Screen                            | Route                                   | Guard                  | Backend service                         |
|----|-----------------------------------|-----------------------------------------|------------------------|-----------------------------------------|
| 1  | Login / Register / OAuth callback | `/login`, `/register`, `/auth/callback` | public                 | identity-service                        |
| 2  | Dashboard                         | `/dashboard` (default)                  | authGuard              | trading-service, reference-data-service |
| 3  | Live Trading                      | `/live-trading`                         | authGuard              | trading-service, market-service         |
| 4  | Journal                           | `/journal`                              | authGuard              | trading-service                         |
| 5  | Charting                          | `/charting`                             | authGuard              | market-service                          |
| 6  | Alerts                            | `/alerts`                               | authGuard              | alert-service                           |
| 7  | AI Insights                       | `/ai-insights`                          | authGuard              | ai-service                              |
| 8  | On-Chain                          | `/on-chain`                             | authGuard              | reference-data-service                  |
| 9  | Reports                           | `/reports`                              | authGuard              | trading-service                         |
| 10 | Settings                          | `/settings`                             | authGuard              | identity-service                        |
| 11 | Admin                             | `/admin`                                | authGuard + adminGuard | identity-service                        |
| 12 | Embed Chart                       | `/embed/chart`                          | embed API key (no JWT) | market-service                          |

Prompts 1–2 below are foundations that sit underneath all twelve. Prompt 15 is the closing QA pass.

---

## Prompt 0 — Session Context Primer

Paste this at the start of any new agent session on this repo, before the first real task of that session.

> You're working in the `frontend/` workspace of the Matthew Trading Platform — a multi-asset (crypto, stocks, forex,
> commodities, indices) trading journal, charting, and alerting web app. It's Angular 21.2 with Angular Material 21 (
> MDC-based, M3 tokens), ECharts, and STOMP/SockJS over a Spring Cloud Gateway. There are 8 backend microservices (
> identity, trading, market, alert, ai, reference-data, notification) reached exclusively through
`environment.gatewayBaseUrl` — read `core/api/api-paths.ts` for the path prefix each one owns before writing any HTTP
> call.
>
> Conventions already established in this codebase — extend them, don't replace them:
> - All standalone components, `signal()`/`computed()` for reactive state.
> - JWT in `localStorage['mtp_auth_token']`; `AuthSessionService` is the single source of truth for auth state (`token`,
    `getToken()`, `getCurrentUserId()`, `hasRole()`, `refresh()`). Any login/register flow writes to that exact key and
    calls `authSession.refresh()`.
> - `ThemeService` toggles `theme-dark`/`theme-light` classes on `<html>`, persisted to `localStorage['mtp_theme']`.
    Dark is the default — this is a terminal, not a marketing site.
> - `shared/chart-library/candlestick-chart/` wraps ECharts and is purely presentational — data and drawings come in as
    inputs, drawing edits go out as `(drawingChanged)` events. It never calls HTTP or opens sockets itself.
> - `shared/symbol-search/` is the one autocomplete-for-a-ticker component — reuse it anywhere a symbol needs to be
    picked, don't build a second one.
> - `core/tools/tools-panel.service.ts` drives a persistent right-hand drawer (currently just the watchlist) — reuse
    this service rather than inventing a second drawer mechanism.
>
> Before you touch a screen, read its existing `.component.ts`, `.models.ts`, and API service file in full — the DTOs
> and endpoints are real and finalized on the backend; your job is the visual and interaction layer, not renaming fields.

---

## Prompt 1 — Design System & Visual Identity

**Why this goes first:** everything downstream inherits these tokens. Skipping straight to feature screens is how you
end up with ten inconsistent dashboards instead of one coherent product.

**Context:** `styles.scss` currently defines a `--tp-*` token set (bridged into Material's `--mat-sys-*` variables so
MDC tables/menus/tabs inherit it) using a near-black GitHub-dark palette (`#0d1117` background, `#388bfd` blue accent).
That bridging *mechanism* is good architecture — keep it. The token *values* are the generic default every AI-assisted
dashboard ships with. Replace them with something that actually reads as a trading terminal, not a template.

> Redesign the design tokens in `styles.scss`, keeping the existing `--tp-*` → `--mat-sys-*` bridge architecture
> intact (don't restructure how Material MDC surfaces inherit theme — only change the values feeding it and extend it
> where noted).
>
> **Palette** — an "ink and brass" trading-terminal identity, not another blue SaaS dashboard:
> - Background layers: `--ink-950 #0A0D12` (app background), `--ink-900 #12151C` (sidebar/secondary),
    `--ink-800 #1B2029` (card surface), `--ink-700 #242A35` (hover/active surface)
> - Primary accent: `--brass-500 #D4A24C` / `--brass-400 #E0B563` — used for primary actions, active nav state, focus
    rings, and the primary line on non-P&L charts. This replaces the generic Material blue as *the* brand color.
> - Secondary accent: `--teal-400 #34D6C4` — reserved for informational/analytical content (AI Insights, secondary chart
    series, links). Never used for primary CTAs, so it stays legible as "this is analysis, not an action."
> - P&L color pair, reserved *exclusively* for gain/loss meaning — never reused decoratively: `--bull-500 #2FBE7A` (
    gains, buy, bullish), `--bear-500 #F0555A` (losses, sell, bearish).
> - Text: `--paper-100 #EDEFF3` (primary), `--paper-400 #8A93A6` (secondary), `--paper-600 #545C6B` (muted/disabled).
> - Hairline borders: `--hairline #2A303C`.
> - Build the light theme as the tonal inverse of this palette (don't just flip to Material defaults) so both modes
    share the same identity.
>
> **Typography** — three roles, tabular numerals are non-negotiable in a price table:
> - Display/headings: **Space Grotesk** (600/700 weight) — gives the terminal a bit of engineered character instead of
    another Inter/Roboto dashboard.
> - UI/body text: keep **IBM Plex Sans** (400/500) — already loaded, reads well at the dense 13px base size this app
    uses.
> - Numeric data — every price, P&L figure, quantity, percentage, and table numeric column: **IBM Plex Mono** with
    `font-variant-numeric: tabular-nums`, so columns of numbers align vertically like a real ticker tape. This is a
    functional requirement, not a style preference — misaligned decimals in a price table is a usability bug.
>
> **Signature element — the Ticker Marquee.** Add a thin (28–32px), full-bleed, continuously auto-scrolling strip
> directly beneath the app shell's top toolbar, present on every authenticated screen, showing the user's watchlist
> symbols with live price and a small directional arrow, color-coded bull/bear. This is the one unmistakable visual
> signature of the app — the thing that makes it read as *a terminal* on first glance instead of *a dashboard*. Pause the
> scroll on hover; respect `prefers-reduced-motion` by freezing it into a static, horizontally-scrollable row instead.
>
> **Live-update micro-interaction.** Define a reusable `.tp-flash-bull` / `.tp-flash-bear` utility (or a small
> directive) that briefly flashes a cell or number's background the P&L color and fades over ~500ms whenever a live value
> changes — the classic terminal "something just moved" cue. You'll wire this into Dashboard, Live Trading, Charting, and
> the watchlist in later prompts; build the utility now so it's shared.
>
> **Spacing & shape:** 4px base grid (4/8/12/16/24/32/48). Radius: `--tp-radius-sm 6px` (chips, inputs),
`--tp-radius-md 10px` (buttons, table rows), `--tp-radius-lg 16px` (cards, panels). Keep card surfaces flat with a 1px
> hairline border rather than heavy shadows — a terminal is precise, not glossy; reserve `--tp-shadow` for genuinely
> elevated things like open menus and dialogs.
>
> Do not touch any feature component in this step — this is tokens and the marquee component only. Confirm the app still
> builds and every existing screen still renders legibly (Material tables, chips, and buttons should visibly pick up the
> new palette through the bridge with zero component-level changes).

**Done when:** `ng build` succeeds, the whole app (every existing screen) visibly uses the new palette purely through
the token bridge, and the marquee renders in isolation with mock data.

---

## Prompt 2 — App Shell, Navigation & Shared States

**Context:** `core/layout/app-shell.component.ts` already has the right bones — a collapsible rail sidebar (
`sidebarExpanded` signal), a nav-items array driving 10 routes, an admin-only nav gate (`isAdmin` computed off
`AuthSessionService.hasRole('ROLE_ADMIN')`), the `NotificationBellComponent`, the `WatchlistWidgetComponent`, dark/light
toggle, and a `flushContent` signal that drops page padding specifically for `/charting`. Refine, don't replace.

> Refine `core/layout/app-shell.component.*` on top of Prompt 1's tokens:
>
> 1. Mount the Ticker Marquee directly under the top toolbar, above `<router-outlet>`, on every route except
     `/charting` (full-bleed chart already owns that space) and the public `/login`, `/register`, `/embed/chart` routes.
> 2. Sidebar: icon + label rail, collapsible to icon-only via `toggleSidebar()`. Active route gets the brass accent (
     left border or filled pill, your call) — not the default Material ripple-blue. Keep the existing `visibleNavItems`
     admin-gating logic exactly as-is.
> 3. Top toolbar: keep the theme toggle, the tools-panel toggle (`toggleTools()`), and `NotificationBellComponent` —
     restyle them onto the new tokens, don't rewire their logic.
> 4. Responsive: below ~768px, collapse the rail sidebar into a bottom tab bar with the same `visibleNavItems`, and keep
     the marquee but drop it to a single-line auto-scroll (no multi-row wrap).
> 5. Build two small shared presentational components other prompts will reuse everywhere, under `shared/`:
     >
- `EmptyStateComponent` — icon slot, headline, one line of body copy, optional action button. Copy is written per-screen
  later; this component just lays it out consistently.
>    - `LoadingStateComponent` (or a skeleton-row directive) — a themed loading placeholder so every screen's "
       fetching…" moment looks intentional instead of a bare spinner dropped in a blank card.
> 6. Every list/table screen built in later prompts must use these two for its empty and loading states — call this out
     explicitly when you get to Prompts 4–13 rather than re-deciding it each time.
>
> Do not touch feature-page components yet — shell, marquee mount point, and the two shared state components only.

**Done when:** the shell frames every authenticated route with the marquee and refreshed nav, collapses correctly on
mobile, and `EmptyStateComponent`/`LoadingStateComponent` exist and render with placeholder content.

---

## Prompt 3 — Auth: Login, Register, OAuth Callback

**Route:** `/login`, `/register`, `/auth/callback` (public, no shell, no `authGuard`)
**Backend:** identity-service — `POST /api/auth/login`, `POST /api/auth/register`, Google OAuth2 initiation +
`/auth/callback` handling
**Reuse:** `AuthSessionService`, `AuthService` (`features/auth/auth.service.ts`) — do not change their public API.

> Redesign `LoginPageComponent`, `RegisterPageComponent`, and `OauthCallbackPageComponent` under `features/auth/`.
>
> This is the one screen with no sidebar and no marquee — it's the front door, not the terminal. Use it to establish
> first impression: full-bleed `--ink-950` background, the product name set in Space Grotesk, and one small piece of "
> proof of substance" — a muted, non-interactive candlestick silhouette or the marquee's visual language (rows of tiny
> price ticks) rendered decoratively in the background at low opacity. Don't animate it distractingly; it should read as
> texture, not a hero.
>
> **Login:** email + password fields, inline validation (not just a toast on submit-fail), a primary brass CTA, and a
> secondary "Continue with Google" button that redirects to identity-service's OAuth2 initiation endpoint (confirm the
> exact entrypoint in `SecurityConfig` — it is not the `/auth/callback` URL itself). On success, write the JWT to
`localStorage['mtp_auth_token']`, call `authSession.refresh()`, then navigate to `/dashboard` — don't force a full page
> reload.
>
> **Register:** matching visual language, add password-confirmation and a real-time password-strength indicator (
> length/complexity, not just "weak/strong" text — a small segmented bar reading directly off the same validators driving
> the submit button's disabled state).
>
> **OAuth callback:** a minimal, branded loading state ("Signing you in…") using `LoadingStateComponent` from Prompt 2
> while the token exchange completes, then the same redirect-and-refresh flow as login. If the exchange fails, show a
> clear inline error with a link back to `/login` — never a raw stack trace.
>
> Both forms need a visible error state for a rejected login/registration (wrong credentials, duplicate email) that
> reads as the interface's voice — direct and specific ("That email is already registered — try signing in instead"), not
> a generic "An error occurred."

**Done when:** login/register/OAuth all round-trip correctly against identity-service, the notification bell connects
post-login with zero manual page refresh, and both forms are keyboard-navigable with visible focus states.

---

## Prompt 4 — Dashboard

**Route:** `/dashboard` (default route after login)
**Backend:** trading-service — `GET /api/portfolio/stats` (`PortfolioStats`: `totalPnl`, `winRate`, `tradeCount`,
`openPositions`, `equityCurve: [ISO timestamp, value][]`), `GET /api/trades?limit=20` (`TradesResponse` →
`TradeSummary[]`); reference-data-service via `EconomicCalendarApiService` (`GET /api/reference/calendar`)
**Reuse:** `ChartLibraryModule` in line-chart mode for the equity curve, `WatchlistWidgetComponent` (already lives in
the shell's tools panel — don't duplicate it here), `EconomicCalendarComponent` (already built — restyle, don't rebuild
the data logic).

> Redesign `DashboardModule` (`features/dashboard/dashboard-page.component.*`) as the landing screen after login — it
> needs to answer "how am I doing" in the first three seconds.
>
> **Layout, top to bottom:**
> 1. A row of KPI cards for `totalPnl` (with % alongside the $ figure), `winRate`, `tradeCount`, `openPositions` —
     numeric values in IBM Plex Mono tabular-nums, P&L card colored bull/bear based on sign, not a neutral card with a
     colored number buried inside it.
> 2. An equity curve using `ChartLibraryModule`'s line-chart mode, fed by `equityCurve`, with 1W/1M/3M/ALL range filters
     as a segmented control (not a dropdown — this is glanced at constantly). Gradient-fill the area under the line in a
     muted brass or bull/bear tone depending on whether the period is net positive or negative.
> 3. A recent-trades table (`TradeSummary[]`, capped at the 20 the endpoint returns) — symbol, side, entry/exit,
     quantity, P&L (colored), status chip (OPEN/CLOSED), opened/closed timestamps. Row actions: view in Journal, close (
     if OPEN, opens the exit-price flow), delete (confirm first). Use `EmptyStateComponent` when there are zero trades
     yet, with a "Log your first trade" CTA into `/live-trading`.
> 4. The `EconomicCalendarComponent` widget, restyled onto the new tokens as a compact card — this is reference data,
     not analysis, so keep it visually quieter than the KPI row: a simple date/event/impact-badge list, no chart.
>
> Apply the `.tp-flash-bull`/`.tp-flash-bear` utility from Prompt 1 to the KPI numbers if the dashboard is left open and
> stats refresh live.
>
> Responsive: KPI row wraps to a 2-column grid under ~900px, equity curve and recent-trades stack full-width.

**Done when:** the dashboard renders real portfolio numbers, the equity curve range filter actually re-queries or
re-slices data, and the recent-trades row actions call the same trade-mutation endpoints the Journal screen uses (no
duplicated close/delete logic — extract a shared helper if needed).

---

## Prompt 5 — Live Trading

**Route:** `/live-trading`
**Backend:** trading-service `POST /api/trades` (open), `POST /api/trades/{id}/close`; market-service WebSocket for live
OHLCV (check `MarketStreamController`'s STOMP topic naming and subscribe the same way `notification.service.ts` does —
build a `MarketDataSocketService` following that exact pattern, this already exists at
`core/api/market-data-socket.service.ts`, reuse it, don't fork it)
**Models:** `Trade`, `CreateTradeRequest` (`symbol`, `side: 'BUY'|'SELL'`, `type: 'SPOT'|'FUTURES'`, `entryPrice`,
`quantity`, `broker?`, `notes?`), broker list currently just `AVAILABLE_BROKERS = [{id:'BITUNIX', label:'BitUnix'}]`
**Reuse:** `TradeApiService` (shared with Journal — don't duplicate trade HTTP calls), `ChartLibraryModule`,
`SymbolSearchComponent`.

> Redesign `LiveTradingModule` (`features/live-trading/live-trading-page.component.*`) as a real order-entry
> workstation, not a form bolted next to a chart.
>
> **Layout:** a two-pane workspace — chart dominant (60–70% width on desktop), order ticket docked to the right as a
> fixed-width panel (stacks below the chart on mobile, not squeezed sideways).
>
> - **Chart pane:** `ChartLibraryModule`'s `CandlestickChartComponent` fed live OHLCV via `MarketDataSocketService`,
    symbol picked through `SymbolSearchComponent`. Feed a `livePrice` input so the chart's crosshair/last-price line
    updates in real time — the component itself opens no sockets, this page owns the subscription.
> - **Order ticket:** broker picker (currently `AVAILABLE_BROKERS`, structured so adding a second broker later is a
    one-line change, not a template rewrite), BUY/SELL toggle styled as a clear two-state segmented control colored
    bull/bear (this is the one screen where that color pairing should feel almost aggressive — it's the moment of
    commitment), SPOT/FUTURES type, quantity and entry price (pre-fillable from the live quote with a "use market price"
    action), optional notes. Show a live-computed preview directly above the submit button: invested amount, and — if
    the user has entered a stop-loss/target informally in notes — nothing fancier than what the DTO supports; don't
    invent fields the backend doesn't have.
> - On submit, `POST /api/trades` via `TradeApiService`; on success, a toast confirmation plus the new position
    appearing immediately in an "open positions" strip below or beside the order ticket (client-side optimistic update
    is fine, reconciled on the next fetch).
> - Each open position needs an inline **close** action (opens a small exit-price prompt, calls `/trades/{id}/close`),
    consistent with how Dashboard and Journal close trades — extract shared logic rather than re-implementing the close
    flow a third time.
>
> Flash the live price and any open position's unrealized P&L using the Prompt 1 flash utility on update.

**Done when:** a user can pick a symbol, watch a live chart, submit an order, see it appear as an open position, and
close it — end to end, with zero duplicated trade-API code versus Journal.

---

## Prompt 6 — Journal

**Route:** `/journal`
**Backend:** trading-service via the same `TradeApiService` as Live Trading — `Trade`, `PagedTrades`, `TradeFilter` (
`status?: 'OPEN'|'CLOSED'|'ALL'`, `symbol?`, `from?`, `to?`, `page?`, `size?`), `CloseTradeRequest`
**Reuse:** `TradeApiService`, `EmptyStateComponent`, `LoadingStateComponent`. Already uses Material
table/paginator/dialog/chips/datepicker — keep that toolset, restyle it.

> Redesign `JournalModule` (`features/journal/journal-page.component.*`) as the full trade-history record — this is the
> screen a user opens to actually review their behavior, so density and scanability matter more than decoration here.
>
> **Filter bar** (persistent, not collapsed behind a button): status chips (All/Open/Closed) as a Material chip group
> reading directly off `TradeFilter.status`, symbol search using `SymbolSearchComponent`, and a date-range picker for
`from`/`to`. Filters apply immediately (debounced), not behind an "Apply" button — this is a data-review tool, not a
> form.
>
> **Table:** every column from `Trade` that matters for review — symbol, side, type (SPOT/FUTURES), entry/exit price,
> quantity, P&L (bull/bear colored, tabular-nums), status chip, opened/closed timestamps, broker, and a notes preview (
> truncated, expandable on click). Paginated via `PagedTrades`'s `number`/`size`/`totalElements` against
`MatPaginatorModule`. Row actions: close (OPEN only, reuse the same close-flow as Live Trading and Dashboard), edit
> notes, delete (confirmation dialog — this is destructive and permanent).
>
> Use `EmptyStateComponent` for "no trades match these filters" (distinct copy from "no trades logged yet at all" —
> check which case you're in and phrase accordingly) and `LoadingStateComponent` while a page fetches.
>
> If a trade row has an AI critique available (cross-reference with Prompt 9's `journal-critique` endpoint), surface a
> small "Ask AI" action per closed trade that navigates into AI Insights with that trade's context — don't build a second
> critique UI here, just the entry point.

**Done when:** filtering, pagination, close, and delete all work against real data, and the "Ask AI" entry point
correctly hands off trade context to AI Insights.

---

## Prompt 7 — Charting Workspace

**Route:** `/charting` (full-bleed — shell already special-cases this route to drop padding and hide the marquee)
**Backend:** market-service — `/api/charts/drawings` (`ApiChartDrawing`), `/api/charts/layouts` (`DrawingLayout`,
`CreateLayoutRequest`), `/api/charts/layouts/settings` (`GlobalDrawingSettings`), `/api/indicators/configs` (
`IndicatorConfig`), `/api/indicators/{symbol}/compute` (`IndicatorResult`)
**Reuse:** `ChartLibraryModule` in full — this is its primary consumer. The charting API service maps between
`ApiChartDrawing` (persistence DTO) and the chart library's own presentation-layer drawing model; don't merge those two
types into one.

> Redesign `ChartingModule` (`features/charting/charting-page.component.*`) as the primary analysis workspace — the one
> screen that should feel like a professional charting terminal, unapologetically dense.
>
> **Layout:** chart canvas fills essentially the whole viewport. A slim top strip holds symbol (
`SymbolSearchComponent`), timeframe selector, and bar-count control. A collapsible left or right tool rail holds drawing
> tools (trend line, Fibonacci, rectangle — whatever `CandlestickChartComponent`'s `DrawingToolType` enum currently
> supports) as icon buttons, not a dropdown — drawing tools need to be one click away mid-analysis. An indicator panel (
> collapsible) lists active `IndicatorConfig`s with enable/disable toggles and per-indicator color swatches, pulling live
> computed series from `/api/indicators/{symbol}/compute`.
>
> **Drawings & layouts:** every drawing action in the chart emits `(drawingChanged)`; this page persists it via
`/api/charts/drawings`, mapping to `SaveDrawingRequest`. Support saving/loading named layouts (`DrawingLayout`) via a
> compact layout switcher (dropdown is fine here — it's an infrequent action, unlike the drawing tools) with a "save as
> new layout" and "set as default" (writes `GlobalDrawingSettings.defaultLayoutId`) action.
>
> **Chart theme:** feed `GlobalDrawingSettings.theme` into `CandlestickChartComponent` so drawings persist their
> intended dark/light appearance independent of the app-wide theme toggle if the two ever diverge (they usually won't —
> but the field exists on the backend, respect it rather than ignoring it).
>
> This screen owns zero of its own charting logic beyond data-fetching and persistence — every candle, overlay, and
> drawing render goes through `ChartLibraryModule`. If something the design needs isn't supported by the library yet,
> that's a `ChartLibraryModule` change, not a one-off reimplementation here.

**Done when:** indicators toggle live against real computed data, drawings persist across a page reload, and layout
save/load round-trips correctly.

---

## Prompt 8 — Alerts

**Route:** `/alerts`
**Backend:** alert-service — `GET/POST/PUT/DELETE /api/alerts` (`PriceAlert`: `symbol`,
`condition: 'ABOVE'|'BELOW'|'CROSSES_ABOVE'|'CROSSES_BELOW'`, `targetValue`, `status: 'ACTIVE'|'FIRED'|'DISABLED'`,
`message`, `firedAt`), `SaveAlertRequest`
**Distinction to hold onto:** this module is alert *management* (CRUD), separate from `NotificationBellComponent`, which
already handles live delivery when an alert fires — don't rebuild delivery here, only creation/editing/history.

> Redesign `AlertsModule` (`features/alerts/alerts-page.component.*`) as full alert-management CRUD, visually distinct
> from — but consistent with — the notification bell that delivers these when triggered.
>
> **Create/edit:** a form (dialog or inline panel, your call) for symbol (`SymbolSearchComponent`), condition (`ABOVE`/
`BELOW`/`CROSSES_ABOVE`/`CROSSES_BELOW` as a clear labeled selector — plain English, not the enum verbatim: "Price rises
> above", "Price falls below", "Crosses above", "Crosses below"), target value, and an optional custom message.
> Live-preview the condition as one readable sentence above the submit button ("Alert me when BTCUSDT crosses above $
> 72,000") — this is cheap to build and meaningfully reduces user error on a feature where the cost of misconfiguring is
> silence when they needed a signal.
>
> **List:** a table or card grid (a light card grid probably reads better here than a dense table — each alert is a
> short, glanceable statement, not a data row to scan across) showing symbol, the condition sentence, current status (
`ACTIVE`/`FIRED`/`DISABLED` as colored status chips — brass for active, bull/bear tinted for fired depending on whether
> it was a bullish or bearish trigger, muted for disabled), and `firedAt` if set. Row actions: toggle active/disabled,
> edit, delete (confirm).
>
> `EmptyStateComponent` for zero alerts, with a direct CTA into the create form. Group or sort fired alerts separately
> from still-active ones so the list doesn't read as an undifferentiated pile — active alerts are what the user is
> watching for, fired ones are history.

**Done when:** full CRUD works against `alert-service`, status toggling is immediate (optimistic UI, reconciled on
response), and the condition sentence preview matches what actually gets sent to the API.

---

## Prompt 9 — AI Insights

**Route:** `/ai-insights`
**Backend:** ai-service — `GET /api/ai/summary/{symbol}` (`AiSummary`: `summary` markdown/text,
`sentiment: 'BULLISH'|'BEARISH'|'NEUTRAL'`, `confidence` 0–1), `GET /api/ai/signals/{symbol}` (`AiSignal[]`:
`type: 'BUY'|'SELL'|'HOLD'`, `reasoning`, `confidence`, `targetPrice`, `stopLoss`), `POST /api/ai/journal-critique` (
`JournalCritiqueRequest` → `JournalCritique`: `critique`, `rating: 'GOOD'|'AVERAGE'|'POOR'`, `suggestions[]`)
**Note:** LLM calls are meaningfully slower than every other endpoint in this app — the loading state here needs to be a
first-class design decision, not an afterthought spinner.

> Redesign `AiInsightsModule` (`features/ai-insights/ai-insights-page.component.*`).
>
> **Layout:** symbol picker (`SymbolSearchComponent`) at top, then two sections:
> 1. **Market summary card** — `AiSummary.summary` rendered as markdown, with sentiment shown as a clear badge (
     BULLISH/BEARISH/NEUTRAL, bull/bear/neutral colored) and confidence as a slim progress bar, not just a raw "0.73".
     Timestamp the summary ("Generated 4 minutes ago") since LLM output goes stale.
> 2. **Signals list** — `AiSignal[]` as cards: BUY/SELL/HOLD badge, confidence bar, target price and stop-loss shown
     together as a mini risk/reward strip when both are present, and the `reasoning` text collapsed by default with a "
     why?" expand — traders scanning multiple signals want the verdict first, the explanation on demand.
>
> **Loading state is the design problem here, not a footnote:** since responses can take several seconds, show a
> purpose-built loading sequence (a few staged skeleton lines suggesting "reading the market…", or a short rotating status
> line) rather than a generic spinner sitting in dead space — the user needs to feel the system is working, not frozen.
>
> **Journal critique entry point:** accept an optional trade context (arriving from the Journal screen's "Ask AI" action
> built in Prompt 6) and, when present, show a dedicated critique panel: `POST journal-critique` with
`JournalCritiqueRequest`, render the returned `rating` as a clear badge (GOOD/AVERAGE/POOR), the `critique` text, and
`suggestions[]` as a short actionable list, not a wall of prose.
>
> Handle the "AI service slow/unavailable" case explicitly — a distinct error state (not the generic empty state) that
> says plainly that generation failed and offers a retry, since this is the one screen where "try again" is a genuinely
> likely fix.

**Done when:** summary and signals load for a real symbol with a considered loading experience, and the journal-critique
panel correctly receives and displays context handed off from the Journal screen.

---

## Prompt 10 — On-Chain

**Route:** `/on-chain`
**Backend:** reference-data-service — `GET /api/reference/nft/collections` (`NftCollection`: `name`, `symbol`,
`contractAddress`, `chain`, `floorPrice`, `volume24h`, `itemCount`, `ownerCount`, `imageUrl`, `marketCap`),
`GET /api/reference/defi/pools` (`DefiPool`: `protocol`, `chain`, `token0Symbol`/`token1Symbol`, `feeTier` basis points,
`tvl`, `volume24h`, `apy` 0–1, `poolAddress`)

> Redesign `OnChainModule` (`features/on-chain/on-chain-page.component.*`) with two clearly separated views — NFT
> Collections and DeFi Pools are different mental models and shouldn't share one table shape.
>
> **View switcher:** a simple two-tab or segmented control at the top (NFTs / DeFi), not a route split, since they share
> the same page shell and filter chrome (chain filter applies to both).
>
> **NFT Collections view:** a card grid (this data is inherently visual — `imageUrl` deserves to be shown, not buried in
> a table cell), each card showing the collection image, name/symbol, chain badge, floor price and 24h volume
> prominently (tabular-nums), item/owner counts as secondary stats. Sort/filter by chain and by floor price.
>
> **DeFi Pools view:** this data is comparative and numeric (TVL, APY, volume) — a table serves it better than cards.
> Columns: protocol, chain, pair (`token0Symbol`/`token1Symbol` as "ETH / USDC" style), fee tier (format the basis points
> as a percentage — 30 → "0.30%"), TVL, 24h volume, APY (format the 0–1 float as a percentage, colored toward brass/teal
> for standout high-yield rows rather than bull/bear — this isn't a gain/loss, don't borrow that color pair for it).
> Sortable by TVL and APY by default.
>
> Handle `null` fields gracefully throughout (floor price, volume, APY, etc. are all nullable on the DTOs) — show "—"
> or "Not available", never a literal "null" or a blank cell that reads as a loading bug.

**Done when:** both views render real data with correct null-handling, and sort/filter work independently per view.

---

## Prompt 11 — Reports

**Route:** `/reports`
**Backend:** trading-service — `GET /api/reports/yearly` (`YearlyReport`: `year`, `totalTrades`, `totalWins`,
`totalLosses`, `grossPnl`, `netPnl`, `winRate`, `avgRrr`, `maxDrawdown`, `sharpeRatio` nullable,
`months: MonthlyBreakdown[]`), `GET /api/reports/export.xlsx`

> Redesign `ReportsModule` (`features/reports/reports-page.component.*`) as a performance-review screen, not a data
> dump.
>
> **Year selector** at top (the endpoint is year-scoped — build the selector assuming multiple years become browsable
> even if only the current year has data today).
>
> **Summary strip:** the top-level `YearlyReport` fields as a KPI row — net P&L (bull/bear colored, tabular-nums), win
> rate, avg risk-reward ratio, max drawdown, Sharpe ratio (show "Not enough data" rather than a blank or zero when `null`,
> since a null Sharpe ratio has a specific, explainable cause — insufficient trade history — and the interface should say
> so).
>
> **Monthly breakdown:** `months: MonthlyBreakdown[]` as a bar chart (net P&L per month, bull/bear colored bars) via
`ChartLibraryModule`, plus a compact table beneath it for the underlying numbers (trades, wins, losses, gross/net P&L,
> win rate, avg RRR per month) for anyone who wants the exact figures rather than reading them off a bar.
>
> **Export:** an "Export to Excel" action calling `GET /api/reports/export.xlsx` with `responseType: 'blob'`, triggering
> a real browser file download — never opening the file in-app or navigating away from the page. Show a brief in-progress
> state on the button itself (spinner replacing the icon, button disabled) since this is a file generation call, not
> instant.

**Done when:** the yearly report renders correctly for a year with real data, gracefully handles a year with zero
trades (`EmptyStateComponent`, not a broken chart), and the Excel export triggers an actual file download.

---

## Prompt 12 — Settings

**Route:** `/settings`
**Backend:** identity-service — `GET/PUT /api/profile` (`UserProfile`: `username`, `email` read-only, `displayName`,
`avatarUrl`, `timezone`, `currency` ISO 4217), `GET/PUT /api/profile/preferences` (`NotificationPreferences`:
`inAppEnabled`, `emailEnabled`, `email`, `telegramEnabled`, `telegramChatId`), and broker linking via
`BrokerLinkApiService` (`/api/auth/brokers/{brokerType}/connect|revoke`, `BrokerConnectionStatus`: `brokerType`,
`label`, `connected`, `maskedApiKey`)

> Redesign `SettingsModule` (`features/settings/settings-page.component.*`) as three clearly separated sections on one
> page (tabs or stacked cards with clear headers — avoid a single undifferentiated form, these are three different mental
> tasks).
>
> **Profile:** editable fields only — `displayName`, `avatarUrl` (with a live preview of the avatar), `timezone` (
> searchable select, not a raw text field), `currency` (ISO 4217 select with currency symbol shown alongside the code).
`username` and `email` render as read-only, visually distinct from the editable fields (not just disabled-looking
> inputs — actual static text with a small "read-only" affordance) so it's obvious at a glance which fields respond to
> editing.
>
> **Notifications:** toggle rows for `inAppEnabled`, `emailEnabled` (revealing an email field when on, pre-filled from
`NotificationPreferences.email` if set, otherwise the profile email), `telegramEnabled` (revealing a chat-ID field with
> a short inline note on how to obtain a Telegram chat ID, since that's the one field a user won't know how to fill
> without guidance). Each channel toggle should read as clearly independent — a user might want email alerts but not
> Telegram, and the layout should make that obviously possible rather than implying an all-or-nothing bundle.
>
> **Connected brokers:** list current connections via `listConnections()` — broker name/logo-style badge, masked API
> key (`maskedApiKey`), connected status, a "Disconnect" action (confirm first — this affects Live Trading). A "Connect a
> broker" flow: broker picker (same `AVAILABLE_BROKERS` list as Live Trading — don't fork a second broker list), API key +
> secret fields (masked inputs, standard password-field pattern, with a clear note that these are sent securely and never
> displayed again after saving), optional label. Handle `BrokerLinkResponse.implemented === false` (a broker that's listed
> but not yet wired up) as a distinct disabled state with a "Coming soon" note, not a clickable action that silently
> fails.
>
> Save actions per section (not one giant "Save all" at the bottom) — profile, notifications, and broker connections are
> independent operations against different endpoints and should give independent, immediate feedback (a toast scoped to
> what was actually changed).

**Done when:** all three sections independently read and write against their real endpoints, and the broker
connect/disconnect flow correctly reflects `implemented: false` brokers as not-yet-available rather than broken.

---

## Prompt 13 — Admin

**Route:** `/admin` (behind `authGuard` **and** `adminGuard` — verify both are actually enforced, not just the nav link
hidden)
**Backend:** identity-service — `GET /api/admin/users` (`AdminUser[]`: `username`, `email`, `displayName`,
`roles: ('ROLE_ADMIN'|'ROLE_USER'|'ROLE_MODERATOR')[]`, `status: 'ACTIVE'|'SUSPENDED'|'PENDING'`, `createdAt`,
`lastLoginAt`), `PUT /api/admin/users/:id` (`UpdateUserRequest`), `DELETE /api/admin/users/:id`

> Redesign `AdminModule` (`features/admin/admin-page.component.*`) as a straightforward, low-drama user-management
> table — admin tools should feel calm and precise, not flashy; this is the one screen where restraint matters most
> because mistakes here affect other people's accounts.
>
> **Table:** username, email, display name, role badges (multiple roles per user — render as a small chip group, not a
> single value), status (`ACTIVE`/`SUSPENDED`/`PENDING` as colored status chips — don't reuse bull/bear here, these aren't
> gains/losses; use a neutral traffic-light set: brass/teal for active, muted for pending, bear-adjacent-but-distinct for
> suspended so it doesn't visually collide with "loss" elsewhere in the app), `createdAt`, `lastLoginAt` (relative time, "
> 3 days ago", with the exact timestamp on hover).
>
> **Row actions:** edit roles (multi-select of the three role values), change status, delete user — every one of these
> needs an explicit confirmation dialog stating exactly what will change ("Suspend this user? They'll be signed out and
> unable to log in until reactivated" — not a generic "Are you sure?"), since these are consequential, hard-to-undo
> actions on someone else's account.
>
> **Search/filter:** by username/email, and a status filter — this table will grow, don't ship it assuming it always
> fits on one screen without pagination.
>
> Confirm in this pass that `adminGuard` genuinely blocks route access for non-admins (test by navigating directly to
`/admin` as a non-admin user, not just checking the nav link is hidden) — this was flagged as a requirement, not an
> assumption, in the original migration notes.

**Done when:** the user table is fully functional against real data, every mutating action has a specific confirmation,
and route-level admin gating is verified, not just visually assumed.

---

## Prompt 14 — Embed Chart (public, detachable)

**Route:** `/embed/chart` (public — authenticates via an embed API key header, not a user JWT)
**Backend:** market-service via `EmbedChartApiService` — `getOhlcv(embedKey, symbol, timeframe, limit)`,
`computeIndicators(embedKey, symbol, timeframe, limit)`, both sent with an `X-Embed-Key` header

> Redesign `ChartEmbedPageComponent` (`features/embed/chart-embed-page.component.*`) as a minimal, chrome-free chart
> meant to be dropped into an `<iframe>` elsewhere — this is the one screen that should have almost none of the app's
> shell around it.
>
> No sidebar, no marquee, no toolbar — just `CandlestickChartComponent` filling the viewport, reading `symbol`/
`timeframe`/the embed key from query params, with a tiny unobtrusive attribution/branding mark in a corner (small enough
> not to compete with the chart, present enough that it's clearly this platform's chart when embedded elsewhere).
> Background should honor the app's dark theme by default but stay a clean, uncluttered fill — no card borders or padding
> fighting the embedding page's own layout.
>
> Handle a missing/invalid embed key or symbol as a small, centered, plain-language error inside the frame ("This chart
> link is no longer valid") rather than a broken/blank iframe — whoever embedded this has no console access to debug it,
> the error has to be self-explanatory from the rendered output alone.

**Done when:** the embed loads a real chart standalone (test it inside a bare `<iframe>`, not just as a routed page in
the full app), and an invalid key produces a clear in-frame error instead of a blank screen.

---

## Prompt 15 — Final Consistency, Accessibility & QA Pass

Run this last, after every screen above has been rebuilt.

> Do a full production build (`ng build --configuration production`) and fix every error and warning. Then do a
> screen-by-screen pass across all 12 routes in the table at the top of this document, checking:
>
> 1. **Token consistency** — no screen has a stray hardcoded color, font, or spacing value that bypasses the `--tp-*`
     tokens from Prompt 1. Grep for hex literals and hardcoded `px` font-sizes outside `styles.scss` as a starting
     check.
> 2. **P&L color discipline** — bull/bear green/red is used *only* for gain/loss and buy/sell meaning across every
     screen (Dashboard, Live Trading, Journal, Reports, Alerts), never repurposed decoratively elsewhere (On-Chain APY,
     Admin status chips) — confirm Prompts 10 and 13's color guidance was actually followed.
> 3. **Loading/empty/error states** — every list- or data-driven screen uses `LoadingStateComponent`/
     `EmptyStateComponent` from Prompt 2 rather than a bare spinner or a blank div; every screen that calls a backend
     has a visible, specific error state (not just a console error) for a failed request.
> 4. **Numeric alignment** — every table or card showing prices, quantities, or P&L uses tabular-nums; scan for any
     column where digits visibly don't align.
> 5. **Responsiveness** — every screen down to a ~375px mobile viewport: no horizontal scroll traps, the shell's
     bottom-tab collapse works, Charting and Live Trading (the two chart-heavy screens) remain usable rather than just "
     not broken."
> 6. **Accessibility floor** — visible keyboard focus rings using the brass accent (not the browser default) on every
     interactive element, all form fields have associated labels (not just placeholder text), color is never the only
     signal for status (pair every status chip with text, not just a color), `prefers-reduced-motion` is honored by the
     marquee and any flash/transition animations.
> 7. **Auth/session integrity** — confirm nothing added a second JWT store, a second broker list, a second close-trade
     implementation, or a second drawing-persistence path across all the prompts above; there should be exactly one of
     each.
>
> Report the results as an explicit checklist against these seven points and the 12-screen table — not a general "looks
> good," a screen-by-screen pass/fail with specifics for anything that fails.

---

## Suggested prompt order & dependencies

| #  | Prompt                    | Depends on                         |
|----|---------------------------|------------------------------------|
| 0  | Session context primer    | — (paste every session)            |
| 1  | Design system & tokens    | —                                  |
| 2  | Shell, nav, shared states | 1                                  |
| 3  | Auth                      | 1 (2 optional — auth has no shell) |
| 4  | Dashboard                 | 1, 2                               |
| 5  | Live Trading              | 1, 2                               |
| 6  | Journal                   | 1, 2, 5 (shared trade logic)       |
| 7  | Charting                  | 1, 2                               |
| 8  | Alerts                    | 1, 2                               |
| 9  | AI Insights               | 1, 2, 6 (critique hand-off)        |
| 10 | On-Chain                  | 1, 2                               |
| 11 | Reports                   | 1, 2                               |
| 12 | Settings                  | 1, 2, 5 (shared broker list)       |
| 13 | Admin                     | 1, 2                               |
| 14 | Embed Chart               | 1 only (deliberately shell-free)   |
| 15 | Final QA pass             | all above                          |

Prompts 4, 7, 8, 10, 11, 13 have no cross-feature dependencies beyond the shell — reorder freely among themselves if you
want to tackle the app in a different sequence.
