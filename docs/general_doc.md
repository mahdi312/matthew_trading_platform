# Trading Intelligence Platform – Comprehensive Documentation

This document consolidates all information about the **Trading Intelligence Platform** – a desktop trading journal, market analysis, and alerting application built with JavaFX and Spring Boot. It covers business context, features, architecture, API integrations, technical indicators, data persistence, and future suggestions.

---

## 1. Overview and Business Context

### Purpose
The platform serves as a **personal trading assistant** for retail traders and investors. It allows users to:
- Maintain a **trade journal** with full P&L tracking.
- Analyze markets with **live charts**, **technical indicators**, and **composite BUY/SELL signals**.
- Set **price and indicator alerts** delivered via desktop, email, or Telegram.
- Track **portfolio performance** and export reports to Excel.
- Store all data locally (SQLite) per user profile, ensuring privacy and offline availability.

### User Profiles
- Profiles are named (e.g., *Crypto Portfolio*, *Stocks Journal*, *Forex Trading*) and own all trades, alerts, indicator configurations, and watchlists.
- Switching profiles refreshes all views instantly.
- Default profiles are created on first run; users can add, rename, or delete profiles.

### Asset Classes Supported
The platform handles:
- **CRYPTO** (BTC, ETH, etc.)
- **STOCK** (US equities, ETFs)
- **FOREX** (major and minor pairs)
- **COMMODITY** (gold, oil, etc.)
- **INDEX** (S&P 500, etc.)

---

## 2. Core Features (UI)

The desktop application is divided into views accessible via a sidebar.

### 2.1 Application Shell
- **Header:** App title, profile selector (with +New Profile), alert bell (badge for triggered alerts), and a Settings button.
- **Live Ticker Bar:** Real-time crypto prices via Binance WebSocket; stocks/forex polled every 15 seconds.
- **Sidebar Navigation:** Dashboard, Live Chart, Trade Journal, Analysis, Alerts, Indicator Mixer, Portfolio, Yearly Profit (Fundamentals), Export Excel, and Settings.
- **Status Bar:** Connection/profile status, last update time, version (v1.0.0).

### 2.2 Dashboard (ViewMode: DASHBOARD)
- **KPI Stat Cards:** Total P&L ($ and %), Win Rate, Total/Open Trades, Best/Worst Trade, Profit Factor, Avg Win/Loss.
- **Equity Curve Chart (Canvas):** Filters for 1W, 1M, 3M, ALL.
- **Asset Breakdown:** P&L by CRYPTO/STOCK/FOREX with progress bars.
- **Recent Trades Table:** Last 20 trades (date, symbol, type, direction, entry/exit, qty, P&L, status).
- **Actions:** +New Trade, View All (→ Journal), Edit (✏), Close Trade (✓ with exit price dialog), Delete Trade (🗑 with confirmation).

### 2.3 Portfolio (ViewMode: PORTFOLIO)
- Same as Dashboard but **hides the trades table** – focuses on stats, curve, and breakdown.

### 2.4 Trade Journal (ViewMode: JOURNAL)
- Full trade history (all trades, not limited to 20).
- Hides stat cards, curve, and breakdown.

### 2.5 Trade Entry / Edit
- **Form Fields:** Symbol, Asset Type, Exchange, Direction (Long/Short), Strategy, Entry/Exit Price, Quantity, Stop Loss, Take Profit, Fees, Entry/Exit Date/Time, Notes.
- **Fetch Price:** Live quote via `PriceRouter` auto-fills entry price, asset type, and exchange.
- **Live Preview:** Displays invested amount, P&L ($/%), and Risk:Reward ratio.
- **Validation & Status:** Automatically marks trade as CLOSED if exit price is provided.
- **Supported Asset Types:** CRYPTO, STOCK, FOREX, COMMODITY, INDEX.

### 2.6 Live Chart
- **Symbol:** Preset combo (BTCUSDT, ETHUSDT, SOLUSDT, BNBUSDT, AAPL, TSLA, MSFT, EURUSD, GBPUSD) or custom text input.
- **Timeframes:** 1m, 5m, 15m, 1h, 4h, 1D, 1W (plus 3m, 30m, 2h, 6h, 8h, 12h, 3d, 1mo in recent updates).
- **Bar Count:** 50–1000 (default 200).
- **Chart Overlays:** Toggle EMA, Bollinger Bands, Ichimoku, S/R, Volume, MACD, RSI.
- **Interaction:** Zoom (scroll), Pan (drag), Crosshair with OHLCV tooltip.
- **Actions:** Load, Refresh (force API re-fetch), Analyze.
- **Signal Bar:** Displays BUY/SELL recommendation, confidence %, best buy/sell prices, bull/neutral/bear counts, and live price.

