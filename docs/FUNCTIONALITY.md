# Trading Intelligence Platform — Functionality Overview

A **desktop trading journal and market analysis application** built with Java 21, JavaFX, and Spring Boot. It aggregates free and API-key market data sources, stores history in a local or PostgreSQL database, and provides charting, technical analysis, alerts, and portfolio tracking.

---

## Core capabilities

### 1. Trading journal

- Record **long and short** trades across crypto, stocks, forex, commodities, and indices.
- Track entry/exit, stop-loss, take-profit, fees, P&amp;L, strategy labels, and notes.
- Multiple **user profiles** with separate watchlists and settings.
- Portfolio dashboard with open/closed trade statistics.

### 2. Live market data

- **14+ price providers** with automatic fallback (Binance, Yahoo, Frankfurter, Finnhub, Alpha Vantage, Polygon, Twelve Data, CoinGecko, and more).
- Live crypto ticker via **Binance WebSocket**; stocks/forex polled every 15 seconds.
- Circuit breaker and in-memory stale cache when APIs fail.

### 3. Charts &amp; technical analysis

- Candlestick charts for timeframes: **1m, 5m, 15m, 1h, 4h, 1d, 1w**.
- Indicators: MACD, RSI, EMA/SMA, Bollinger Bands, Ichimoku, ATR, Stochastic, and more (ta4j).
- Support/resistance and Fibonacci levels.
- Composite **BUY / SELL** signal scoring with configurable indicator weights.
- Background refresh every 60 seconds for watchlist symbols.

### 4. Market data persistence (PostgreSQL / dynamic tables)

With the `docker` profile or `app.market-data.dynamic-tables.enabled=true`:

- Each symbol + provider + timeframe gets its own table (e.g. `ETHUSDT_BINANCE_1h`).
- Data is **read from the database first**; APIs refresh on a **timeframe-based schedule**.
- Reference tables: `markets`, `shares`, `companies`, `market_data_table_registry`.
- Company records populated from fundamentals APIs when syncing stock symbols.

### 5. Price alerts

- Price above/below thresholds.
- Indicator-based alerts (strong buy/sell signals).
- Notifications: **desktop tray**, **email (SMTP)**, **Telegram bot**.
- Poll interval: 10 seconds (configurable).

### 6. Stock fundamentals

- Yearly revenue, profit, and earnings via **Alpha Vantage** and **Finnhub**.
- Yearly Profit view and optional Excel export sheet.
- Not available for crypto/forex (explicit UI message).

### 7. Export

- Excel workbook: trades, summary stats, optional fundamentals.

### 8. Settings

- Profile asset focus (crypto / stock / forex / multi).
- Preferred chart data provider.
- Watchlist management.
- API keys via `application-local.properties`.

---

## Architecture

```
JavaFX UI (FXML controllers)
        │
        ▼
Spring Boot services
        ├── PriceRouter ──► 14 external market APIs
        ├── OhlcvStorageService ──► DB (SQLite or PostgreSQL)
        ├── MarketDataSyncScheduler ──► periodic API → DB sync
        ├── AnalysisService ──► ta4j indicators + signals
        ├── AlertService ──► email / Telegram / desktop
        └── TradeService / ProfilePersistenceService
```

- **No embedded HTTP server** — desktop-only (`spring.main.web-application-type=none`).
- **Default DB:** SQLite in `~/.trading-platform/trading.db`.
- **Docker DB:** PostgreSQL 16 via `docker compose` + `docker` Spring profile.

---

## Suggestions for future enhancements

Items you may want to add later:

| Area | Suggestion |
|------|------------|
| **Multi-provider tables** | Store the same symbol from *all* providers in separate tables for comparison (currently: primary provider per symbol). |
| **REST API layer** | Expose read-only endpoints for mobile or web clients (would require enabling Spring Web). |
| **Historical backfill** | One-time job to download years of OHLCV into Postgres partitions. |
| **Data retention policy** | Auto-drop or archive tables older than N months. |
| **Migrations** | Flyway/Liquibase instead of `ddl-auto=update` for production. |
| **Secrets** | Docker secrets or Vault instead of flat properties files. |
| **Monitoring** | Prometheus metrics for sync lag, API errors, table sizes. |
| **Order execution** | Broker API integration (Binance, Interactive Brokers) — currently journal-only. |
| **Backtesting** | Replay stored OHLCV through strategies using ta4j. |
| **User auth** | Multi-user server deployment with login (not needed for single-desktop SQLite). |
| **Redis cache** | Hot quote cache shared across instances if you scale horizontally. |
| **Corporate actions** | Dividends, splits in `companies` / `shares` tables. |
| **CI pipeline** | GitHub Actions: `mvn test`, Docker image publish. |
| **Health checks** | Spring Actuator + DB connectivity probe for container orchestration. |

---

## Related documentation

- [DOCKER.md](./DOCKER.md) — run Postgres and the app
- [MARKET_DATA_TABLES.md](./MARKET_DATA_TABLES.md) — table naming and provider matrix
- [API_PROVIDERS.md](./API_PROVIDERS.md) — external API details
- [CONFIG.md](./CONFIG.md) — configuration reference
- [Features.md](./Features.md) — feature checklist
