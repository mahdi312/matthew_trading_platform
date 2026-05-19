Trading Intelligence Platform — User-Facing Feature Outline
Desktop JavaFX app (Spring Boot, SQLite at ~/.trading-platform/trading.db). All trades, alerts, and indicator settings are scoped to the active profile.

1. Application Shell
Purpose: Main window, navigation, live market ticker, profile management.

Item	Path
FXML
d:\@Projects\@Copy_Cup\TradingPlatformApp_FIXED\fixed\src\main\resources\fxml\MainDashboard.fxml
Controller
d:\@Projects\@Copy_Cup\TradingPlatformApp_FIXED\fixed\src\main\java\com\mst\matt\tradingplatformapp\controller\MainDashboardController.java
Theme CSS
d:\@Projects\@Copy_Cup\TradingPlatformApp_FIXED\fixed\src\main\resources\css\dark-theme.css
Global features:

Header: App title, profile selector, “+ New Profile”, alert bell with triggered-count badge, settings button
Live ticker bar: Real-time crypto (Binance WebSocket) + polled stocks/forex (15s)
Sidebar navigation: Dashboard, Live Chart, Trade Journal, Analysis, Alerts, Indicator Mixer, Portfolio, Export Excel, Settings
Status bar: Connection/profile status, last update time, version (v1.0.0)
Profile switching: Creates default indicator config per profile; refreshes all open views
Default profiles on first run: Crypto Portfolio, Stocks Journal, Forex Trading
Not yet implemented: Settings (shows “Settings coming soon” placeholder)

2. Main Views & Features
2.1 Dashboard
Item	Path
FXML
...\fxml\DashboardView.fxml
Controller
...\controller\DashboardController.java
Service
...\service\TradeService.java
Features (ViewMode: DASHBOARD):

KPI stat cards: Total P&L ($ and %), win rate, total/open trades, best/worst trade, profit factor, avg win/loss
Equity curve chart (Canvas) with filters: 1W, 1M, 3M, ALL
Asset breakdown: P&L by CRYPTO / STOCK / FOREX with progress bars
Recent trades table (last 20): date, symbol, type, direction, entry/exit, qty, P&L, status
Actions: + New Trade, View All (→ Journal), Edit (✏), Close open trade (✓ with exit price dialog)
2.2 Portfolio
Same view/controller as Dashboard, ViewMode: PORTFOLIO.

Shows: Stat cards + equity curve + asset breakdown
Hides: Trades table

2.3 Trade Journal
Same view/controller, ViewMode: JOURNAL.

Shows: Full trade history table (all trades, not limited to 20)
Hides: Stat cards and equity/breakdown sections

2.4 Trade Entry / Edit
Item	Path
FXML
...\fxml\TradeEntry.fxml
Controller
...\controller\TradeEntryController.java
Services
TradeService, PriceRouter
Features:

New or edit trade: Symbol, asset type, exchange, long/short, strategy
Pricing: Entry/exit price, quantity, stop loss, take profit, fees
Dates: Entry date/time, optional exit date
Notes field
Fetch Price: Live quote via PriceRouter; auto-fills entry price, asset type, exchange
Live preview: Invested amount, P&L ($/%), risk:reward ratio
Save/Cancel: Validates required fields; open vs closed status based on exit price
Modes: “New Trade Entry” or “Edit Trade” (populated from dashboard edit action)
Supported asset types: CRYPTO, STOCK, FOREX, COMMODITY, INDEX

2.5 Live Chart
Item	Path
FXML
...\fxml\ChartView.fxml
Controller
...\controller\ChartController.java
Chart UI
...\ui\chart\CandlestickChartCanvas.java
Services
AnalysisService, OhlcvStorageService, PriceRouter
Features:

Symbol: Preset combo (BTCUSDT, ETHUSDT, SOLUSDT, BNBUSDT, AAPL, TSLA, MSFT, EURUSD, GBPUSD) or custom text input
Timeframes: 1m, 5m, 15m, 1h, 4h, 1D, 1W
Bar count: 50–1000 (default 200)
Chart overlays (toggle): EMA, Bollinger Bands, Ichimoku, S/R, Volume, MACD, RSI
Chart interaction: Zoom (scroll), pan (drag), crosshair with OHLCV tooltip
Actions: Load, Refresh (force API re-fetch), Analyze
Signal bar: BUY/SELL recommendation, confidence %, best buy/sell prices, bull/neutral/bear counts, live price
2.6 Analysis
Same screen as Live Chart — nav item Analysis opens ChartView with the signal bar as the primary analysis output. Composite scoring runs automatically on chart load.