### 2.7 Analysis
- Same screen as Live Chart – opens the chart with the signal bar as the primary analysis output.
- Composite scoring runs automatically on chart load.

### 2.8 Indicator Mixer
- **Presets:** Swing Trading, Scalping, Day Trading, Crypto Momentum, Long Term, Conservative, Custom.
- **Per-Indicator Controls:** Enable/disable toggle, weight slider (0–10), period/threshold spinners.
- **Indicators Configurable:** MACD, RSI, Ichimoku, EMA (incl. golden/death cross periods), Bollinger, Fibonacci, Stochastic, VWAP, CCI.
- **Save / Reset** per profile.
- **Live Preview Panel:** Signal label, confidence progress bar, best buy/sell, per-indicator breakdown.

### 2.9 Alert Manager
- **Create Alert:** Symbol, Type, Target (price or %), Notification Channels (Desktop, Email, Telegram), Repeating flag, Custom message.
- **Alert Types:** PRICE_ABOVE, PRICE_BELOW, PCT_CHANGE_24H, INDICATOR_BUY_SIGNAL, INDICATOR_SELL_SIGNAL, FIBONACCI_LEVEL_TOUCH, VOLUME_SPIKE.
- **Alerts Table:** Symbol, type, target, status, notify icons, triggered time.
- **Row Actions:** Toggle active/pause (⏸), Delete (🗑).
- **Background Polling:** Every 10 seconds for price-based alerts.

### 2.10 Export Excel
- Exports profile data to `.xlsx` with a timestamped default filename.
- **Sheets:**
    1. **Trade Log** – full list, color-coded P&L, auto-filter.
    2. **Summary** – portfolio KPI dashboard.
    3. **Asset Breakdown** – grouped stats with embedded bar chart.
    4. **Equity Curve** – cumulative P&L table with embedded line chart.
    5. *(Optional)* **Fundamentals** – last loaded fundamentals snapshot.

### 2.11 Yearly Profit (Fundamentals)
- Displays yearly revenue, profit, EBITDA for stocks using Alpha Vantage / Finnhub.
- Not available for crypto/forex (explicit message shown).

### 2.12 Settings
- **Profile Settings View:** Asset focus (crypto/stocks/forex/multi), default symbol, preferred chart and fundamentals providers, and a customizable ticker watchlist.

---

## 3. Technical Architecture

### 3.1 Stack
- **Java 21**, **JavaFX 21**, **Spring Boot 3.3** (without embedded web server – desktop-only).
- **Persistence:** Default SQLite (local), optional PostgreSQL via Docker profile.
- **Libraries:** ta4j (technical analysis), Apache POI (Excel export), OkHttp (HTTP/WebSocket), Hibernate/JPA.
- **Build:** Maven, with jpackage for native installers.

### 3.2 Core Services
| Service | Responsibility |
|---------|----------------|
| **TradeService** | CRUD operations, portfolio statistics, P&L calculations. |
| **PriceRouter** | Routes symbols to correct market data provider based on asset class and user preference. |
| **OhlcvStorageService** | Manages OHLCV data fetching, caching (SQLite or PostgreSQL), and refresh logic. |
| **AnalysisService** | Orchestrates the full analysis pipeline: fetch data → compute indicators → S/R levels → signal scoring. |
| **IndicatorService** | Computes technical indicators using ta4j. |
| **SignalScoringService** | Applies profile weights to generate composite BUY/SELL score (confidence ≥60% triggers alerts). |
| **AlertService** | Polls and fires alerts; uses NotificationService for delivery. |
| **NotificationService** | Sends alerts via desktop tray, SMTP (email), and Telegram bot. |
| **ExcelExportService** | Generates multi-sheet Excel reports. |
| **FundamentalRouter** | Routes fundamentals requests to Alpha Vantage / Finnhub. |
| **AppStartupService** | Starts WebSocket streams and background jobs on boot. |

