# Trading Intelligence Platform — User Guide

Desktop trading journal and analysis app built with **JavaFX + Spring Boot + SQLite**.

Data is stored locally at `~/.trading-platform/trading.db`. Everything is scoped to the **active profile** (Crypto Portfolio, Stocks Journal, Forex Trading, or custom profiles you create).

---

## Main navigation

| Menu item | What it does |
|-----------|----------------|
| **Dashboard** | Portfolio overview: P&L stats, equity curve, asset breakdown, last 20 trades |
| **Live Chart** | Candlestick chart with indicators, timeframes, and signal bar |
| **Trade Journal** | Full trade history table (all trades for the profile) |
| **Analysis** | Same as Live Chart — opens chart with BUY/SELL signal scoring |
| **Alerts** | Create and manage price / indicator alerts |
| **Indicator Mixer** | Tune indicator weights and presets per profile |
| **Portfolio** | Stats + equity curve + breakdown (no trade table) |
| **Yearly Profit** | Fundamentals view — yearly revenue/profit/EBITDA rows per ticker (T-17) |
| **Export Excel** | Download multi-sheet `.xlsx` report (optionally with a fundamentals sheet — T-22) |
| **Settings** | Profile preferences — asset focus, default symbol, chart/fundamentals provider, custom watchlist (T-12, T-17) |

---

## Profiles

- Switch profiles from the header dropdown.
- **+ New Profile** creates a profile with default Swing Trading indicator config.
- **✏ Rename / 🗑 Delete** buttons next to the selector let you rename or remove the active profile (T-13). Deleting cascades through all trades, alerts and indicator configs; the last remaining profile cannot be deleted.
- Switching profile refreshes dashboard, chart, alerts, mixer, and export context.
- A custom **watchlist** can be set per profile in Settings (T-12); it overrides the hard-coded ticker bar defaults the moment you save.

---

## Dashboard / Portfolio / Trade Journal

Three modes of the same data, optimized for different tasks:

### Dashboard
- **Stat cards:** Total P&L, win rate, trade counts, best/worst trade, profit factor
- **Equity curve** with 1W / 1M / 3M / ALL filters
- **Asset breakdown** by CRYPTO / STOCK / FOREX
- **Recent trades** (20 rows) with Edit and Close actions

### Portfolio
- Stat cards + equity curve + breakdown only (no trades table)

### Trade Journal
- Full trades table only (every trade for the profile)
- **+ New Trade** opens the trade entry form
- **Edit (✏)** opens trade entry in edit mode
- **Close (✓)** on open trades prompts for exit price

---

## Trade Entry

Create or edit a trade:

| Field | Notes |
|-------|--------|
| Symbol | e.g. `BTCUSDT`, `AAPL`, `EURUSD` |
| Asset type | CRYPTO, STOCK, FOREX, COMMODITY, INDEX |
| Direction | LONG / SHORT |
| Entry / exit price, quantity | Required for save |
| Stop loss, take profit, fees | Optional; used for R:R preview |
| **Fetch Price** | Live quote via market-data APIs |

**Live preview** shows invested amount, P&L ($/%), and risk:reward while you type.

---

## Live Chart & Analysis

### Toolbar
- **Symbol dropdown** or custom text (BTCUSDT, ETHUSDT, AAPL, EURUSD, …)
- **Load** — fetch OHLCV and run analysis
- **Timeframes:** 1m, 5m, 15m, 1h, 4h, 1D, 1W
- **Bars:** 50–1000 candles (default 200)
- **Overlays:** EMA, Bollinger, Ichimoku, S/R, Volume, MACD, RSI
- **Refresh** — force re-download from API
- **Analyze** — re-run signal scoring

### Chart interaction
- Scroll to zoom, drag to pan
- Crosshair with OHLCV tooltip

### Signal bar (after load)
- Recommendation (STRONG BUY → STRONG SELL)
- Confidence %, best buy/sell prices
- Bull / neutral / bear indicator counts
- Live price when quote API succeeds

---

## Indicator Mixer

Per-profile indicator configuration:

- **Presets:** Swing Trading, Scalping, Day Trading, Crypto Momentum, Long Term, Conservative, Custom
- **Indicators:** MACD, RSI, Ichimoku, EMA, Bollinger, Fibonacci, Stochastic, VWAP, CCI
- Each has enable toggle, weight slider (0–10), and period/threshold spinners
- **Save Config** / **Reset**
- Preview panel updates when chart data is available

---

## Alerts

### Alert types
- PRICE_ABOVE / PRICE_BELOW
- PCT_CHANGE_24H
- INDICATOR_BUY_SIGNAL / INDICATOR_SELL_SIGNAL
- FIBONACCI_LEVEL_TOUCH
- VOLUME_SPIKE

### Notification channels
- **Desktop** (default)
- **Email** (SMTP in `application.properties`)
- **Telegram** (`telegram.bot.enabled=true`)

Alerts are polled every **10 seconds**. Triggered active alerts show on the header bell badge.

---

## Export Excel

Exports for the active profile:

1. **Trade Log** — all trades, color-coded P&L
2. **Summary** — portfolio KPIs
3. **Asset Breakdown** — grouped stats + chart
4. **Equity Curve** — cumulative P&L table + chart

---

## Market data providers

| Asset class | Primary API | Fallback |
|-------------|-------------|----------|
| Crypto (USDT pairs) | Binance REST + WebSocket | CoinGecko |
| Crypto (base tickers) | CoinGecko | Binance (auto `BTC` → `BTCUSDT`) |
| US stocks / ETFs | Yahoo Finance chart API | — |
| Forex (6-letter pairs) | Frankfurter (ECB rates) | Yahoo (`EURUSD=X`) |

See [API_PROVIDERS.md](./API_PROVIDERS.md) for endpoints, response shapes, and troubleshooting.

---

## Configuration (`application.properties`)

| Key | Purpose |
|-----|---------|
| `api.binance.base-url` | Primary Binance REST |
| `api.binance.fallback-url` | Mirror when primary is blocked (`data-api.binance.vision`) |
| `api.yahoo.base-url` | Yahoo chart API base |
| `api.frankfurter.base-url` | Forex rates |
| `api.coingecko.base-url` / `api.coingecko.key` | CoinGecko fallback |
| `api.http.connect-timeout-sec` | Network timeout (default 30) |
| `api.http.read-timeout-sec` | Read timeout (default 45) |
| `api.http.retry-count` | Automatic retries on timeout/5xx |

---

## Running tests

```bash
# Unit tests with mocked API responses (no network)
mvn test

# Optional live smoke tests (requires internet)
mvn test -Dtest=LivePriceApiSmokeTest -Dlive.api.tests=true
```

---

## Known limitations

- **Settings UI** not implemented (watchlist customization exists in code only)
- **Forex OHLCV** is daily ECB data (approximated intraday high/low)
- **Network blocks:** Some regions block Binance/Yahoo; use fallback URL or VPN
- **CoinGecko rate limits** on free tier — Binance preferred for crypto

---

## Tech stack

- Java 21, JavaFX 21, Spring Boot 3.3
- SQLite + JPA (Hibernate)
- ta4j for indicators
- Apache POI for Excel export
- OkHttp for HTTP/WebSocket