2.7 Indicator Mixer
Item	Path
FXML
...\fxml\IndicatorMixerView.fxml
Controller
...\controller\IndicatorMixerController.java
Services
IndicatorService, SignalScoringService, SupportResistanceService, OhlcvStorageService
Repository
...\repository\IndicatorConfigRepository.java
Features:

Presets: Swing Trading, Scalping, Day Trading, Crypto Momentum, Long Term, Conservative, Custom
Per-indicator controls: Enable/disable, weight slider (0–10), period/threshold spinners
Indicators configurable: MACD, RSI, Ichimoku, EMA (incl. golden/death cross periods), Bollinger, Fibonacci, Stochastic, VWAP, CCI
Save Config / Reset (per profile)
Live preview panel: Signal label, confidence progress bar, best buy/sell, per-indicator signal breakdown (when chart data is available)
2.8 Alert Manager
Item	Path
FXML
...\fxml\AlertManagerView.fxml
Controller
...\controller\AlertManagerController.java
Service
...\service\alert\AlertService.java
Features:

Create alert: Symbol, type, target price/%, notification channels, repeating flag, custom message
Alert types: PRICE_ABOVE, PRICE_BELOW, PCT_CHANGE_24H, INDICATOR_BUY_SIGNAL, INDICATOR_SELL_SIGNAL, FIBONACCI_LEVEL_TOUCH, VOLUME_SPIKE
Notification channels: Email, Telegram, Desktop (default on)
Alerts table: Symbol, type, target, status, notify icons, triggered time
Row actions: Toggle active/pause (⏸), Delete (🗑)
Background polling: Every 10 seconds for price-based alerts
2.9 Export Excel
Item	Path
FXML
...\fxml\ExportView.fxml
Controller
...\controller\ExportController.java
Service
...\service\export\ExcelExportService.java
Features:

Export to .xlsx via file chooser (profile-scoped, timestamped default filename)
Sheet 1 — Trade Log: Full trade list, color-coded P&L, auto-filter
Sheet 2 — Summary: Portfolio KPI dashboard
Sheet 3 — Asset Breakdown: Grouped stats + embedded bar chart
Sheet 4 — Equity Curve: Cumulative P&L table + embedded line chart
3. Data Models
Model	Path	Purpose
UserProfile
...\model\UserProfile.java
Named trading profile; owns trades, alerts, indicator config
Trade
...\model\Trade.java
Trade journal record with auto P&L computation
PriceAlert
...\model\PriceAlert.java
Configurable price/indicator alerts
IndicatorConfig
...\model\IndicatorConfig.java
Per-profile indicator weights and parameters
OhlcvBar
...\model\OhlcvBar.java
Cached candlestick bar (symbol + timeframe)
Trade enums: AssetType, TradeDirection (LONG/SHORT), TradeStatus (OPEN/CLOSED/CANCELLED)
Alert enums: AlertType, AlertCondition
Indicator presets: SWING_TRADING, SCALPING, DAY_TRADING, CRYPTO_MOMENTUM, LONG_TERM, CONSERVATIVE, CUSTOM

4. Services (Backend Logic)
4.1 Trade & Portfolio
Service	Path
TradeService
...\service\TradeService.java
CRUD, close trade, portfolio stats (win rate, profit factor, equity curve, etc.)

4.2 Market Data & Pricing
Service	Path	Role
PriceRouter
...\service\price\PriceRouter.java
Routes symbols to correct provider
BinanceService
...\service\price\BinanceService.java
Crypto quotes + WebSocket + OHLCV
CoinGeckoService
...\service\price\CoinGeckoService.java
Crypto fallback
YahooFinanceService
...\service\price\YahooFinanceService.java
Stocks/ETFs
ForexService
...\service\price\ForexService.java
Forex via Frankfurter
LiveTickerService
...\service\price\LiveTickerService.java
Header ticker bar
OhlcvStorageService
...\service\OhlcvStorageService.java
OHLCV fetch, SQLite cache, refresh
PriceQuote
...\service\price\PriceQuote.java
Quote DTO
4.3 Analysis Engine
Service	Path	Role
AnalysisService
...\service\analysis\AnalysisService.java
Full pipeline orchestration
IndicatorService
...\service\analysis\IndicatorService.java
ta4j indicator computation
SupportResistanceService
...\service\analysis\SupportResistanceService.java
Fibonacci, pivots, swing S/R
SignalScoringService
...\service\analysis\SignalScoringService.java
Weighted composite BUY/SELL score
IndicatorResult
...\service\analysis\IndicatorResult.java
Indicator values DTO
IchimokuResult
...\service\analysis\IchimokuResult.java
Ichimoku-specific data
Signal recommendations: STRONG_BUY, BUY, NEUTRAL, SELL, STRONG_SELL (confidence ≥60% triggers indicator alerts)