### 3.3 Controllers (UI)
| Controller | FXML | Primary Function |
|------------|------|------------------|
| `MainDashboardController` | MainDashboard.fxml | Shell, navigation, profiles, ticker. |
| `DashboardController` | DashboardView.fxml | Dashboard / Journal / Portfolio. |
| `TradeEntryController` | TradeEntry.fxml | Create/edit trades. |
| `ChartController` | ChartView.fxml | Live chart + analysis signal bar. |
| `IndicatorMixerController` | IndicatorMixerView.fxml | Indicator weight tuning. |
| `AlertManagerController` | AlertManagerView.fxml | Price & signal alerts. |
| `ExportController` | ExportView.fxml | Excel export generation. |
| `ProfileSettingsController` | ProfileSettingsView.fxml | Settings per profile. |

### 3.4 Data Models
- **UserProfile** – owns trades, alerts, indicator configs.
- **Trade** – journal entry with auto P&L.
- **PriceAlert** – configurable alerts.
- **IndicatorConfig** – per-profile weights and parameters.
- **OhlcvBar** – cached candlestick data.
- Enums: `AssetType`, `TradeDirection`, `TradeStatus`, `AlertType`, etc.
- Indicator presets: SWING_TRADING, SCALPING, DAY_TRADING, CRYPTO_MOMENTUM, LONG_TERM, CONSERVATIVE, CUSTOM.

### 3.5 Persistence Repositories
- `UserProfileRepository`, `TradeRepository`, `PriceAlertRepository`, `IndicatorConfigRepository`, `OhlcvBarRepository`.
- `DataSourceConfig` configures SQLite; the `docker` profile switches to PostgreSQL.

---

## 4. Market Data Providers

### 4.1 Price & OHLCV Providers
The `PriceRouter` uses a fallback chain per asset class, with the user’s preferred provider prepended. All providers are integrated via `PriceService` implementations. Rate limiting is applied for free tiers (Alpha Vantage: 5 req/min, Finnhub: 60 req/min) using token-bucket throttling.

| Provider | Asset Classes | Key Property | Free Tier | Notes |
|----------|---------------|--------------|-----------|-------|
| **Binance** | Crypto | (public) | Unlimited | Primary crypto source; WebSocket for live ticker. |
| **CoinGecko** | Crypto | `api.coingecko-key` | 30 req/min | Broader symbol coverage. |
| **Yahoo Finance** | Stock, Forex, Commodity, Index | (none) | Polite use | Primary stock source. Symbol mapping: `EURUSD=X`, `GC=F`, `^GSPC`. |
| **Frankfurter** | Forex | (none) | Unlimited | Daily ECB rates. |
| **Alpha Vantage** | Stock, Forex, Crypto | `api.alphavantage-key` | 5 req/min | Provides fundamentals too. |
| **Finnhub** | Stock, Forex, Crypto | `api.finnhub-key` | 60 req/min | Provides fundamentals. |
| **Polygon.io** | Stock | `api.polygon-key` | 5 req/min | Premium feed. |
| **Twelve Data** | Stock, Forex, Crypto | `api.twelvedata-key` | 8 req/min | Cross-asset fallback. |
| **Marketstack** | Stock | `api.marketstack-key` | 100 req/month | End-of-day quotes. |
| **Fixer.io** | Forex | `api.fixerio-key` | 100 req/month | Spot quotes only. |
| **Open Exchange Rates** | Forex | `api.openexchangerates-key` | 1000 req/month | Spot quotes only. |
| **ExchangeRate-API** | Forex | `api.exchangerateapi-key` | 1500 req/month | Spot quotes only. |
| **FreeCurrencyAPI** | Forex | `api.freecurrencyapi-key` | 5000 req/month | Spot quotes only. |
| **CurrencyLayer** | Forex | `api.currencylayer-key` | 100 req/month | Spot quotes only. |

### 4.2 Fundamentals Providers
- **Alpha Vantage**: US equities (OVERVIEW, INCOME_STATEMENT, EARNINGS).
- **Finnhub**: Global equities (/stock/metric, /stock/profile2, /financials-reported).  
  Provider order: profile preference → Alpha Vantage → Finnhub.

