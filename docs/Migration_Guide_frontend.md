# Matthew Trading Platform — Angular 17 Frontend Migration Guide (Agent-Optimized)

**Companion to:** `Migration_Guide_3.md` (backend gap-closure — read that first, this guide assumes every
API it lists exists) and `MTP_Microservices_Migration_Guide.md` Phase D, Steps 12–14 (the original
frontend scaffolding steps — this guide replaces/expands them with what's actually needed).

**How to use this doc:** same rules as the other three guides — one step per prompt, paste verbatim, verify
before advancing, keep `.cursorrules` + Appendix C below loaded.

---

## Part 1 — Audit: What's Actually in `frontend/` Right Now

`frontend/` is a real Angular 17 standalone workspace, but only two small slices of Step 4.75 got built —
nothing from Steps 12–13 exists yet:

| Piece                                                        | State                                                        |
| ------------------------------------------------------------ | ------------------------------------------------------------ |
| `package.json`                                               | Angular 17.3 core packages + `@stomp/stompjs` + `sockjs-client` only. **No Angular Material, no ECharts, no RxJS operators beyond core** — despite both being specified in the original guide. |
| `app.config.ts`                                              | Only `provideRouter(routes)`. **No `provideHttpClient()`, no JWT interceptor.** |
| `app.routes.ts`                                              | `export const routes: Routes = [];` — literally empty.       |
| `core/auth/auth-session.service.ts`                          | Reads/decodes a JWT from `localStorage['mtp_auth_token']` and exposes `token`/`getCurrentUserId()` as signals. **Explicitly documented as not doing login itself** — it's waiting for the module built in this guide's Step 2. |
| `core/notification/notification.service.ts` + `shared/notification-bell/` | A real, working STOMP/SockJS client against `{gatewayBaseUrl}/ws`, subscribed to `/topic/alerts/{userId}`. This is a good reference for the WebSocket pattern every live-data module below should reuse. |
| Feature modules (Dashboard, LiveTrading, Journal, Alerts, Settings, Charting, AI, On-Chain, Reports, Admin) | **None exist.**                                              |
| `ChartLibraryModule`                                         | **Does not exist.** Nothing renders a candle yet.            |

**Established conventions already in the codebase — follow these, don't reinvent them:**

- All standalone components/services, `signal()` for reactive state (not `BehaviorSubject`-heavy patterns).
- `environment.gatewayBaseUrl` is the *only* base URL anything talks to — never a microservice's own port.
- JWT lives in `localStorage['mtp_auth_token']` (`AuthSessionService.TOKEN_STORAGE_KEY`); anything that adds
  a login flow **must** write to this exact key and call `authSession.refresh()` afterward, or the
  notification bell (and everything else reading the signal) won't notice the new session.
- WebSocket/STOMP connections go through the Gateway (`{gatewayBaseUrl}/ws`) with `Authorization: Bearer`
  as a **STOMP connect header**, not a URL param — see `notification.service.ts`.

---

## Part 2 — Screen → Module → API Map

The JavaFX desktop app has 16 view controllers. None of them port 1:1 — each becomes an Angular
**standalone feature module** calling the REST APIs `Migration_Guide_3.md` builds. This table is the
frontend equivalent of that guide's Part 2 ownership map:

| Desktop JavaFX controller                                    | Angular module                                               | Backend API it calls (via Gateway)                           |
| ------------------------------------------------------------ | ------------------------------------------------------------ | ------------------------------------------------------------ |
| `LoginController`, `RegisterController`                      | `AuthModule`                                                 | `identity-service`: `/api/auth/login`, `/api/auth/register`, `/api/auth/oauth2/google/callback` |
| `MainDashboardController`, `DashboardController`             | `DashboardModule`                                            | `trading-service`: `/api/portfolio/stats`, `/api/trades` (recent) |
| `TradeEntryController`                                       | `LiveTradingModule` + `JournalModule`                        | `trading-service`: `/api/trades`, `/api/trades/{id}/close`; `market-service`: `/api/market/ohlcv/{symbol}` |
| `ChartController`, `IndicatorMixerController`                | `ChartingModule` (uses `ChartLibraryModule`)                 | `market-service`: `/api/charts/drawings`, `/api/charts/layouts`, `/api/indicators/configs`, `/api/indicators/{symbol}/compute` |
| `AlertManagerController`                                     | `AlertsModule` (full CRUD UI — the bell already handles live delivery) | `alert-service`: `/api/alerts`                               |
| `AiNewsController`                                           | `AiInsightsModule`                                           | `ai-service`: `/api/ai/summary/{symbol}`, `/api/ai/signals/{symbol}`, `/api/ai/journal-critique` |
| `DeFiDashboardController`, `NftExplorerController`, `OnchainPoolsController` | `OnChainModule`                                              | `reference-data-service`: `/api/reference/nft/collections`, `/api/reference/defi/pools` |
| `ExportController`, `YearlyProfitController`                 | `ReportsModule`                                              | `trading-service`: `/api/reports/yearly`, `/api/reports/export.xlsx` |
| `ProfileSettingsController`                                  | `SettingsModule`                                             | `identity-service`: `/api/profile`                           |
| `AdminUserManagementController`                              | `AdminModule` (role-gated)                                   | `identity-service`: `/api/admin/users`                       |

---

## Part 3 — Phase 1 (P0): Core Plumbing

Do these before any feature module — everything below depends on them.

### Step 1: HTTP Client, Auth Interceptor, Dependencies

**Goal:** Right now nothing in the app can make an authenticated REST call. Fix that once, globally.

**Prompt:**

> In `frontend/`:
>
> 1. Add to `package.json`: `@angular/material`, `@angular/cdk`, `echarts`, `ngx-echarts` (or a thin wrapper
     >    you write against `echarts` directly if you'd rather not add another dependency — your call, note the
     >    choice).
> 2. In `app.config.ts`, add `provideHttpClient(withInterceptors([authInterceptor]))`.
> 3. Create `core/auth/auth.interceptor.ts` — a functional interceptor that reads the current token from
     >    `AuthSessionService.getToken()` and, if present, attaches `Authorization: Bearer {token}` to every
     >    outgoing request whose URL starts with `environment.gatewayBaseUrl`. Do NOT attach it to other origins.
> 4. Create `core/api/api-paths.ts` — a single file of constants for every Gateway-routed path prefix from
     >    Part 2's table (`/api/auth`, `/api/trades`, `/api/portfolio`, `/api/market`, `/api/charts`,
     >    `/api/indicators`, `/api/alerts`, `/api/ai`, `/api/reference`, `/api/reports`, `/api/profile`,
     >    `/api/admin`), so no module hardcodes a path string.
