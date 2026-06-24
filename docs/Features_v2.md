Here is a restructured and refined version of the feature outline, organized for clarity and readability while retaining every technical detail from the original.

---

# Trading Intelligence Platform: User-Facing Feature Outline

## Overview

A desktop application built with JavaFX and Spring Boot. It uses a local SQLite database located at `~/.trading-platform/trading.db`. All user data, including trades, alerts, and indicator settings, is scoped to the active user profile.

---

## 1. Application Shell

The main window serves as the central hub for navigation and status information.

- **FXML:** `MainDashboard.fxml`
- **Controller:** `MainDashboardController.java`
- **Theme CSS:** `dark-theme.css`

### Global Features

- **Header:**
    - App title
    - Profile selector
    - **“+ New Profile”** button
    - Alert bell with a badge showing the count of triggered alerts
    - Settings button
- **Live Ticker Bar:** Displays real-time prices.
    - **Crypto:** Binance WebSocket
    - **Stocks/Forex:** Polled every 15 seconds
- **Sidebar Navigation:** Provides access to all core views: Dashboard, Live Chart, Trade Journal, Analysis, Alerts, Indicator Mixer, Portfolio, Yearly Profit (Fundamentals), Export Excel, and Settings.
- **Status Bar:** Shows connection status, active profile, last update time, and application version (v1.0.0).
- **Profile Management:**
    - Profile switching creates a default indicator configuration per profile and refreshes all open views.
    - Default profiles on first run: **Crypto Portfolio**, **Stocks Journal**, **Forex Trading**.
- **Settings (T-17):** Opens the `ProfileSettingsView`. This allows configuration of asset focus, default symbols, chart/fundamental providers, and a customizable ticker watchlist (T-12). (The placeholder from legacy versions has been removed).

---

## 2. Core Views & Features

### 2.1 Dashboard
Displays a comprehensive overview of trading performance.

- **FXML:** `DashboardView.fxml`
- **Controller:** `DashboardController.java`
- **Service:** `TradeService.java`
- **View Mode:** `DASHBOARD`

#### Features

- **KPI Stat Cards:** Total P&L ($ and %), Win Rate, Total/Open Trades, Best/Worst Trade, Profit Factor, Avg Win/Loss.
- **Equity Curve Chart (Canvas):** Filters available for 1W, 1M, 3M, and ALL timeframes.
- **Asset Breakdown:** Visualizes P&L by asset type (CRYPTO / STOCK / FOREX) using progress bars.
- **Recent Trades Table:** Displays the last 20 trades with columns for date, symbol, type, direction, entry/exit, qty, P&L, and status.
- **Actions:**
    - **+ New Trade**
    - **View All** (Navigates to Journal)
    - **Edit (✏)**
    - **Close Trade (✓):** Prompts for an exit price dialog.
    - **Delete Trade (🗑):** Performs deletion with a confirmation prompt (T-11).

### 2.2 Portfolio
A focused view for portfolio-level analytics.

- **View Mode:** `PORTFOLIO`
- **Features:** Shows KPI stat cards, equity curve, and asset breakdown.
- **Hides:** The trades table.

### 2.3 Trade Journal
A dedicated view for reviewing the complete trade history.

- **View Mode:** `JOURNAL`
- **Features:** Displays a full list of all trades in the history.
- **Hides:** KPI stat cards, equity curve, and asset breakdown sections.

### 2.4 Trade Entry / Edit
A comprehensive form for creating and editing trades.

- **FXML:** `TradeEntry.fxml`
- **Controller:** `TradeEntryController.java`
- **Services:** `TradeService`, `PriceRouter`

#### Features