### 4.3 Fallback Chains (Default)
| Asset Class | Order |
|-------------|-------|
| CRYPTO | Binance → CoinGecko → Twelve Data → Alpha Vantage → Finnhub → Yahoo |
| FOREX | Frankfurter → Fixer → FreeCurrencyAPI → Open Exchange Rates → ExchangeRate-API → CurrencyLayer → Twelve Data → Alpha Vantage → Finnhub → Yahoo |
| STOCK | Yahoo → Finnhub → Polygon → Alpha Vantage → Twelve Data → Marketstack → Binance → CoinGecko |
| COMMODITY | Yahoo (with `=F`) → Twelve Data → Alpha Vantage → Finnhub |
| INDEX | Yahoo (with `^`) → Twelve Data → Alpha Vantage → Finnhub |

### 4.4 Dynamic Market Data Tables (PostgreSQL)
When `app.market-data.dynamic-tables.enabled=true` (automatic with the Docker profile), OHLCV data is stored in dedicated tables per symbol, provider, and timeframe, e.g., `ETHUSDT_BINANCE_1h`. Reference tables (`markets`, `shares`, `companies`, `market_data_table_registry`) hold metadata. The scheduler syncs data on a timeframe-based schedule (stale-while-revalidate).

---

## 5. Technical Analysis Indicators

All indicators are powered by **ta4j 0.22.1**. They are computed on-the-fly from OHLCV bars; no precomputed storage is used. Each indicator is defined by an `IndicatorDefinition` with period(s), color, price source, and visibility.

### 5.1 Moving Averages (Price Pane Overlay)
- EMA, SMA, WMA, DEMA, TEMA, Hull MA, KAMA, ZLEMA, VWAP.

### 5.2 Bands & Channels (Price Pane Overlay)
- Bollinger Bands, Keltner Channel, Donchian Channel, Parabolic SAR.

### 5.3 Momentum Oscillators (Sub Pane)
- RSI, MACD (with histogram and signal line), Stochastic, StochRSI, CCI, Williams %R, ROC, DPO, Aroon (oscillator + up/down), CMO, Fisher Transform, PPO.

### 5.4 Volatility (Sub Pane)
- ATR, Ulcer Index.

### 5.5 Volume (Sub Pane)
- OBV, MFI, CMF, Chaikin Oscillator.

### 5.6 Trend Strength (Sub Pane)
- ADX (with +DI and -DI).

### 5.7 Complex Overlays (Price Pane)
- Ichimoku Cloud (Tenkan, Kijun, Senkou Span A/B, Chikou).
- Support & Resistance (Fibonacci and pivot points via custom service).

### 5.8 Indicator Mixer and Signals
- Users can enable/disable, adjust weights (0–10), and modify periods for each indicator.
- Composite scoring produces signals: STRONG_BUY, BUY, NEUTRAL, SELL, STRONG_SELL.
- A confidence ≥60% triggers indicator alerts.

---

## 6. Data Persistence

### 6.1 Default: SQLite
- Database file: `~/.trading-platform/trading.db`.
- Single-user, local. All data scoped to profiles.

### 6.2 Optional: PostgreSQL (via Docker)
- PostgreSQL 16 container.
- Dynamic OHLCV tables for each symbol/provider/timeframe.
- Enabled by setting `SPRING_PROFILES_ACTIVE=docker`.
- Volume `trading_pgdata` persists data.
- Environment variables: `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`.

### 6.3 Schema
- **Reference tables:** `user_profiles`, `trades`, `price_alerts`, `indicator_configs`, `markets`, `shares`, `companies`, `market_data_table_registry`.
- **Dynamic OHLCV tables:** `{SYMBOL}_{PROVIDER}_{TIMEFRAME}` with columns: `id`, `open_time`, `open_price`, `high_price`, `low_price`, `close_price`, `volume`, `asset_type`, `updated_at`.

---

## 7. Configuration and Deployment

### 7.1 Secrets and API Keys
- Place sensitive keys in `application-local.properties` (gitignored).
- Example file provided: `application.properties.example`.
- Keys can also be set as environment variables.
- The main `application.properties` imports the local file.

### 7.2 Running the Application
- **Development:** `mvn javafx:run` or `mvn spring-boot:run`.
- **Building:** `mvn package` produces a JAR; `jpackage` creates native installers.
- **Docker:** `docker compose up -d postgres` for the database only; run the app on host with `SPRING_PROFILES_ACTIVE=docker`.
- **Full container:** `docker compose up --build` (headless, not for desktop UI).

