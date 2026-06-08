# Market Data Tables Reference

When `app.market-data.dynamic-tables.enabled=true` (automatic with the `docker` profile), OHLCV candlesticks are stored in **dedicated tables** named:

```
{SYMBOL}_{PROVIDER}_{TIMEFRAME}
```

Examples:

| Symbol | Provider | Timeframe | Table name |
|--------|----------|-----------|------------|
| ETHUSDT | BINANCE | 1h | `ETHUSDT_BINANCE_1h` |
| AAPL | YAHOO | 1d | `AAPL_YAHOO_1d` |
| EURUSD | FRANKFURTER | 1d | `EURUSD_FRANKFURTER_1d` |
| BTCUSDT | COINGECKO | 4h | `BTCUSDT_COINGECKO_4h` |

Registry metadata is stored in `market_data_table_registry`.

---

## Table schema (each dynamic OHLCV table)

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGINT / INTEGER | Surrogate key |
| `open_time` | TIMESTAMP | Candle open time (unique) |
| `open_price` | NUMERIC | Open |
| `high_price` | NUMERIC | High |
| `low_price` | NUMERIC | Low |
| `close_price` | NUMERIC | Close |
| `volume` | NUMERIC | Volume |
| `asset_type` | VARCHAR | CRYPTO, STOCK, FOREX, … |
| `updated_at` | TIMESTAMP | Last write time |

---

## Reference data tables

| Table | Purpose |
|-------|---------|
| `markets` | Exchange / venue per asset class and provider (e.g. `CRYPTO_BINANCE`) |
| `shares` | Tradable symbols linked to a market (and company when available) |
| `companies` | Issuer fundamentals (sector, industry, country) from Alpha Vantage / Finnhub |
| `market_data_table_registry` | Maps symbol + provider + timeframe → physical table name |

Legacy unified cache (SQLite default mode): `ohlcv_bars`.

---

## Supported timeframes

| Timeframe | Sync interval |
|-----------|---------------|
| `1m` | 1 minute |
| `5m` | 5 minutes |
| `15m` | 15 minutes |
| `1h` | 1 hour |
| `4h` | 4 hours |
| `1d` | 1 day |
| `1w` | 7 days |

The scheduler checks every **30 seconds** which registry entries are due (`next_sync_at`).

---

## Data flow

1. Chart / analysis requests bars via `OhlcvStorageService`.
2. Service **reads from the database first**.
3. If the table is empty, a **bootstrap** fetch runs once from the API.
4. If data exists but sync is due, an **async refresh** runs in the background.
5. UI always receives DB data immediately (stale-while-revalidate).

---

## Provider → asset class → example tables

### Crypto

| Provider | API (external) | Example symbols | OHLCV support |
|----------|----------------|-----------------|---------------|
| **BINANCE** | `api.binance.com` — `/api/v3/klines` | BTCUSDT, ETHUSDT | 1m–1w |
| **COINGECKO** | `/coins/{id}/ohlc` | BTC, ETH | Approximated granularity |
| **TWELVE_DATA** | `/time_series` | BTC/USD | 1m–1w |
| **ALPHA_VANTAGE** | `TIME_SERIES_INTRADAY` / `DAILY` | BTCUSD | 1m–1d |
| **FINNHUB** | `/crypto/candle` | BINANCE:ETHUSDT | 1m–1w |
| **YAHOO** | `/v8/finance/chart` | BTC-USD | 1m–1w |

### Stocks

| Provider | API (external) | Example symbols | OHLCV support |
|----------|----------------|-----------------|---------------|
| **YAHOO** | `/v8/finance/chart` | AAPL, MSFT | 1m–1w |
| **FINNHUB** | `/stock/candle` | AAPL | 1m–1w |
| **POLYGON** | `/v2/aggs/ticker/...` | AAPL | 1m–1w |
| **ALPHA_VANTAGE** | `TIME_SERIES_*` | AAPL | 1m–1d |
| **TWELVE_DATA** | `/time_series` | AAPL | 1m–1w |
| **MARKETSTACK** | `/v1/eod` | AAPL | Daily EOD only |

### Forex

| Provider | API (external) | Example symbols | OHLCV support |
|----------|----------------|-----------------|---------------|
| **FRANKFURTER** | `api.frankfurter.app` | EURUSD | Daily (ECB rates) |
| **FIXER** | `data.fixer.io` | EURUSD | Spot quote only |
| **FREE_CURRENCY_API** | `freecurrencyapi.com` | EURUSD | Spot quote only |
| **OPEN_EXCHANGE_RATES** | `openexchangerates.org` | EURUSD | Spot quote only |
| **EXCHANGE_RATE_API** | `exchangerate-api.com` | EURUSD | Spot quote only |
| **CURRENCY_LAYER** | `currencylayer.com` | EURUSD | Spot quote only |
| **TWELVE_DATA** | `/time_series` | EUR/USD | 1m–1w |
| **ALPHA_VANTAGE** | `FX_*` | EURUSD | 1m–1d |
| **FINNHUB** | `/forex/candle` | OANDA:EUR_USD | 1m–1w |
| **YAHOO** | `EURUSD=X` | EURUSD | 1m–1w |

> Forex spot-only providers populate **quotes** via `PriceRouter` but produce **no intraday OHLCV**; the router falls through to Yahoo / Finnhub / Twelve Data for candles.

---

## Provider selection per symbol

The **primary provider** for a table is the first enabled service in the fallback chain for that asset class (see `PriceProviderRegistry`). User profile **Chart Provider** preference is prepended to the chain.

Tables are created on first access for that symbol + timeframe + primary provider combination.