- **Form Fields:** Symbol, Asset Type, Exchange, Long/Short, Strategy.
- **Pricing:** Entry/Exit Price, Quantity, Stop Loss, Take Profit, Fees.
- **Dates:** Entry Date/Time with an optional Exit Date.
- **Notes:** A free-text field for trade notes.
- **Fetch Price:** Retrieves a live quote via `PriceRouter`, auto-filling the entry price, asset type, and exchange.
- **Live Preview:** Calculates and displays invested amount, P&L ($/%), and Risk:Reward ratio in real-time.
- **Validation:** Validates all required fields before saving.
- **Status Logic:** Automatically sets the trade status (OPEN/CLOSED) based on the presence of an exit price.
- **Modes:** Supports "New Trade Entry" and "Edit Trade" (populated from the dashboard edit action).
- **Supported Asset Types:** CRYPTO, STOCK, FOREX, COMMODITY, INDEX.

### 2.5 Live Chart
Interactive charting for technical analysis.

- **FXML:** `ChartView.fxml`
- **Controller:** `ChartController.java`
- **Chart UI:** `CandlestickChartCanvas.java`
- **Services:** `AnalysisService`, `OhlcvStorageService`, `PriceRouter`

#### Features

- **Symbol Selector:** Preset combo (BTCUSDT, ETHUSDT, SOLUSDT, BNBUSDT, AAPL, TSLA, MSFT, EURUSD, GBPUSD) or a custom text input.
- **Timeframes:** 1m, 5m, 15m, 1h, 4h, 1D, 1W.
- **Bar Count:** Configurable from 50 to 1000 (default 200).
- **Chart Overlays:** Toggle indicators including EMA, Bollinger Bands, Ichimoku, S/R, Volume, MACD, and RSI.
- **Chart Interaction:** Zoom (scroll), Pan (drag), and Crosshair with OHLCV tooltip.
- **Actions:** Load, Refresh (force API re-fetch), and Analyze.
- **Signal Bar:** Displays a BUY/SELL recommendation with confidence %, best buy/sell prices, bull/neutral/bear counts, and the live price.

### 2.6 Analysis
This view provides an in-depth look at the composite scoring engine.

- **Context:** Opens the same screen as the Live Chart (`ChartView`), but presents the signal bar as the primary output.
- **Function:** Composite scoring runs automatically on chart load.

### 2.7 Indicator Mixer
Centralized configuration for indicator weights and parameters.

- **FXML:** `IndicatorMixerView.fxml`
- **Controller:** `IndicatorMixerController.java`
- **Services:** `IndicatorService`, `SignalScoringService`, `SupportResistanceService`, `OhlcvStorageService`
- **Repository:** `IndicatorConfigRepository.java`

#### Features

- **Presets:** Pre-configured sets for Swing Trading, Scalping, Day Trading, Crypto Momentum, Long Term, Conservative, and Custom.
- **Per-Indicator Controls:** Enable/disable toggles, weight sliders (0–10), and period/threshold spinners.
- **Configurable Indicators:** MACD, RSI, Ichimoku, EMA (including golden/death cross periods), Bollinger, Fibonacci, Stochastic, VWAP, and CCI.
- **Persistence:** Save Config / Reset (per profile).
- **Live Preview:** Displays a signal label, confidence progress bar, best buy/sell prices, and a per-indicator signal breakdown (when chart data is available).

### 2.8 Alert Manager
Create and manage price and indicator-based alerts.

- **FXML:** `AlertManagerView.fxml`
- **Controller:** `AlertManagerController.java`
- **Service:** `AlertService.java`

#### Features

- **Create Alert:** Configure symbol, type, target price/%, notification channels, repeating flag, and a custom message.
- **Alert Types:** PRICE_ABOVE, PRICE_BELOW, PCT_CHANGE_24H, INDICATOR_BUY_SIGNAL, INDICATOR_SELL_SIGNAL, FIBONACCI_LEVEL_TOUCH, VOLUME_SPIKE.
- **Notification Channels:** Email, Telegram, Desktop (default on).
- **Alerts Table:** Lists symbol, type, target, status, notify icons, and triggered time.
- **Row Actions:** Toggle active/pause (⏸) and Delete (🗑).
- **Background Polling:** Evaluates price-based alerts every 10 seconds.