### 7.3 Background Jobs
- Alert polling: every 10 seconds.
- Analysis refresh for watchlist symbols: every 60 seconds.
- Stock/forex ticker poll: every 15 seconds.
- Market data sync (PostgreSQL): every 30 seconds for due tables.

### 7.4 Notifications
- **Desktop:** Uses `SystemTray` (default on).
- **Email:** SMTP configured via `spring.mail.*` and `notification.email.to`.
- **Telegram:** Enabled with `telegram.bot.enabled=true`, token, and chat-ids.

---

## 8. Future Suggestions – Server-Client Architecture

### 8.1 Concept
Currently, the platform is a **single-user desktop application**. To extend it for a team or for accessing the same data from multiple devices, a **server-backed** architecture is proposed:

- **Central Server:** Runs the Spring Boot backend with a **PostgreSQL** database (same schema as current Docker mode). It exposes REST or WebSocket APIs for market data, trades, alerts, and analysis.
- **Desktop Clients:** The existing JavaFX application can be modified to connect to this server **instead of** using local SQLite. However, to support **offline usage**, each client can also maintain a **local SQLite cache** that syncs when the network is available.

### 8.2 Benefits
- **Centralized Data:** All users share the same market data cache and can see each other’s trades (if permissions allow).
- **Redundant API Calls:** The server can poll market data once and distribute it to all clients, reducing rate-limit pressure.
- **Remote Access:** Users can run the desktop client from anywhere with an internet connection.
- **Offline Fallback:** Clients can operate on a local copy of their personal trades and recent market data, syncing changes when back online.

### 8.3 Implementation Outline
1. **Extract Backend Services:** Move all `@Service` classes (TradeService, PriceRouter, OhlcvStorageService, AlertService, etc.) into a separate Spring Boot module that can be deployed as a REST API.
2. **Add Controllers:** Expose endpoints for all operations (CRUD on trades/alerts/profiles, chart data requests, analysis, export).
3. **Client-Side Adapter:** Replace direct service calls in JavaFX controllers with HTTP clients (e.g., `RestTemplate` or `WebClient`). Use a fallback to local SQLite when offline.
4. **Authentication:** Add simple user authentication (e.g., JWT) to isolate data per user.
5. **Sync Strategy:** For offline clients, implement a conflict-resolution mechanism (e.g., timestamp-based last-write-wins).
6. **Deployment:** Package the server as a Docker container with PostgreSQL; clients remain as installable desktop apps.

### 8.4 Additional Enhancements
- **Web Dashboard:** Build a lightweight web frontend (e.g., React/Vue) using the same REST APIs.
- **Mobile App:** Use the same APIs for a mobile companion app.
- **Alerts as Push Notifications:** Extend the server to send push notifications via Firebase/APNS for mobile.
- **Multi-user Collaboration:** Allow sharing of profiles or watchlists among team members.

### 8.5 Migration Path
- The current codebase already supports both SQLite and PostgreSQL via Spring profiles.
- The server module can reuse the existing `docker` profile configuration.
- Clients can continue to use SQLite for offline caching; the sync logic can be added incrementally.

This evolution would transform the platform from a personal tool into a collaborative trading intelligence system while preserving offline functionality for individual users.


### 8.6 AI-Powered News & Investment Suggestions

This feature would allow users to search for **real-time financial news** and receive **AI-generated investment insights** directly within the platform. It combines external news APIs with large language models (LLMs) to deliver actionable intelligence.

#### Proposed User Workflow
1. **Search Bar** – A dedicated input field (e.g., in a new “News & Insights” panel or sidebar) where the user types a query, such as:
    - *“Latest news on NVIDIA”*
    - *“AI sector investment ideas”*
    - *“Earnings report for Tesla”*
2. **Retrieval** – The system queries one or more news APIs (e.g., NewsAPI, Alpha Vantage News, Finnhub News) to fetch relevant articles, headlines, and press releases.
3. **AI Processing** – The retrieved news is sent to an LLM (via API) for:
    - **Summarization** – Short, punchy bullet points or a concise paragraph.
    - **Sentiment Analysis** – Bullish, neutral, or bearish scoring for each article or overall.
    - **Key Entities** – Extraction of companies, sectors, and financial metrics mentioned.