4.4 Alerts & Notifications
Service	Path	Role
AlertService
...\service\alert\AlertService.java
Poll, evaluate, fire alerts
NotificationService
...\service\alert\NotificationService.java
Desktop tray, email (SMTP), Telegram
TradingTelegramBot
...\service\alert\TradingTelegramBot.java
Telegram bot integration
4.5 Export & Startup
Service	Path
ExcelExportService
...\service\export\ExcelExportService.java
AppStartupService
...\service\AppStartupService.java — starts live WebSocket streams on boot
5. Repositories (Persistence)
Repository	Path
UserProfileRepository
...\repository\UserProfileRepository.java
TradeRepository
...\repository\TradeRepository.java
PriceAlertRepository
...\repository\PriceAlertRepository.java
IndicatorConfigRepository
...\repository\IndicatorConfigRepository.java
OhlcvBarRepository
...\repository\OhlcvBarRepository.java
Config: ...\config\DataSourceConfig.java — SQLite datasource

6. Notifications (User-Visible)
Channel	When	Configuration
Desktop tray
Alert fires
OS SystemTray (default for new alerts)
Email
Alert fires
SMTP in application.properties (spring.mail.*, notification.email.to)
Telegram
Alert fires
telegram.bot.enabled=true + token/username/chat-ids
In-app indicators:

Alert bell badge (count of triggered + active alerts)
JavaFX Alert dialogs for save/export/validation errors and success messages
Background jobs:

Alert polling: every 10s (app.alert.poll-interval-ms)
Analysis refresh: every 60s for watchlist symbols
Stock/forex ticker poll: every 15s
7. Analysis Pipeline (End-to-End)
OHLCV fetch/cache → ta4j indicators → S/R levels → weighted signal score → Chart + Signal Bar + Indicator Alerts
User selects symbol/timeframe/bars on chart
OhlcvStorageService loads from SQLite or fetches via PriceRouter
IndicatorService computes MACD, RSI, EMA, Bollinger, Ichimoku, Stochastic, VWAP, CCI, etc.
SupportResistanceService finds Fibonacci, pivot, and swing levels
SignalScoringService applies profile weights from IndicatorConfig
Results render on CandlestickChartCanvas + signal bar
If confidence ≥60% and BUY/SELL: AlertService.triggerIndicatorAlert() fires matching indicator alerts
8. Controllers Summary
Controller	FXML	Primary user function
MainDashboardController
MainDashboard.fxml
Shell, nav, profiles, ticker
DashboardController
DashboardView.fxml
Dashboard / Journal / Portfolio
TradeEntryController
TradeEntry.fxml
Create/edit trades
ChartController
ChartView.fxml
Live chart + analysis signal bar
IndicatorMixerController
IndicatorMixerView.fxml
Indicator weight tuning
AlertManagerController
AlertManagerView.fxml
Price & signal alerts
ExportController
ExportView.fxml
Excel export
9. Planned / Not Exposed in UI
Feature	Status
Settings screen
Placeholder only
Delete trade
TradeService.deleteTrade() exists; no UI button
Custom ticker watchlist
Code supports addToWatchlist(); Settings UI not built
COMMODITY / INDEX trades
Model supports; limited price provider routing
10. Entry Point & Boot
File	Path
Spring Boot main
...\TradingPlatformAppApplication.java
JavaFX bootstrap
...\config\JavaFxApplication.java, StageInitializer.java
App config
...\resources\application.properties
Asset classes supported in live data: Crypto (Binance/CoinGecko), US stocks (Yahoo), major forex pairs (Frankfurter).