### 2.9 Export Excel
Export profile data to a structured Excel file.

- **FXML:** `ExportView.fxml`
- **Controller:** `ExportController.java`
- **Service:** `ExcelExportService.java`

#### Features

- **File Output:** Exports to `.xlsx` via a file chooser with a profile-scoped, timestamped default filename.
- **Sheets:**
    1.  **Trade Log:** Full trade list with color-coded P&L and auto-filter.
    2.  **Summary:** Portfolio KPI dashboard.
    3.  **Asset Breakdown:** Grouped stats with an embedded bar chart.
    4.  **Equity Curve:** Cumulative P&L table with an embedded line chart.

---

## 3. Data Models

| Model | Path | Purpose |
| :--- | :--- | :--- |
| `UserProfile` | `.../model/UserProfile.java` | A named trading profile. Owns trades, alerts, and indicator configs. |
| `Trade` | `.../model/Trade.java` | A trade journal record with automatic P&L computation. |
| `PriceAlert` | `.../model/PriceAlert.java` | Configurable price and indicator-based alerts. |
| `IndicatorConfig` | `.../model/IndicatorConfig.java` | Per-profile indicator weights and parameters. |
| `OhlcvBar` | `.../model/OhlcvBar.java` | Cached candlestick bar data (symbol + timeframe). |
| **Enums** | | |
| `AssetType`, `TradeDirection` (LONG/SHORT), `TradeStatus` (OPEN/CLOSED/CANCELLED) | | Trade-related enums. |
| `AlertType`, `AlertCondition` | | Alert-related enums. |
| **Indicator Presets** | | SWING_TRADING, SCALPING, DAY_TRADING, CRYPTO_MOMENTUM, LONG_TERM, CONSERVATIVE, CUSTOM. |

---

## 4. Services (Backend Logic)

### 4.1 Trade & Portfolio

| Service | Path | Role |
| :--- | :--- | :--- |
| `TradeService` | `.../service/TradeService.java` | Handles CRUD operations, closing trades, and computing portfolio stats (win rate, profit factor, equity curve, etc.). |

### 4.2 Market Data & Pricing

| Service | Path | Role |
| :--- | :--- | :--- |
| `PriceRouter` | `.../service/price/PriceRouter.java` | Routes symbols to the correct market data provider. |
| `BinanceService` | `.../service/price/BinanceService.java` | Provides crypto quotes, WebSocket streams, and OHLCV data. |
| `CoinGeckoService` | `.../service/price/CoinGeckoService.java` | Crypto fallback provider. |
| `YahooFinanceService` | `.../service/price/YahooFinanceService.java` | Provides data for stocks and ETFs. |
| `ForexService` | `.../service/price/ForexService.java` | Provides forex data via the Frankfurter API. |
| `LiveTickerService` | `.../service/price/LiveTickerService.java` | Powers the header ticker bar. |
| `OhlcvStorageService` | `.../service/OhlcvStorageService.java` | Manages OHLCV data fetching, SQLite caching, and refresh logic. |
| `PriceQuote` | `.../service/price/PriceQuote.java` | A DTO for price quotes. |

### 4.3 Analysis Engine

| Service | Path | Role |
| :--- | :--- | :--- |
| `AnalysisService` | `.../service/analysis/AnalysisService.java` | Orchestrates the full analysis pipeline. |
| `IndicatorService` | `.../service/analysis/IndicatorService.java` | Computes technical indicators using the `ta4j` library. |
| `SupportResistanceService` | `.../service/analysis/SupportResistanceService.java` | Calculates Fibonacci levels, pivot points, and swing S/R levels. |
| `SignalScoringService` | `.../service/analysis/SignalScoringService.java` | Generates a weighted composite BUY/SELL score. |
| **DTOs** | | |
| `IndicatorResult` | `.../service/analysis/IndicatorResult.java` | DTO for indicator values. |
| `IchimokuResult` | `.../service/analysis/IchimokuResult.java` | DTO for Ichimoku-specific data. |
| **Signals** | | Generates recommendations: STRONG_BUY, BUY, NEUTRAL, SELL, STRONG_SELL. Confidence ≥60% triggers indicator alerts. |