4. **Investment Suggestion** – Based on the user’s query and the processed news, the AI can generate:
    - **Potential trade ideas** (e.g., “NVIDIA appears oversold after earnings; consider a swing long”).
    - **Risk warnings** (e.g., “Regulatory news may pressure crypto stocks”).
    - **Sector trends** – “AI infrastructure stocks are gaining momentum.”
5. **Display** – Results appear in a clean, card-based UI with:
    - **News headlines** with source and timestamp.
    - **Sentiment badges** (🔴 Bearish / 🟡 Neutral / 🟢 Bullish).
    - **AI-generated summary** and **suggestion** in plain language.
    - Option to **save** interesting ideas or create alerts based on them.

#### API Integration Options
| Service | Purpose | API Key Required | Rate Limits |
|---------|---------|------------------|-------------|
| **Finnhub News** | Real-time US/global company news | Yes (free tier: 60 calls/min) | Included |
| **Alpha Vantage News & Sentiment** | Latest headlines and sentiment scores | Yes (5 req/min) | Included |
| **NewsAPI** | Broad news coverage, custom keywords | Yes (free: 100 req/day) | Included |
| **OpenAI / Anthropic** | LLM for summarization & suggestion generation | Yes (pay-as-you-go) | Varies |

#### Implementation Outline
1. **New Service** – `NewsService` that aggregates results from multiple providers and normalises them.
2. **AI Service** – `InsightGeneratorService` that calls the LLM API with a structured prompt (including the user query and retrieved news). The prompt could be:
   > “Given the following news articles about [query], provide a brief summary, overall sentiment, and 2–3 actionable investment ideas or warnings. Be concise.”
3. **Caching** – Store processed results in the local database for quick reuse (with a TTL, e.g., 15 minutes).
4. **UI** – A new view (e.g., `InsightsView.fxml`) accessible from the sidebar, with a search bar, result cards, and a history of past queries.
5. **Alerts Integration** – Allow users to create alerts that trigger when the AI detects a strong sentiment shift for a watched symbol (e.g., “Alert me if sentiment for TSLA drops below -0.5”).

#### Technical Considerations
- **Cost Management** – LLM API calls can be expensive; implement:
    - **Smart caching** – Cache results for the same query for a short period.
    - **Summarisation threshold** – Only call the AI if at least 3 news items are retrieved.
    - **User configurable** – Allow users to enable/disable AI features or choose a preferred LLM provider.
- **Rate Limiting** – Apply token-bucket throttling (similar to T-23) for news APIs to avoid hitting free-tier caps.
- **Offline Mode** – When offline, show cached results and a message indicating “AI insights unavailable until network is restored.”

#### Business Value
- **Empowered Decision-Making** – Users save time by getting curated news and investment ideas without switching between multiple websites.
- **Contextual Alerts** – Combine AI sentiment with price action to create more nuanced alerts (e.g., “BUY signal AND positive news sentiment”).
- **Competitive Edge** – Early access to breaking news and AI-generated insights can give traders an edge over manual research.

#### Future Extensions
- **Personalised Feed** – Learn from user’s portfolio and watchlist to automatically surface relevant news and suggestions.
- **Voice Interface** – Allow voice queries for hands-free research.
- **Social Sentiment** – Integrate Twitter/X or Reddit sentiment (via APIs) for a broader market mood.


### 8.7 Advanced Chart Drawing Tools (TradingView-style Annotations)

To transform the charting experience from a passive viewer into a fully interactive analysis workspace, the platform can implement a comprehensive drawing toolbar. This allows users to mark patterns, plan entries/exits, and visualize risk directly on the `CandlestickChartCanvas`.

#### Proposed User Workflow
1. **Toolbar** – A floating palette appears on the left or top of the chart, containing draggable icons for each drawing tool.
2. **Selection** – The user picks a tool (e.g., "Trend Line"), then clicks and drags directly on the chart to place the object.
3. **Interactivity** –
    - Anchors (nodes) appear on the object for dragging to adjust length, angle, or price levels.
    - A right-click context menu offers *Delete*, *Duplicate*, or *Lock* (prevent accidental shifts).
4. **Persistence** – All drawings are saved to the active profile's database (SQLite/PostgreSQL) in a `chart_drawings` table. When the user reloads the same symbol/timeframe, the drawings reappear exactly where they were placed.
5. **Snapping** – Anchors optionally snap to the nearest OHLC high/low/open/close for precise precision, holding `Shift` toggles snap mode.

