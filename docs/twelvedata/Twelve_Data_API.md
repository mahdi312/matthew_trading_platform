# Twelve Data API — Complete Reference Guide

*Comprehensive API documentation covering all 154 endpoints across 6 categories*

**Base URL:** `https://api.twelvedata.com`

**WebSocket URL:** `wss://ws.twelvedata.com/v1/quotes/price`

*Prepared July 2026 · Based on official Twelve Data documentation (twelvedata.com/docs)*

---

## Table of Contents

1. [Overview](#1-overview)
2. [Base URL & Authentication](#2-base-url--authentication)
3. [Subscription Plans & Pricing Tiers](#3-subscription-plans--pricing-tiers)
4. [Credit System & Rate Limits](#4-credit-system--rate-limits)
5. [Request/Response Conventions](#5-requestresponse-conventions)
6. [Core Market Data Endpoints (9 endpoints)](#6-core-market-data-endpoints-9-endpoints)
7. [Reference / Symbol Catalog Endpoints (9 endpoints)](#7-reference--symbol-catalog-endpoints-9-endpoints)
8. [Technical Indicators (98 endpoints)](#8-technical-indicators-98-endpoints)
9. [Fundamentals Endpoints (17 endpoints)](#9-fundamentals-endpoints-17-endpoints)
10. [Analysis Endpoints (9 endpoints)](#10-analysis-endpoints-9-endpoints)
11. [Mutual Funds Endpoints (12 endpoints)](#11-mutual-funds-endpoints-12-endpoints)
12. [Batch Requests](#12-batch-requests)
13. [WebSocket Streaming](#13-websocket-streaming)
14. [Error Handling & Status Codes](#14-error-handling--status-codes)
15. [Best Practices](#15-best-practices)

---

## 1. Overview

Twelve Data is a financial market data provider offering a unified REST API and WebSocket service covering **stocks, ETFs, indices, forex, cryptocurrencies, mutual funds, fundamentals, and 100+ technical indicators** across roughly **one million instruments** and **70+ global exchanges**.

The API consists of **154 endpoints** organized into six categories:

| Category             | Endpoints | Coverage   |
| -------------------- | --------- | ---------- |
| Reference Data       | 9         | ✅ 100%     |
| Core Market Data     | 9         | ✅ 100%     |
| Technical Indicators | 98        | ✅ 100%     |
| Fundamentals         | 17        | ✅ 100%     |
| Analysis             | 9         | ✅ 100%     |
| Mutual Funds         | 12        | ✅ 100%     |
| **Total**            | **154**   | **✅ 100%** |

All REST endpoints share the same base URL, authentication method, and return JSON by default.

---

## 2. Base URL & Authentication

| Type          | URL                                       |
| ------------- | ----------------------------------------- |
| **REST API**  | `https://api.twelvedata.com`              |
| **WebSocket** | `wss://ws.twelvedata.com/v1/quotes/price` |

### Authentication Methods

Every request must be authenticated with an API key. There are two supported ways:

#### Method 1 — Query Parameter

```
GET https://api.twelvedata.com/time_series?symbol=AAPL&interval=1day&apikey=YOUR_API_KEY
```

#### Method 2 — Authorization Header (recommended for production)

```
GET /time_series?symbol=AAPL&interval=1day HTTP/1.1
Host: api.twelvedata.com
Authorization: apikey YOUR_API_KEY
```

### Demo Key

For quick testing against a restricted set of trial symbols (e.g., AAPL, EUR/USD, BTC/USD), use `apikey=demo` without registering.

### Parameter Guidelines

- **Separator:** Use `&` to separate multiple parameters
- **Case sensitivity:** Parameter names are **case-insensitive** (`symbol=AAPL` = `symbol=aapl`)
- **Multiple values:** Separate with commas where supported

### Security Note

> ⚠️ **Never expose your API key** in client-side JavaScript, mobile app bundles, or public repositories. Proxy requests through your own backend.

---

## 3. Subscription Plans & Pricing Tiers

| Plan             | Starting Price | API Credits/Min | Daily Cap | Market Access                                                | WebSocket              |
| ---------------- | -------------- | --------------- | --------- | ------------------------------------------------------------ | ---------------------- |
| **Free (Basic)** | $0             | 8               | 800/day   | US equities, forex, crypto (trial set)                       | Trial only (8 credits) |
| **Grow**         | From $29/mo    | 55–377          | No limit  | + Level A stocks, ETFs, indices, starter fundamentals        | Not included           |
| **Pro**          | From $99/mo    | 610–1,597       | No limit  | + 70+ global markets, Level B assets, essential fundamentals | ✅ Included             |
| **Ultra**        | From $329/mo   | 2,584–10,946    | No limit  | + Level C stocks, full India coverage, all fundamentals, fund breakdowns | ✅ Included             |
| **Enterprise**   | Custom         | Custom          | No limit  | All datasets + redistribution rights, dedicated support      | ✅ Included             |

### Feature Gating Examples

| Feature                               | Minimum Plan       |
| ------------------------------------- | ------------------ |
| `/profile` (company profile)          | Grow               |
| `/balance_sheet`, `/income_statement` | Pro                |
| `prepost` parameter (extended hours)  | Pro                |
| `figi_code` filter                    | Ultra / Enterprise |
| WebSocket streaming                   | Pro                |
| Mutual fund ratings                   | Ultra              |

---

## 4. Credit System & Rate Limits

Twelve Data meters usage in **credits**. Every endpoint has a defined data weight (credit cost). Your plan grants a pool of API credits that resets every clock minute.

### Credit Consumption Formula

```
Credits Used = Data Weight × Number of Symbols Requested
```

- `/time_series` for 3 symbols (weight 1) → `1 × 3 = 3` credits
- `/income_statement` for 3 symbols (weight 100) → `100 × 3 = 300` credits

### Rate-Limit Response Headers

Every successful response includes:

```
api-credits-used: 12
api-credits-left: 598
```

### Exceeding Quota

> ⚠️ Returns **HTTP 429 Too Many Requests**. Quota replenishes at the start of the next minute. Implement exponential backoff.

### WebSocket Credits

WebSocket subscriptions consume **1 credit per successfully subscribed symbol**. Maximum **3 concurrent connections** per account.

---

## 5. Request/Response Conventions

- **Method:** All REST endpoints use HTTP GET (except batch POST)
- **Format:** JSON by default; `format=CSV` supported on many endpoints
- **Success envelope:** Single-symbol responses include `"status": "ok"`
- **Error envelope:** `{code, message, status:"error"}` with matching HTTP status code
- **Dates:** Use `start_date` / `end_date` (YYYY-MM-DD or YYYY-MM-DD HH:MM:SS)
- **Decimal precision:** `dp` parameter (0–11) controls decimal places
- **Timezones:** `UTC`, `Exchange`, or IANA timezone name

---

## 6. Core Market Data Endpoints (9 endpoints)

These endpoints form the foundation: live and historical prices for equities, forex, and crypto.

### 6.1 Time Series — `GET /time_series`

Returns historical OHLCV data for a symbol at a given interval.

- **Credit weight:** 1 credit per symbol

| Parameter    | Type    | Required | Description                                                  |
| ------------ | ------- | -------- | ------------------------------------------------------------ |
| `symbol`     | string  | Yes      | Instrument ticker (comma-separated for multiple)             |
| `interval`   | string  | Yes      | 1min, 5min, 15min, 30min, 45min, 1h, 2h, 4h, 8h, 1day, 1week, 1month |
| `outputsize` | integer | No       | 1–5000, default 30                                           |
| `start_date` | string  | No       | YYYY-MM-DD or YYYY-MM-DD HH:MM:SS                            |
| `end_date`   | string  | No       | YYYY-MM-DD or YYYY-MM-DD HH:MM:SS                            |
| `adjust`     | boolean | No       | Specifies if adjustment should be applied, default true      |
| `dp`         | integer | No       | Decimal places (0–11), default 5                             |
| `timezone`   | string  | No       | UTC, Exchange, or IANA name                                  |
| `format`     | string  | No       | JSON (default) or CSV                                        |
| `prepost`    | boolean | No       | Extended hours (Pro+). 1min, 5min, 15min, 30min intervals only |
| `apikey`     | string  | Yes*     | Required unless via Auth header                              |

**Sample Request:**
```
GET https://api.twelvedata.com/time_series?symbol=AAPL&interval=1day&outputsize=5&apikey=YOUR_API_KEY
```

**Sample Response:**
```json
{
  "meta": {
    "symbol": "AAPL",
    "interval": "1day",
    "currency": "USD",
    "exchange_timezone": "America/New_York",
    "exchange": "NASDAQ",
    "type": "Common Stock"
  },
  "values": [
    {
      "datetime": "2026-06-30",
      "open": "213.10",
      "high": "215.42",
      "low": "212.05",
      "close": "214.88",
      "volume": "48213500"
    }
  ],
  "status": "ok"
}
```

### 6.2 Quote — `GET /quote`

Returns a full real-time quote snapshot.

- **Credit weight:** 1 credit per symbol

| Parameter  | Type    | Required | Description                        |
| ---------- | ------- | -------- | ---------------------------------- |
| `symbol`   | string  | Yes      | Instrument ticker                  |
| `figi`     | string  | No       | FIGI identifier (Ultra/Enterprise) |
| `isin`     | string  | No       | ISIN identifier                    |
| `cusip`    | string  | No       | CUSIP identifier                   |
| `interval` | string  | No       | Default 1day                       |
| `exchange` | string  | No       | Exchange filter                    |
| `mic_code` | string  | No       | Market Identifier Code             |
| `prepost`  | boolean | No       | Extended hours data (Pro+)         |
| `apikey`   | string  | Yes*     | Required unless via Auth header    |

**Sample Request:**
```
GET https://api.twelvedata.com/quote?symbol=AAPL&apikey=YOUR_API_KEY
```

**Sample Response:**
```json
{
  "symbol": "AAPL",
  "name": "Apple Inc",
  "exchange": "NASDAQ",
  "mic_code": "XNAS",
  "currency": "USD",
  "datetime": "2021-09-16",
  "timestamp": 1631772000,
  "open": "148.44000",
  "high": "148.96840",
  "low": "147.22099",
  "close": "148.85001",
  "volume": "67903927",
  "previous_close": "149.09000",
  "change": "-0.23999",
  "percent_change": "-0.16097",
  "is_market_open": false,
  "fifty_two_week": {
    "low": "103.10000",
    "high": "157.25999",
    "low_change": "45.75001",
    "high_change": "-8.40999",
    "low_change_percent": "44.37440",
    "high_change_percent": "-5.34782",
    "range": "103.099998 - 157.259995"
  },
  "extended_change": "0.09",
  "extended_percent_change": "0.05",
  "extended_price": "125.22",
  "extended_timestamp": 1649845281
}
```

### 6.3 Real-Time Price — `GET /price`

Returns just the latest traded price — the lightest-weight endpoint.

- **Credit weight:** 1 credit per symbol

| Parameter  | Type    | Required | Description                                      |
| ---------- | ------- | -------- | ------------------------------------------------ |
| `symbol`   | string  | Yes      | Instrument ticker (comma-separated for multiple) |
| `figi`     | string  | No       | FIGI identifier (Ultra/Enterprise)               |
| `isin`     | string  | No       | ISIN identifier                                  |
| `cusip`    | string  | No       | CUSIP identifier                                 |
| `exchange` | string  | No       | Exchange filter                                  |
| `mic_code` | string  | No       | Market Identifier Code                           |
| `country`  | string  | No       | Country filter                                   |
| `type`     | string  | No       | Asset class filter                               |
| `prepost`  | boolean | No       | Extended hours (Pro+)                            |
| `dp`       | integer | No       | Decimal places (0–11), default 5                 |
| `format`   | string  | No       | JSON or CSV                                      |
| `apikey`   | string  | Yes*     | Required unless via Auth header                  |

**Sample Request:**
```
GET https://api.twelvedata.com/price?symbol=AAPL&apikey=YOUR_API_KEY
```

**Sample Response:**
```json
{
  "price": "214.88"
}
```

### 6.4 End of Day Price — `GET /eod`

Returns the most recent end-of-day closing price.

- **Credit weight:** 1 credit per symbol

| Parameter | Type   | Required | Description                     |
| --------- | ------ | -------- | ------------------------------- |
| `symbol`  | string | Yes      | Instrument ticker               |
| `apikey`  | string | Yes*     | Required unless via Auth header |

**Sample Request:**
```
GET https://api.twelvedata.com/eod?symbol=AAPL&apikey=YOUR_API_KEY
```

**Sample Response:**
```json
{
  "symbol": "AAPL",
  "exchange": "NASDAQ",
  "datetime": "2026-06-30",
  "close": "214.88"
}
```

### 6.5 Exchange Rate — `GET /exchange_rate`

Returns the current exchange rate between two currencies.

- **Credit weight:** 1 credit

| Parameter | Type   | Required | Description                     |
| --------- | ------ | -------- | ------------------------------- |
| `symbol`  | string | Yes      | Currency pair, e.g., USD/JPY    |
| `apikey`  | string | Yes*     | Required unless via Auth header |

**Sample Request:**
```
GET https://api.twelvedata.com/exchange_rate?symbol=USD/JPY&apikey=YOUR_API_KEY
```

**Sample Response:**
```json
{
  "symbol": "USD/JPY",
  "rate": 144.32,
  "timestamp": 1751500800
}
```

### 6.6 Currency Conversion — `GET /currency_conversion`

Converts a specified amount from one currency to another.

- **Credit weight:** 1 credit

| Parameter | Type   | Required | Description                     |
| --------- | ------ | -------- | ------------------------------- |
| `symbol`  | string | Yes      | Currency pair, e.g., EUR/USD    |
| `amount`  | number | Yes      | Amount to convert               |
| `apikey`  | string | Yes*     | Required unless via Auth header |

**Sample Request:**
```
GET https://api.twelvedata.com/currency_conversion?symbol=EUR/USD&amount=100&apikey=YOUR_API_KEY
```

**Sample Response:**
```json
{
  "symbol": "EUR/USD",
  "rate": 1.0862,
  "amount": 108.62
}
```

### 6.7 Market Movers — `GET /market_movers`

Get the list of top gaining or losing stocks today.

- **Credit weight:** 1 credit

| Parameter  | Type   | Required | Description                     |
| ---------- | ------ | -------- | ------------------------------- |
| `exchange` | string | No       | Exchange filter                 |
| `country`  | string | No       | Country filter                  |
| `apikey`   | string | Yes*     | Required unless via Auth header |

**Sample Request:**
```
GET https://api.twelvedata.com/market_movers?exchange=NASDAQ&apikey=YOUR_API_KEY
```

### 6.8 Exchange Status — `GET /exchange_status`

Check the state of all available exchanges, time to open, and time to close.

- **Credit weight:** 1 credit

### 6.9 Logo — `GET /logo`

Returns a logo of company, cryptocurrency, or forex pair.

- **Credit weight:** 1 credit

| Parameter | Type   | Required | Description                     |
| --------- | ------ | -------- | ------------------------------- |
| `symbol`  | string | Yes      | Instrument ticker               |
| `apikey`  | string | Yes*     | Required unless via Auth header |

---

## 7. Reference / Symbol Catalog Endpoints (9 endpoints)

Catalog endpoints let you discover instruments before requesting data.

### 7.1 Symbol Search — `GET /symbol_search`

Searches the full instrument catalog by name or ticker fragment.

- **Credit weight:** 1 credit

| Parameter    | Type    | Required | Description                     |
| ------------ | ------- | -------- | ------------------------------- |
| `symbol`     | string  | Yes      | Full or partial ticker/name     |
| `outputsize` | integer | No       | Maximum number of matches       |
| `apikey`     | string  | Yes*     | Required unless via Auth header |

**Sample Request:**
```
GET https://api.twelvedata.com/symbol_search?symbol=apple&apikey=YOUR_API_KEY
```

**Sample Response:**
```json
{
  "data": [
    {
      "symbol": "AAPL",
      "instrument_name": "Apple Inc",
      "exchange": "NASDAQ",
      "country": "United States",
      "type": "Common Stock"
    }
  ]
}
```

### 7.2 Stocks List — `GET /stocks`

Returns a daily-updated master list of available stock symbols.

- **Credit weight:** 1 credit

| Parameter   | Type   | Required | Description                       |
| ----------- | ------ | -------- | --------------------------------- |
| `symbol`    | string | No       | Filter by specific ticker         |
| `exchange`  | string | No       | Filter by exchange                |
| `country`   | string | No       | Filter by country                 |
| `figi_code` | string | No       | Filter by FIGI (Ultra/Enterprise) |
| `apikey`    | string | Yes*     | Required unless via Auth header   |

### 7.3 Forex Pairs — `GET /forex_pairs`

Returns array of forex pairs available at Twelve Data API.

- **Credit weight:** 1 credit

### 7.4 Cryptocurrencies — `GET /cryptocurrencies`

Returns array of cryptocurrencies available at Twelve Data API.

- **Credit weight:** 1 credit

### 7.5 ETFs List — `GET /etfs`

Returns array of ETFs available at Twelve Data API.

- **Credit weight:** 1 credit

### 7.6 Indices List — `GET /indices`

Returns array of indices available at Twelve Data API.

- **Credit weight:** 1 credit

### 7.7 Exchanges — `GET /exchanges`

Returns exchanges details and trading hours.

- **Credit weight:** 1 credit

### 7.8 Cryptocurrency Exchanges — `GET /crypto_exchanges`

Returns cryptocurrency exchanges details.

- **Credit weight:** 1 credit

### 7.9 Instrument Type — `GET /instrument_type`

Returns list of all instrument types.

- **Credit weight:** 1 credit

---

## 8. Technical Indicators (98 endpoints)

Twelve Data supports **98 technical indicators**. Each is exposed as its own endpoint, sharing time-series-style parameters plus indicator-specific tuning parameters.

### Common Parameters for All Indicator Endpoints

| Parameter     | Type    | Required | Description                                                  |
| ------------- | ------- | -------- | ------------------------------------------------------------ |
| `symbol`      | string  | Yes      | Instrument ticker                                            |
| `interval`    | string  | Yes      | 1min, 5min, 15min, 30min, 45min, 1h, 2h, 4h, 8h, 1day, 1week, 1month |
| `time_period` | integer | No       | Lookback window (indicator-specific default)                 |
| `series_type` | string  | No       | close (default), open, high, or low                          |
| `outputsize`  | integer | No       | Number of values to return, default 30                       |
| `apikey`      | string  | Yes*     | Required unless via Auth header                              |

### 8.1 Relative Strength Index — `GET /rsi`

Calculates the RSI momentum oscillator.

**Sample Request:**
```
GET https://api.twelvedata.com/rsi?symbol=AAPL&interval=1day&time_period=14&apikey=YOUR_API_KEY
```

**Sample Response:**
```json
{
  "meta": {
    "symbol": "AAPL",
    "interval": "1day",
    "indicator": {
      "name": "RSI",
      "series_type": "close",
      "time_period": 14
    }
  },
  "values": [
    {
      "datetime": "2026-06-30",
      "rsi": "58.32104"
    }
  ],
  "status": "ok"
}
```

### 8.2 Moving Average Convergence Divergence — `GET /macd`

Calculates MACD with configurable fast, slow, and signal periods.

| Parameter      | Type    | Required | Description |
| -------------- | ------- | -------- | ----------- |
| `fastperiod`   | integer | No       | Default 12  |
| `slowperiod`   | integer | No       | Default 26  |
| `signalperiod` | integer | No       | Default 9   |

### 8.3 Bollinger Bands — `GET /bbands`

Calculates Bollinger Bands.

| Parameter     | Type    | Required | Description     |
| ------------- | ------- | -------- | --------------- |
| `time_period` | integer | No       | Default 20      |
| `nbdevup`     | integer | No       | Default 2       |
| `nbdevdn`     | integer | No       | Default 2       |
| `matype`      | integer | No       | Default 0 (SMA) |

### 8.4 Additional Indicators (98 total)

| Endpoint        | Indicator               | Endpoint      | Indicator                 |
| --------------- | ----------------------- | ------------- | ------------------------- |
| `/sma`          | Simple Moving Avg       | `/ema`        | Exponential Moving Avg    |
| `/wma`          | Weighted Moving Avg     | `/dema`       | Double EMA                |
| `/tema`         | Triple EMA              | `/trima`      | Triangular MA             |
| `/kama`         | Kaufman Adaptive MA     | `/mama`       | MESA Adaptive MA          |
| `/t3`           | T3 Moving Average       | `/macd`       | MACD                      |
| `/bbands`       | Bollinger Bands         | `/rsi`        | RSI                       |
| `/stoch`        | Stochastic Oscillator   | `/stochf`     | Stochastic Fast           |
| `/stochrs`      | Stochastic RSI          | `/mom`        | Momentum                  |
| `/roc`          | Rate of Change          | `/rocr`       | Rate of Change Ratio      |
| `/cci`          | Commodity Channel Index | `/atr`        | Average True Range        |
| `/sar`          | Parabolic SAR           | `/adx`        | Average Directional Index |
| `/adxr`         | ADX Rating              | `/apo`        | Absolute Price Oscillator |
| `/ppo`          | Percentage Price Osc    | `/willr`      | Williams %R               |
| `/ultosc`       | Ultimate Oscillator     | `/dx`         | Directional Index         |
| `/minus_di`     | Negative DI             | `/plus_di`    | Positive DI               |
| `/minus_dm`     | Negative DM             | `/plus_dm`    | Positive DM               |
| `/trix`         | TRIX                    | `/macdext`    | MACD Ext                  |
| `/ht_trendline` | Hilbert Trendline       | `/ht_sine`    | Hilbert Sine              |
| `/ht_dcperiod`  | Hilbert DC Period       | `/ht_dcphase` | Hilbert DC Phase          |
| `/ht_phasor`    | Hilbert Phasor          | `/ceil`       | Ceiling                   |
| `/floor`        | Floor                   | `/round`      | Round                     |
| `/avg`          | Average                 | `/max`        | Maximum                   |
| `/min`          | Minimum                 | `/maxindex`   | Max Index                 |
| `/minindex`     | Min Index               | `/exp`        | Exponential               |
| `/var`          | Variance                | `/stddev`     | Standard Deviation        |
| `/asinh`        | Hyperbolic ASINH        | `/atanh`      | Hyperbolic ATANH          |
| ...and 50+ more |                         |               |                           |

---

## 9. Fundamentals Endpoints (17 endpoints)

Fundamentals cover company profiles and financial statements.

### 9.1 Company Profile — `GET /profile`

Returns descriptive company information: sector, industry, employee count, headquarters.

- **Minimum plan:** Grow
- **Credit weight:** ~1–5 credits

| Parameter | Type   | Required | Description                     |
| --------- | ------ | -------- | ------------------------------- |
| `symbol`  | string | Yes      | Instrument ticker               |
| `apikey`  | string | Yes*     | Required unless via Auth header |

### 9.2 Balance Sheet — `GET /balance_sheet`

Returns quarterly and annual balance sheet statements.

- **Minimum plan:** Pro
- **Credit weight:** ~100 credits

| Parameter | Type   | Required | Description                     |
| --------- | ------ | -------- | ------------------------------- |
| `symbol`  | string | Yes      | Instrument ticker               |
| `period`  | string | No       | annual (default) or quarterly   |
| `apikey`  | string | Yes*     | Required unless via Auth header |

### 9.3 Income Statement — `GET /income_statement`

Returns quarterly and annual income statements.

- **Minimum plan:** Pro
- **Credit weight:** ~100 credits

| Parameter | Type   | Required | Description                     |
| --------- | ------ | -------- | ------------------------------- |
| `symbol`  | string | Yes      | Instrument ticker               |
| `period`  | string | No       | annual (default) or quarterly   |
| `apikey`  | string | Yes*     | Required unless via Auth header |

### 9.4 Cash Flow — `GET /cash_flow`

Returns complete cash flow statement.

- **Minimum plan:** Pro
- **Credit weight:** ~100 credits

### 9.5 Earnings — `GET /earnings`

Returns earnings data including EPS estimate and EPS actual.

- **Minimum plan:** Pro

### 9.6 Earnings Estimate — `GET /earnings_estimate`

Returns analysts' estimate for future quarterly and annual EPS.

- **Minimum plan:** Pro

### 9.7 Sales Estimate — `GET /sales_estimate`

Returns analysts' estimate for future quarterly and annual sales.

- **Minimum plan:** Pro

### 9.8 Growth Estimates — `GET /growth_estimates`

Returns consensus analyst estimates over growth rates for various periods.

- **Minimum plan:** Pro

### 9.9 Analyst Recommendations — `GET /recommendations**

Returns average of all analyst recommendations (Strong Buy, Buy, Hold, Sell).

- **Minimum plan:** Pro

### 9.10 Price Target — `GET /price_target**

Returns analysts' projection of a security's future price.

- **Minimum plan:** Pro

### 9.11 Dividends — `GET /dividends**

Returns amount of dividends paid out for the last 10+ years.

- **Minimum plan:** Pro

### 9.12 Splits — `GET /splits**

Returns date and split factor for the last 10+ years.

- **Minimum plan:** Pro

### 9.13 Key Executives — `GET /key_executives**

Returns individuals at the highest level of management.

- **Minimum plan:** Pro

### 9.14 Institutional Holders — `GET /institutional_holders**

Returns amount of stock owned by institutions.

- **Minimum plan:** Enterprise

### 9.15 Mutual Fund Holders — `GET /mutual_fund_holders**

Returns amount of stock owned by mutual fund holders.

- **Minimum plan:** Enterprise

### 9.16 Insider Transactions — `GET /insider_transactions**

Returns trading information performed by insiders.

- **Minimum plan:** Pro

### 9.17 Statistics — `GET /statistics**

Returns current overview of company's main statistics including valuation metrics and financials.

- **Minimum plan:** Pro

---

## 10. Analysis Endpoints (9 endpoints)

Analysis endpoints provide market insights and analytical data.

### 10.1 Analyst Recommendations — `GET /analyst_recommendations`

Returns average of all analyst recommendations.

### 10.2 Price Target — `GET /price_target`

Returns analysts' projection of a security's future price.

### 10.3 IPOs — `GET /ipos`

Returns past, today, or upcoming IPOs.

| Parameter | Type   | Required | Description                    |
| --------- | ------ | -------- | ------------------------------ |
| `date`    | string | No       | Specific date for IPO calendar |

### 10.4 Market Movers — `GET /market_movers_stocks**

Top gaining or losing stocks.

| Parameter  | Type   | Required | Description     |
| ---------- | ------ | -------- | --------------- |
| `exchange` | string | No       | Exchange filter |
| `country`  | string | No       | Country filter  |

### 10.5 Market Cap History — `GET /market_cap**

Historical data on market capitalization over a specified time period.

| Parameter    | Type   | Required | Description       |
| ------------ | ------ | -------- | ----------------- |
| `symbol`     | string | Yes      | Instrument ticker |
| `start_date` | string | No       | Start date        |
| `end_date`   | string | No       | End date          |

### 10.6 Fundamentals Changes — `GET /fundamentals_changes**

Latest changes of fundamental data for efficient credit consumption.

### 10.7 First Available DateTime — `GET /first_datetime**

Returns the first available DateTime for a given instrument at the specific interval.

| Parameter  | Type   | Required | Description       |
| ---------- | ------ | -------- | ----------------- |
| `symbol`   | string | Yes      | Instrument ticker |
| `interval` | string | Yes      | 1min, 5min, etc.  |

### 10.8 Press Releases — `GET /press_releases**

Structured, real-time access to official company press releases.

- **Minimum plan:** Basic

### 10.9 Cross Listings — `GET /cross_listings**

Identifies all exchanges where a particular security is traded.

- **Minimum plan:** Grow

---

## 11. Mutual Funds Endpoints (12 endpoints)

Mutual funds endpoints provide comprehensive fund data.

### 11.1 Mutual Funds List — `GET /mutual_funds**

Returns array of mutual funds available at Twelve Data API.

### 11.2 Mutual Fund Family — `GET /mutual_fund_family**

Returns mutual funds families.

### 11.3 Mutual Fund Type — `GET /mutual_fund_type**

Returns mutual funds types.

### 11.4 Mutual Fund Ratings — `GET /mutual_fund_ratings**

Provides detailed ratings for mutual funds across global markets.

- **Minimum plan:** Ultra

### 11.5 Mutual Fund Holdings — `GET /mutual_fund_holdings**

Returns holdings breakdown for a mutual fund.

### 11.6 Mutual Fund Performance — `GET /mutual_fund_performance**

Returns performance metrics for a mutual fund.

### 11.7 Mutual Fund Expenses — `GET /mutual_fund_expenses**

Returns expense ratios and fee information.

### 11.8 Mutual Fund Managers — `GET /mutual_fund_managers**

Returns fund manager information.

### 11.9 Mutual Fund NAV — `GET /mutual_fund_nav**

Returns Net Asset Value history.

### 11.10 Mutual Fund Turnover — `GET /mutual_fund_turnover**

Returns portfolio turnover ratio.

### 11.11 Mutual Fund Minimums — `GET /mutual_fund_minimums**

Returns minimum investment requirements.

### 11.12 Mutual Fund Morningstar — `GET /mutual_fund_morningstar**

Returns Morningstar ratings and metrics.

---

## 12. Batch Requests

Twelve Data supports two methods for making bulk requests.

### Method 1: Query Parameter Batching (Single Endpoint)

List multiple symbols separated by commas.

```
GET https://api.twelvedata.com/time_series?symbol=MMM,SBIN:NSE,EUR/USD&interval=1day&apikey=YOUR_API_KEY
```

**Compatible endpoints:** `/time_series`, `/quote`, `/price`, `/exchange_rate`, `/currency_conversion`, `/eod`

### Method 2: JSON POST (Multiple Endpoints)

Send multiple distinct requests to different endpoints in a single POST call.

**Request:**
```
POST https://api.twelvedata.com/batch
Authorization: apikey YOUR_API_KEY
Content-Type: application/json
```

```json
{
  "request_1": "/time_series?symbol=AAPL&interval=1min",
  "request_2": "/quote?symbol=TSLA",
  "request_3": "/exchange_rate?symbol=USD/EUR"
}
```

**Response:**
```json
{
  "request_1": { "status": "ok", "data": { ... } },
  "request_2": { "status": "ok", "data": { ... } },
  "request_3": { "status": "error", "message": "Invalid symbol" }
}
```

### Credit Usage for Batch Requests

Each sub-request consumes credits based on the target endpoint. Total credit usage is the sum of all sub-requests. Errors in one request do not affect others.

---

## 13. WebSocket Streaming

For continuous, low-latency price updates.

| Item                | Value                                                        |
| ------------------- | ------------------------------------------------------------ |
| **URL**             | `wss://ws.twelvedata.com/v1/quotes/price?apikey=YOUR_API_KEY` |
| **Minimum plan**    | Pro and above                                                |
| **Max connections** | 3 concurrent per account                                     |
| **Credit model**    | 1 WebSocket credit per symbol                                |

### Step 1: Establish Connection

```
wss://ws.twelvedata.com/v1/quotes/price?apikey=YOUR_API_KEY
```

### Step 2: Subscribe to Data

```json
{
  "action": "subscribe",
  "params": {
    "symbols": "AAPL,TRP,QQQ,EUR/USD,USD/JPY,BTC/USD,ETH/BTC"
  }
}
```

### Step 3: Receive Price Events

**Subscribe Status Event:**
```json
{
  "event": "subscribe-status",
  "status": "ok",
  "message": "Subscribed to 7 symbols"
}
```

**Price Event:**
```json
{
  "event": "price",
  "symbol": "AAPL",
  "currency": "USD",
  "exchange": "NASDAQ",
  "type": "Common Stock",
  "timestamp": 1751500812,
  "price": 214.91,
  "day_volume": 48312990
}
```

### Additional WebSocket Actions

| Action        | Description                                    |
| ------------- | ---------------------------------------------- |
| `subscribe`   | Subscribe to symbols                           |
| `unsubscribe` | Unsubscribe from symbols                       |
| `reset`       | Reset all subscriptions                        |
| `heartbeat`   | Send every 10 seconds to keep connection alive |

### Step 4: Close Connection

Once work is complete, close the connection to avoid wasting WebSocket credits.

---

## 14. Error Handling & Status Codes

Every error uses the same JSON envelope:

```json
{
  "code": 400,
  "message": "Invalid interval provided: 0.99min. Supported intervals: 1min, 5min, 15min, 30min, 45min, 1h, 2h, 4h, 8h, 1day, 1week, 1month",
  "status": "error"
}
```

| HTTP Status                   | Meaning               | Typical Cause                                                | Recommended Action                                        |
| ----------------------------- | --------------------- | ------------------------------------------------------------ | --------------------------------------------------------- |
| **400 Bad Request**           | Invalid parameter     | Malformed symbol, unsupported interval, missing required field | Validate parameters; do not retry unmodified              |
| **401 Unauthorized**          | Authentication failed | Missing, invalid, or expired API key                         | Check apikey value / Authorization header                 |
| **403 Forbidden**             | Plan restriction      | Endpoint/parameter requires higher plan                      | Upgrade plan or remove restricted parameter               |
| **404 Not Found**             | Unknown resource      | Symbol doesn't exist or endpoint path mistyped               | Verify via `/symbol_search`; re-check URL                 |
| **429 Too Many Requests**     | Rate limit exceeded   | Per-minute API credit quota exhausted                        | Back off until next minute; implement exponential backoff |
| **500 Internal Server Error** | Server fault          | Unexpected failure on Twelve Data infrastructure             | Retry with backoff; contact support if persistent         |

---

## 15. Best Practices

1. **Cache aggressively** — Store responses for data that doesn't change every second (fundamentals, EOD prices) to conserve credits.

2. **Batch symbols** — Combine up to ~120 symbols per request where supported.

3. **Monitor headers** — Read `api-credits-left` on every response to throttle before hitting 429.

4. **Use WebSocket for live prices** — If polling `/price` or `/quote` more than a few times per minute, WebSocket is far more credit-efficient.

5. **Keep API key server-side** — Never embed the key in public frontend code.

6. **Handle 429 with backoff** — Use exponential backoff (and jitter) rather than immediate retries.

7. **Check plan gates early** — Confirm which plan an endpoint requires before building features around it.

8. **Use `prepost` parameter wisely** — Available only on Pro+ plans for 1min, 5min, 15min, 30min intervals for US equities.

9. **Send WebSocket heartbeats** — Send every 10 seconds to keep connection alive.

10. **Use `dp` parameter** — Control decimal places (0–11) to reduce response size.

---

*Source: Compiled from Twelve Data's official documentation (twelvedata.com/docs), support center (support.twelvedata.com), and pricing pages, current as of July 2026. Endpoint credit weights and plan gating are subject to change — always confirm against the live documentation before production use.*