### 4.4 Alerts & Notifications

| Service | Path | Role |
| :--- | :--- | :--- |
| `AlertService` | `.../service/alert/AlertService.java` | Polls, evaluates, and fires alerts. |
| `NotificationService` | `.../service/alert/NotificationService.java` | Manages notification delivery: Desktop tray, Email (SMTP), and Telegram. |
| `TradingTelegramBot` | `.../service/alert/TradingTelegramBot.java` | Integrates the Telegram bot. |

### 4.5 Export & Startup

| Service | Path | Role |
| :--- | :--- | :--- |
| `ExcelExportService` | `.../service/export/ExcelExportService.java` | Handles data export to `.xlsx` files. |
| `AppStartupService` | `.../service/AppStartupService.java` | Starts live WebSocket streams on application boot. |

---

## 5. Repositories (Persistence)

| Repository | Path |
| :--- | :--- |
| `UserProfileRepository` | `.../repository/UserProfileRepository.java` |
| `TradeRepository` | `.../repository/TradeRepository.java` |
| `PriceAlertRepository` | `.../repository/PriceAlertRepository.java` |
| `IndicatorConfigRepository` | `.../repository/IndicatorConfigRepository.java` |
| `OhlcvBarRepository` | `.../repository/OhlcvBarRepository.java` |
| **Configuration** | |
| `DataSourceConfig` | `.../config/DataSourceConfig.java` (Configures the SQLite datasource). |

---

## 6. Notifications (User-Visible)

| Channel | Trigger | Configuration |
| :--- | :--- | :--- |
| **Desktop Tray** | Alert fires | Uses OS `SystemTray` (default for new alerts). |
| **Email** | Alert fires | Configured via `spring.mail.*` and `notification.email.to` in `application.properties`. |
| **Telegram** | Alert fires | Enabled via `telegram.bot.enabled=true` with token, username, and chat-ids configured. |

### In-App Indicators

- **Alert Bell Badge:** Displays the count of triggered + active alerts.
- **JavaFX Dialogs:** Used for validation errors, save/export success messages, and confirmations.

### Background Jobs

- **Alert Polling:** Every 10 seconds (`app.alert.poll-interval-ms`).
- **Analysis Refresh:** Every 60 seconds for symbols on the watchlist.
- **Stock/Forex Ticker Polling:** Every 15 seconds.

---

## 7. Analysis Pipeline (End-to-End)

The core analysis flow is as follows:

1.  **Data Fetch:** User selects symbol/timeframe/bars on the chart.
2.  **Load/Cache:** `OhlcvStorageService` loads data from SQLite or fetches it via `PriceRouter`.
3.  **Indicator Computation:** `IndicatorService` calculates MACD, RSI, EMA, Bollinger, Ichimoku, Stochastic, VWAP, CCI, etc.
4.  **S/R & Fibonacci:** `SupportResistanceService` calculates pivot points and support/resistance levels.
5.  **Scoring:** `SignalScoringService` applies profile weights from `IndicatorConfig`.
6.  **Render:** Results are displayed on the `CandlestickChartCanvas` and the signal bar.
7.  **Alerting:** If a signal meets the confidence threshold (≥60%) and is a BUY/SELL, `AlertService.triggerIndicatorAlert()` fires matching alerts.

---

## 8. Controllers Summary