---

#### Complete Tool List (Implementation Roadmap)

**1. Line & Trend Tools** (Foundation)
- **Trend Line** – Standard straight line connecting two points. Extends infinitely or limited.
- **Ray** – Line with an endpoint on the left and extending infinitely to the right.
- **Extended Line** – Infinite line extending in both directions.
- **Horizontal Line** – Fixed price level across the entire visible time range.
- **Vertical Line** – Fixed time marker across the entire price range.
- **Parallel Channel** – Two parallel trend lines (upper and lower boundaries). User draws the centerline; the parallel copies are auto-generated with an adjustable width.
- **Flat Channel** – Rectangle with horizontal supports/resistances.

**2. Fibonacci Suite** (Comprehensive)
- **Fib Retracement** – Draws the 0–100% range with automatically calculated levels: 23.6%, 38.2%, 50%, 61.8%, 78.6%, and 100% (with optional extension levels like 127.2%, 161.8%).
- **Fib Extension** – Projects potential price targets (1.272, 1.618, 2.618) based on three selected swing points.
- **Fib Channel** – Applies Fibonacci ratios to parallel channel boundaries, creating sloping support/resistance zones.
- **Fib Time Zones** – Vertical bands based on Fibonacci sequence (1, 2, 3, 5, 8, 13...) projected from a starting pivot point for time-based reversal prediction.
- **Fib Speed Resistance** – Angled support/resistance lines based on price/time speed (1x, 2x, 3x slopes).
- **Fib Fan** – Radial lines emanating from a pivot point at Fibonacci angles.

**3. Position & Risk Management Tools**
- **Long Position** – Visual template with three draggable horizontal lines: **Entry** (green), **Stop Loss** (red), and **Take Profit** (blue). Automatically calculates and displays:
    - Risk/Reward ratio (R:R).
    - Position size preview (based on account risk %).
- **Short Position** – Same as long, but with red entry and green target (inverse logic).
- **Risk/Reward Label** – A floating annotation that displays the calculated risk/reward ratio for any highlighted trade setup.
- **Profit Target / Stop Loss** – Standalone horizontal lines with editable price labels, often used alongside existing open positions.

##### Integration with Trade Journal

Each **Long Position** or **Short Position** drawing acts as a **live trade draft**, bridging visual planning and the trade journal.

###### Two‑Step Workflow (Full Control)

1. **Trigger the transfer** – The user can either:
    - **Right‑click** on the position shape and select **“Create Trade from Drawing”** from the context menu, **or**
    - **Hover** over the shape; a small **“+ Trade”** icon appears near the anchor – clicking it initiates the same action.

2. **Pre‑filled Trade Entry form** – The `TradeEntry` dialog (see section **2.4**) opens with the following fields automatically populated from the drawing:

   | Field          | Source                               |
      |----------------|--------------------------------------|
   | **Symbol**     | Current chart symbol                 |
   | **Direction**  | `LONG` or `SHORT` (as drawn)         |
   | **Entry Price**| The drawn entry line level           |
   | **Stop Loss**  | The drawn SL line level              |
   | **Take Profit**| The drawn TP line level              |
   | **Risk:Reward**| Computed and shown in the preview    |

3. **User adjustments** – The trader can now:
    - Modify any field (e.g., adjust entry, add quantity, change strategy, write notes).
    - Use the live preview to see the updated P&L and R:R.
    - **Save** the trade – it is persisted to the active profile’s journal.

###### One‑Click Workflow (Quick Logging)

For fast, on‑the‑fly logging, the platform offers an **“Instant Save”** feature:

- An **“Instant Save”** button (or a keyboard shortcut, e.g., `Ctrl+Shift+T`) is available directly on the position shape.
- Clicking it **saves the trade immediately** with sensible defaults:
    - Quantity = `1` (or a user‑configured default)
    - Status = `OPEN`
    - No exit date/price (the trade is considered open)
    - Strategy/notes are left blank (or can be filled later via edit).

This bypasses the full entry form, allowing traders to record a planned trade in under two seconds – ideal during fast‑moving markets.

---

###### Implementation Notes