> 5. Add a global HTTP error interceptor (or extend the auth one) that redirects to `/login` on a 401 from
     >    any Gateway-routed call, clearing `localStorage['mtp_auth_token']` first.
>
> DO NOT build any feature module yet — this step is plumbing only.

**Expected output:** Any future `HttpClient` call to a Gateway path automatically carries the JWT and
handles 401s consistently.

---

### Step 2: Auth Module — Login, Register, Google OAuth2

**Goal:** `AuthSessionService` currently has nothing writing to it. Close that loop.

**Prompt:**

> Build `AuthModule` (standalone components under `frontend/src/app/features/auth/`):
>
> 1. `LoginPageComponent` — email/password form calling `POST {gatewayBaseUrl}/api/auth/login`
     >    (`identity-service`'s `AuthController`), plus a "Sign in with Google" button that redirects to
     >    `{gatewayBaseUrl}/api/auth/oauth2/google/callback`'s initiating endpoint (confirm the exact redirect
     >    entrypoint identity-service's `SecurityConfig` exposes for starting the OAuth2 flow — it's not the
     >    callback URL itself).
> 2. `RegisterPageComponent` — calls `POST /api/auth/register`.
> 3. On successful login/register, write the returned JWT to `localStorage['mtp_auth_token']` (the exact key
     >    `AuthSessionService.TOKEN_STORAGE_KEY` already expects), then call `authSession.refresh()` and
     >    `notificationService.connect()` so the bell picks up the new session immediately without a page reload.
> 4. Add an `authGuard` (functional route guard) using `AuthSessionService.token()` — redirect to `/login`
     >    if absent. Apply it to every route added in later steps except `/login` and `/register`.
> 5. Wire `/login` and `/register` into `app.routes.ts`.
>
> Reuse `AuthSessionService` exactly as it exists today — do not change its public API (`token`,
> `getToken()`, `getCurrentUserId()`, `refresh()`); other code (the notification bell) already depends on it.

**Expected output:** A working login screen; after login, the existing notification bell connects without
any change to its own code.

---

### Step 3: `ChartLibraryModule`