| Controller | FXML | Primary Function |
| :--- | :--- | :--- |
| `MainDashboardController` | `MainDashboard.fxml` | Manages the application shell, navigation, profiles, and ticker. |
| `DashboardController` | `DashboardView.fxml` | Orchestrates the Dashboard, Journal, and Portfolio views. |
| `TradeEntryController` | `TradeEntry.fxml` | Handles creation and editing of trades. |
| `ChartController` | `ChartView.fxml` | Manages the Live Chart and analysis signal bar. |
| `IndicatorMixerController` | `IndicatorMixerView.fxml` | Controls indicator weight tuning and configuration. |
| `AlertManagerController` | `AlertManagerView.fxml` | Manages price and signal alerts. |
| `ExportController` | `ExportView.fxml` | Handles Excel export generation. |

---

## 9. Planned / Not Exposed in UI

| Feature | Status |
| :--- | :--- |
| **Settings Screen** | Was a placeholder only. Now replaced with `ProfileSettingsView`. |
| **Delete Trade** | `TradeService.deleteTrade()` exists; there is currently no UI button for it. |
| **Custom Ticker Watchlist** | Code supports `addToWatchlist()`, but the Settings UI to manage it is not fully built. |
| **COMMODITY / INDEX Trades** | Model supports them, but price provider routing is limited. |

---

## 10. Entry Point & Boot

| File | Path |
| :--- | :--- |
| **Spring Boot Main** | `.../TradingPlatformAppApplication.java` |
| **JavaFX Bootstrap** | `.../config/JavaFxApplication.java`, `StageInitializer.java` |
| **App Config** | `.../resources/application.properties` |

### Asset Class Support in Live Data

- **Crypto:** Binance (primary), CoinGecko (fallback)
- **US Stocks:** Yahoo Finance
- **Major Forex Pairs:** Frankfurter API

## 11. Drawing Tools

### Chart Drawing & Trade Integration – Implementation Summary

#### What Was Built

##### Data Layer
- **ChartDrawing** entity → `chart_drawings` table (auto-created via `ddl-auto=update`).
- **ChartDrawingToolType** – full enum covering all tools from the spec:
  - Lines (trend, ray, extended, horizontal, vertical)
  - Fibonacci suite (retracement, extension, fan, time zones, channel, speed resistance)
  - Positions (long, short)
  - Shapes (rectangle, triangle, ellipse, channel)
  - Annotations (text, callout, arrow, ruler, note)
  - Utility (parallel lines, mirror)
- **ChartPoint / ChartDrawingProperties** – stored as Gson‑serialized JSON for anchors and style attributes.
- **ChartDrawingRepository** + **ChartDrawingService** – provide load/save/delete/duplicate operations, scoped by `profileId`, `symbol`, and `timeframe`.

##### Rendering & Interaction
- **DrawingRenderer** – renders all supported drawing types:
  - Trend lines, rays, horizontals, verticals
  - Rectangles, channels, parallel lines
  - Fibonacci retracement, extension, fan, time zones (with level labels)
  - Long/short positions (with R:R label and draggable entry/SL/TP lines)
  - Text labels, callouts, arrows, ruler
- **ChartDrawingEngine** – core interaction logic:
  - Click‑drag creation for 2‑point tools (press → drag → release)
  - Anchor dragging for editing (moving nodes updates the drawing)
  - Hit‑testing for selection
  - Right‑click context menu: *Delete*, *Duplicate*, *Lock*, *Create Trade from Drawing*, *Instant Save*
- **DrawingCoordinateMapper** – converts between price‑time coordinates and screen pixels; holding `Shift` toggles OHLC snap (nearest high/low/open/close).
- **CandlestickChartCanvas** extended:
  - Drawings rendered after candles, before crosshair
  - Pan disabled while drawing tool is active
  - `Ctrl+Shift+T` shortcut for instant‑saving the selected position drawing

##### UI
- **DrawingToolbar** – a floating palette on the left side of the chart, containing 17 tool icons plus a delete/selection mode.
- Wired through **ChartController**:
  - Drawings are loaded on chart refresh
  - Automatic persistence on creation, update, or deletion