- **Data Transfer:** The drawing object stores entry, stop, and target prices as properties; when the action is triggered, these values are passed directly to the `TradeEntryController` via the `TradeEntryController.initFromDrawing(...)` method.
- **Validation:** If the drawing lacks a stop loss or take profit, the form still opens but those fields remain empty (the user can enter them manually).
- **Persistence:** Once saved, the trade is stored in the `trades` table and becomes part of the portfolio statistics, exactly like any manually entered trade.
- **Editing:** If the drawing is later modified, the already‑created trade is **not** automatically updated – the user must edit the trade manually (using the existing Edit action in the journal) to keep it in sync.

---

###### Benefits

- **Saves time** – No need to copy numbers from the chart into a separate form.
- **Reduces errors** – Prices come directly from the drawing coordinates.
- **Encourages discipline** – Forces the user to define entry, SL, and TP before the trade is placed.
- **Flexibility** – Choose between full‑control (review all fields) or one‑click speed.


**4. Geometric Shapes & Patterns**
- **Rectangle** – Draws a box (support/resistance zone) with optional fill opacity.
- **Triangle / Wedge** – Ascending, descending, or symmetrical pattern drawing.
- **Ellipse / Circle** – Useful for cyclic patterns and round-number zones.
- **Andrew's Pitchfork** – Median line with two parallel channels extending from three pivot points. (Includes standard, modified, and Schiff forks).
- **Gann Fan** – Angled lines based on geometric angles (1x1, 1x2, 2x1, etc.) used in W.D. Gann theory.

**5. Annotations & Measurement**
- **Text Label** – Freeform text box with adjustable font, size, and background color (e.g., "Breakout here!").
- **Callout** – Text box with an attached leader line pointing to a specific price/time coordinate.
- **Arrow** – Upward/Downward/Left/Right arrowheads for marking trends or specific candles.
- **Ruler / Price Measure** – Displays the price difference (points/pips/%) and time duration between two clicked points.
- **Note Icon** – A small sticky-note icon that expands on hover to show a longer description.

**6. Projection & Copy Tools**
- **Parallel Lines** – Duplicates a selected line at a user-defined offset distance.
- **Mirror Tool** – Flips a selected drawing horizontally (time axis) or vertically (price axis).

---

#### Implementation Considerations

- **Rendering Engine**: Extend `CandlestickChartCanvas` with a dedicated overlay layer for drawings. All coordinates are stored in **"absolute price-time units"** (e.g., timestamp in milliseconds + double price) to survive timeframe switches and zoom/pan adjustments.
- **Data Storage**: Add a new entity `ChartDrawing` with fields: `id`, `profileId`, `symbol`, `timeframe`, `toolType` (enum), `points` (JSON array of {time, price}), `properties` (JSON for color, line width, opacity, fill), `creationDate`, and `locked` (boolean).
- **Repository**: `ChartDrawingRepository` with queries to load drawings matching the current `symbol` and `timeframe` filter.
- **Interaction Layer**: Use JavaFX `MouseEvent` handlers on the canvas (press → drag → release) to create objects; for editing, detect clicks within a threshold (e.g., 5 pixels) of a drawing line or anchor.
- **Context Menu**: Integrate JavaFX `ContextMenu` (right-click) for deleting, duplicating, locking, and changing colors (via a color picker popup).
- **Performance**: For charts with many drawings (200+), use batch rendering to a separate `Canvas` buffer that is translated/scaled alongside the main price canvas, avoiding redraw flicker.

#### Business Value
- **Eliminates External Dependencies** – Users no longer need to screenshot charts and paste into paint apps or third-party pattern tools.
- **Reinforces Trading Discipline** – The Long/Short Position tools enforce pre-defined exit points, making the platform a complete **trade planning cockpit**.
- **Collaborative Possibility** – In a future server-backed deployment (see 8.2), drawings could be shared between team members for collective analysis.

#### Future Extensions
- **Indicator Auto-Overlay** – Automatically add Fibonacci levels drawn from the last detected swing high/low (by `SupportResistanceService`).
- **Drawing Templates** – Save a set of drawings (e.g., "My BTC Breakout Layout") for one-click reapplication.
- **Shareable Links** – Export drawings as a lightweight JSON snippet to share with other platform users.
---

*This enhancement would transform the platform from a passive journal/charting tool into an active intelligence assistant, aligning with modern AI-driven trading workflows.*
---

*This document consolidates all information from the project’s documentation files and serves as a single source of truth for the Trading Intelligence Platform.*