**Goal:** Every trading/charting screen needs this — build it once, standalone, before any feature module
that uses it (per the original guide's Step 12, now actually executed).

**Prompt:**

> Build a tree-shakable `ChartLibraryModule` in `frontend/src/app/shared/chart-library/`:
>
> 1. `CandlestickChartComponent` (standalone) wrapping ECharts — inputs for OHLCV data, theme (dark/light),
     >    and an overlay list of indicator series (SMA/EMA/RSI/MACD/Bollinger/Ichimoku — matching the compute
     >    endpoints `market-service`'s `IndicatorController` from `Migration_Guide_3.md` Step 7 exposes).
> 2. Drawing tools (trend lines, Fibonacci, rectangles) that emit drawing events the host module can persist
     >    via `market-service`'s `/api/charts/drawings` — this component itself does NOT call the API; it emits
     >    `(drawingChanged)` and takes drawings as an `@Input()`, keeping it presentation-only and reusable.
> 3. Zoom/pan/crosshair, and a `livePrice` input that the host feeds from a WebSocket subscription (the
     >    component itself opens no sockets — same separation of concerns as the drawing tools).
> 4. Export it as a single-file-friendly, standalone component with no required inputs (sensible defaults),
     >    per this project's Angular conventions.
>
> DO NOT wire it to any specific feature module or backend call in this step — it's a pure display library,
> consumed by `ChartingModule`, `LiveTradingModule`, and `JournalModule` in later steps.

**Expected output:** A chart you can drop into any future module with mock data and see it render.

---

## Part 4 — Phase 2 (P1): Feature Modules, One at a Time

Build these in the order below — each is independent of the others except where noted, but this order
gets you a usable app fastest (auth → see your money → place a trade → everything else).

### Step 4: `DashboardModule`

**Prompt:**

> Build `DashboardModule` under `features/dashboard/`. Fetch `GET /api/portfolio/stats` and
> `GET /api/trades?limit=20` (`trading-service`). Show KPI cards (total P&L, win rate, trade count), a
> recent-trades table, and an equity curve using `ChartLibraryModule`'s line-chart mode (or a lightweight
> separate line-chart component if candlesticks are overkill here — your call). Add `/dashboard` to
> `app.routes.ts` behind `authGuard`, and make it the default route after login.

**Expected output:** Landing screen after login shows real portfolio numbers.

---

### Step 5: `LiveTradingModule` + `JournalModule`

**Prompt:**

> Build both under `features/live-trading/` and `features/journal/`, sharing a `TradeApiService`
> (`core/api/trade-api.service.ts`) that wraps `/api/trades/**` and `/api/portfolio/**`:
>
> 1. **LiveTradingModule**: broker picker (currently just BitUnix — read available brokers from
     >    `trading-service` if it exposes that, otherwise hardcode BitUnix and note the TODO), live OHLCV via
     >    `ChartLibraryModule` fed from `market-service`'s WebSocket (new STOMP topic — check
     >    `MarketStreamController`'s channel naming in `market-service` and subscribe the same way
     >    `notification.service.ts` does, one `MarketDataSocketService` per this pattern), and an order-entry form
     >    posting to `/api/trades` (spot/futures per BitUnix's supported types).
> 2. **JournalModule**: trade history table/CRUD against the same `TradeApiService`, with filters
     >    (open/closed, date range, symbol) and a close-trade action calling `/trades/{id}/close`.
>
> Both consume `ChartLibraryModule` — do not duplicate chart rendering code in either.

**Expected output:** You can place and close a trade end-to-end from the browser.

---

### Step 6: `ChartingModule`

**Prompt:**

> Build `ChartingModule` under `features/charting/` — the full-screen charting workspace (as opposed to the
> smaller embedded chart in `LiveTradingModule`). Calls `market-service`'s `/api/charts/drawings`,
> `/api/charts/layouts`, `/api/indicators/configs`, `/api/indicators/{symbol}/compute`. Persists drawings on
> `(drawingChanged)` from `ChartLibraryModule`. Lets the user save/load named layouts
> (`GlobalDrawingSettings`/`DrawingLayout` from the backend model).

**Expected output:** Chart drawings and indicator configs persist across sessions, server-side.

---

### Step 7: `AlertsModule`

**Prompt:**

> Build `AlertsModule` under `features/alerts/` — full CRUD UI against `alert-service`'s `/api/alerts`
> (create/edit/delete a `PriceAlert`: symbol, condition, target value). This is distinct from
> `NotificationBellComponent`, which already handles live delivery — this module is alert *management*, not
> delivery. Show alert status (active/fired) and history.

**Expected output:** Users can create alerts from the UI instead of only receiving the ones already seeded.

---

### Step 8: `AiInsightsModule`

**Prompt:**

> Build `AiInsightsModule` under `features/ai-insights/` calling `ai-service`'s `/api/ai/summary/{symbol}`,
> `/api/ai/signals/{symbol}`, `/api/ai/journal-critique`. Show an AI-generated market summary and signal list
> per symbol, and (from the Journal module's context) a "critique this trade" action calling
> `journal-critique`. Handle slow responses with a loading state — LLM calls are not cache-fast like the
> other modules.

**Expected output:** AI tab shows real summaries/signals.

---

### Step 9: `OnChainModule`

**Prompt:**

> Build `OnChainModule` under `features/on-chain/` with two views (NFT collections, DeFi pools), calling
> `reference-data-service`'s `/api/reference/nft/collections` and `/api/reference/defi/pools`.

**Expected output:** NFT/DeFi/on-chain screens have real data.

---

### Step 10: `ReportsModule`

**Prompt:**

> Build `ReportsModule` under `features/reports/` calling `trading-service`'s `/api/reports/yearly` and
> `/api/reports/export.xlsx`. The export endpoint should trigger a browser file download (`responseType:
> 'blob'` on the `HttpClient` call), not open the file in-app.

**Expected output:** Yearly report view + working Excel download.

---

### Step 11: `SettingsModule` + `AdminModule`

**Prompt:**

> Build both under `features/settings/` and `features/admin/`:
>
> 1. **SettingsModule**: profile fields + app settings, calling `identity-service`'s `/api/profile`.
> 2. **AdminModule**: user list + role management, calling `/api/admin/users`. Gate the route itself (not
     >    just hide the nav link) with a role-checking guard reading the JWT's role claim via
     >    `AuthSessionService` (extend it with a `getRoles()` method the same way `getCurrentUserId()` already
     >    decodes claims — don't build a second JWT decoder).

**Expected output:** Settings and admin screens both functional, admin route genuinely inaccessible to
non-admins (not just visually hidden).

---

## Part 5 — Phase 3 (P2): Cross-Cutting Polish

### Step 12: Navigation Shell + Theming

**Prompt:**

> Build a persistent app shell (`core/layout/`) with a nav sidebar/toolbar linking every module from Phase
> 2, mounted in `app.component.html` alongside the existing `NotificationBellComponent`. Add Angular
> Material theming (dark/light toggle, matching the desktop app's existing theme options if it has any —
> check `desktop/.../config/` or FXML CSS for the current palette to match rather than inventing a new one).

**Expected output:** One coherent app shell instead of standalone routed pages.

---

### Step 13: Build, Lint, and Parity Pass

**Prompt:**

> Run a full `ng build --configuration production` and fix any errors. Then do a screen-by-screen parity
> check against the 16 JavaFX controllers listed in Part 2 of this guide — confirm every one has a working
> Angular equivalent reachable from the nav shell. Report any that don't, with the reason (e.g., backend
> endpoint from `Migration_Guide_3.md` not yet implemented).

**Expected output:** A clean production build + an explicit parity checklist, not an assumption that
everything got covered.

---

## Appendix A — Suggested Prompt Order

| #    | Step                             | Depends on             |
| ---- | -------------------------------- | ---------------------- |
| 1    | HTTP client + interceptor + deps | —                      |
| 2    | Auth module                      | Step 1                 |
| 3    | ChartLibraryModule               | — (parallel to Step 2) |
| 4    | Dashboard                        | Steps 1–2              |
| 5    | Live Trading + Journal           | Steps 1–3              |
| 6    | Charting                         | Steps 1–3              |
| 7    | Alerts                           | Steps 1–2              |
| 8    | AI Insights                      | Steps 1–2              |
| 9    | On-Chain                         | Steps 1–2              |
| 10   | Reports                          | Steps 1–2              |
| 11   | Settings + Admin                 | Steps 1–2              |
| 12   | Shell + theming                  | Steps 4–11             |
| 13   | Build/parity pass                | All above              |

## Appendix B — `.cursorrules` Addendum (append, don't replace)

```text
Angular frontend rules (this guide):
- Every HTTP/WebSocket call goes through environment.gatewayBaseUrl — never a microservice's own port.
- JWT lives in localStorage['mtp_auth_token'] (AuthSessionService.TOKEN_STORAGE_KEY). Any code that
  performs login/register MUST write to this exact key and call authSession.refresh() afterward.
- Reuse AuthSessionService's existing public API as-is (token, getToken(), getCurrentUserId(),
  refresh()) — extend it (e.g., getRoles()) rather than building a second JWT decoder.
- STOMP/WebSocket auth uses a connect header (Authorization: Bearer ...), not a URL query param —
  follow notification.service.ts's pattern for any new socket connection.
- ChartLibraryModule is presentation-only: no HTTP/WebSocket calls inside it. Host modules feed it data
  and listen to its output events (e.g., (drawingChanged)).
- Use signal() for reactive component/service state, consistent with the existing codebase — don't
  introduce NgRx or a second state pattern without discussing it first.
```