##### Trade Journal Integration
- **TradeDrawingDraft** – a temporary DTO that carries position data (symbol, direction, entry, SL, TP) from the drawing.
- **TradeEntryController.initFromDrawing(...)** – pre‑fills the trade entry form with:
  - Symbol (current chart symbol)
  - Direction (LONG / SHORT)
  - Entry price (drawn entry line)
  - Stop Loss (drawn SL line)
  - Take Profit (drawn TP line)
  - Computed R:R (displayed in live preview)
- **TradeEntryController.instantSaveFromDrawing(...)** – saves the trade with defaults: quantity = 1, status = OPEN, no exit price, no strategy/notes.
- **MainDashboardController** – routes the context‑menu actions (`Create Trade from Drawing` and `Instant Save`) to the appropriate trade entry methods.

---

#### How to Use

1. **Open the Chart view** with an active profile.
2. **Select a drawing tool** from the left toolbar (e.g., Trend Line, Fib Retracement, Long Position).
3. **Click and drag** on the chart to create the drawing:
   - For 2‑point tools: press → drag → release.
   - For Fibonacci retracements: drag from a swing low to a swing high (or vice versa).
4. **Switch to `Select` mode** (top icon on the toolbar) to:
   - **Drag anchors** to adjust the drawing.
   - **Pan** the chart (click and drag on empty canvas area).
5. **Right‑click** a drawing to access the context menu:
   - **Delete** – remove the drawing.
   - **Duplicate** – create an identical copy.
   - **Lock** – prevent accidental edits.
   - **Create Trade from Drawing** – opens the full trade entry form (pre‑filled).
   - **Instant Save** – saves the trade immediately with minimal defaults (or use `Ctrl+Shift+T` for speed).
6. For **Long/Short Position** tools:
   - Drag horizontally to set the time span.
   - Entry, SL, and TP are auto‑placed at ±2% and ±4% from entry; drag the coloured lines to adjust.
   - Hold `Shift` while placing or editing to snap to the nearest OHLC of the underlying candle.

---

#### Architecture

The chart drawing system integrates seamlessly with the existing chart and trade journal components:

flowchart LR
    Toolbar --> ChartController
    ChartController --> Canvas
    Canvas --> DrawingEngine
    DrawingEngine --> DrawingRenderer
    ChartController --> ChartDrawingService
    ChartDrawingService --> DB[(chart_drawings)]
    DrawingEngine -->|position trade| MainDashboard
    MainDashboard --> TradeEntryController


---

#### Roadmap (Stubbed or Partial)

The following features are currently stubbed or have only basic/fallback rendering – they are not yet exposed in the toolbar or fully polished:

| Category | Tool / Feature | Status |
|----------|----------------|--------|
| **Complex Patterns** | Andrew's Pitchfork | Enum exists; minimal rendering |
| | Gann Fan | Enum exists; minimal rendering |
| | Triangle / Wedge | Enum exists; basic shape rendering |
| | Ellipse / Circle | Enum exists; basic shape rendering |
| **Utility** | Mirror Tool | Enum exists; copy function stubbed |
| | Parallel Lines (copy offset) | Enum exists; UI placeholder |
| **Fibonacci** | Fib Channel | Basic fan/time‑zone rendering present; UI missing |
| | Fib Speed Resistance | Not integrated into toolbar |
| **Styling** | Color picker in context menu | Not yet implemented |
| **Persistence** | Drawing templates (save/load layout) | Not implemented |
| **Export** | Shareable JSON export | Not implemented |
| **Automation** | Indicator auto‑overlay from `SupportResistanceService` | Not wired |

All core workflows from the original specification are **fully functional**:
- Draw → persist → reload across sessions
- Plan trades visually → log to journal with one click or full form
- Edit and adjust drawings with intuitive anchors

---

**Let me know** if you want any of the remaining roadmap items prioritised or if you’d like to polish a specific drawing type. I can implement the next tool or refine the existing UX (e.g., add a colour picker, improve Fib level labels, or wire up the Pitchfork).