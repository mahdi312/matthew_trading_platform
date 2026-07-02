# Alpha Vantage API — Complete Reference Guide

*Comprehensive API documentation covering all 100+ functions across 9 categories*

**Base URL:** `https://www.alphavantage.co/query`

*Prepared July 2026 · Based on official Alpha Vantage documentation (alphavantage.co/documentation)*

---

## Table of Contents

1. [Overview](#1-overview)
2. [Base URL & Authentication](#2-base-url--authentication)
3. [Rate Limits & Subscription Plans](#3-rate-limits--subscription-plans)
4. [Request/Response Conventions](#4-requestresponse-conventions)
5. [Core Stock Time Series APIs](#5-core-stock-time-series-apis)
6. [Index Data APIs](#6-index-data-apis)
7. [US Options Data APIs](#7-us-options-data-apis)
8. [Alpha Intelligence™](#8-alpha-intelligence)
9. [Fundamental Data APIs](#9-fundamental-data-apis)
10. [Physical & Crypto Currencies APIs](#10-physical--crypto-currencies-apis)
11. [Commodities APIs](#11-commodities-apis)
12. [Economic Indicators APIs](#12-economic-indicators-apis)
13. [Technical Indicators APIs (60+ functions)](#13-technical-indicators-apis-60-functions)
14. [Error Handling & Status Codes](#14-error-handling--status-codes)
15. [Best Practices](#15-best-practices)

---

## 1. Overview

Alpha Vantage provides a free REST API delivering real-time and historical market data for stocks, options, forex,
cryptocurrencies, commodities, and economic indicators. The API exposes **100+ functions** across **9 categories**:

| Category                     | Functions |
|------------------------------|-----------|
| Core Time Series Stock Data  | 8         |
| Index Data                   | ~5        |
| US Options Data              | ~5        |
| Alpha Intelligence™          | ~5        |
| Fundamental Data             | 15        |
| Physical & Crypto Currencies | ~8        |
| Commodities                  | ~8        |
| Economic Indicators          | ~10       |
| Technical Indicators         | 60+       |
| **Total**                    | **100+**  |

All endpoints use a **single REST endpoint** with the `function` query parameter specifying the dataset.

---

## 2. Base URL & Authentication

### Base URL

```
https://www.alphavantage.co/query
```

### Authentication

Every request requires an API key passed as a query parameter.

```
https://www.alphavantage.co/query?function=TIME_SERIES_INTRADAY&symbol=IBM&interval=5min&apikey=YOUR_API_KEY
```

### Get Your API Key

Claim your free API key at: https://www.alphavantage.co/support/#api-key

### Demo Key

For quick testing, use `apikey=demo` (limited to certain symbols and functions).

### Security Note

> ⚠️ **Never expose your API key** in client-side JavaScript, mobile apps, or public repositories. Proxy requests
> through your own backend.

---

## 3. Rate Limits & Subscription Plans

### Free Tier Limits

- **5 requests per minute**
- **500 requests per day**
- `outputsize=full` not available for most endpoints

### Premium Plans

Premium memberships unlock:

- Higher rate limits
- `outputsize=full` capability
- Real-time and 15-minute delayed intraday data
- Additional premium endpoints

### Rate Limit Handling

When exceeding the limit, the API returns an error message in the response body. Implement **12-second delays** between
requests for free tier to stay within 5 requests/minute.

---

## 4. Request/Response Conventions

### Request Format

All requests are HTTP GET with query parameters:

```
GET https://www.alphavantage.co/query?function=FUNCTION_NAME&param1=value1&param2=value2&apikey=YOUR_API_KEY
```

### Common Parameters

| Parameter    | Type   | Description                              |
|--------------|--------|------------------------------------------|
| `function`   | string | **Required.** The API function to call   |
| `symbol`     | string | **Required for most.** The ticker symbol |
| `apikey`     | string | **Required.** Your API key               |
| `datatype`   | string | `json` (default) or `csv`                |
| `outputsize` | string | `compact` (latest 100) or `full`         |

### Response Format

- **JSON** is the default response format
- **CSV** available by setting `datatype=csv`
- Financial statements return JSON only

---

## 5. Core Stock Time Series APIs

This suite provides global equity data in 4 temporal resolutions: intraday, daily, weekly, and monthly, with **20+ years
of historical depth**.

### 5.1 Intraday — `TIME_SERIES_INTRADAY`

Returns current and historical intraday OHLCV time series, covering **pre-market and post-market hours** (4:00am to 8:
00pm Eastern Time for US markets).

**API Parameters:**

| Parameter        | Required | Description                                            |
|------------------|----------|--------------------------------------------------------|
| `function`       | Yes      | `TIME_SERIES_INTRADAY`                                 |
| `symbol`         | Yes      | Equity symbol (e.g., `IBM`)                            |
| `interval`       | Yes      | `1min`, `5min`, `15min`, `30min`, `60min`              |
| `adjusted`       | No       | `true` (default, split/dividend adjusted) or `false`   |
| `extended_hours` | No       | `true` (default) or `false`                            |
| `month`          | No       | YYYY-MM format (e.g., `2009-01`) for historical months |
| `outputsize`     | No       | `compact` (100 points) or `full` (30 days)             |
| `datatype`       | No       | `json` or `csv`                                        |
| `entitlement`    | No       | `realtime` or `delayed` (15-min delay)                 |
| `apikey`         | Yes      | Your API key                                           |

**Sample Request:**

```
https://www.alphavantage.co/query?function=TIME_SERIES_INTRADAY&symbol=IBM&interval=5min&apikey=demo
```

**Sample Response:**

```json
{
  "Meta Data": {
    "1. Information": "Intraday (5min) prices, volumes",
    "2. Symbol": "IBM",
    "3. Last Refreshed": "2026-07-02 16:00:00",
    "4. Interval": "5min",
    "5. Output Size": "Compact",
    "6. Time Zone": "US/Eastern"
  },
  "Time Series (5min)": {
    "2026-07-02 16:00:00": {
      "1. open": "145.23",
      "2. high": "145.67",
      "3. low": "144.89",
      "4. close": "145.12",
      "5. volume": "1234567"
    }
  }
}
```

> **Note:** This is a premium endpoint. Realtime and 15-minute delayed data requires a premium membership.

### 5.2 Daily — `TIME_SERIES_DAILY`

Returns daily time series (open, high, low, close, volume) covering 20+ years of historical data.

**API Parameters:**

| Parameter    | Required | Description                      |
|--------------|----------|----------------------------------|
| `function`   | Yes      | `TIME_SERIES_DAILY`              |
| `symbol`     | Yes      | Equity symbol                    |
| `outputsize` | No       | `compact` (100 points) or `full` |
| `datatype`   | No       | `json` or `csv`                  |
| `apikey`     | Yes      | Your API key                     |

**Sample Request:**

```
https://www.alphavantage.co/query?function=TIME_SERIES_DAILY&symbol=IBM&apikey=demo
```

**Sample Response:**

```json
{
  "Meta Data": {
    "1. Information": "Daily Time Series",
    "2. Symbol": "IBM",
    "3. Last Refreshed": "2026-07-02",
    "4. Output Size": "Compact",
    "5. Time Zone": "US/Eastern"
  },
  "Time Series (Daily)": {
    "2026-07-02": {
      "1. open": "144.50",
      "2. high": "146.20",
      "3. low": "143.80",
      "4. close": "145.12",
      "5. volume": "5678901"
    }
  }
}
```

> **Note:** `outputsize=full` requires a premium key.

### 5.3 Daily Adjusted — `TIME_SERIES_DAILY_ADJUSTED`

Returns raw daily values, adjusted close values, and historical split/dividend events.

**API Parameters:**

| Parameter     | Required | Description                  |
|---------------|----------|------------------------------|
| `function`    | Yes      | `TIME_SERIES_DAILY_ADJUSTED` |
| `symbol`      | Yes      | Equity symbol                |
| `outputsize`  | No       | `compact` or `full`          |
| `datatype`    | No       | `json` or `csv`              |
| `entitlement` | No       | `realtime` or `delayed`      |
| `apikey`      | Yes      | Your API key                 |

**Sample Request:**

```
https://www.alphavantage.co/query?function=TIME_SERIES_DAILY_ADJUSTED&symbol=IBM&apikey=demo
```

### 5.4 Weekly — `TIME_SERIES_WEEKLY`

Returns weekly time series (last trading day of each week, weekly open/high/low/close/volume).

**Sample Request:**

```
https://www.alphavantage.co/query?function=TIME_SERIES_WEEKLY&symbol=IBM&apikey=demo
```

### 5.5 Weekly Adjusted — `TIME_SERIES_WEEKLY_ADJUSTED`

Returns weekly time series with adjusted close and dividends.

**Sample Request:**

```
https://www.alphavantage.co/query?function=TIME_SERIES_WEEKLY_ADJUSTED&symbol=IBM&apikey=demo
```

### 5.6 Monthly — `TIME_SERIES_MONTHLY`

Returns monthly time series (last trading day of each month).

### 5.7 Monthly Adjusted — `TIME_SERIES_MONTHLY_ADJUSTED**

Returns monthly time series with adjusted close.

### 5.8 Global Quote — `GLOBAL_QUOTE`

Returns a lightweight real-time quote for a symbol.

**Sample Request:**

```
https://www.alphavantage.co/query?function=GLOBAL_QUOTE&symbol=IBM&apikey=demo
```

**Sample Response:**

```json
{
  "Global Quote": {
    "01. symbol": "IBM",
    "02. open": "144.50",
    "03. high": "146.20",
    "04. low": "143.80",
    "05. price": "145.12",
    "06. volume": "5678901",
    "07. latest trading day": "2026-07-02",
    "08. previous close": "143.90",
    "09. change": "1.22",
    "10. change percent": "0.85%"
  }
}
```

---

## 6. Index Data APIs

Provides time series data for major market indices.

| Function         | Description         |
|------------------|---------------------|
| `INDEX_INTRADAY` | Intraday index data |
| `INDEX_DAILY`    | Daily index data    |
| `INDEX_WEEKLY`   | Weekly index data   |
| `INDEX_MONTHLY`  | Monthly index data  |

**Supported Indices:** S&P 500, NASDAQ, Dow Jones, and more.

**Sample Request:**

```
https://www.alphavantage.co/query?function=INDEX_DAILY&symbol=SPX&apikey=YOUR_API_KEY
```

---

## 7. US Options Data APIs

Provides data for US options contracts.

| Function           | Description           |
|--------------------|-----------------------|
| `OPTIONS_INTRADAY` | Intraday options data |
| `OPTIONS_DAILY`    | Daily options data    |
| `OPTIONS_WEEKLY`   | Weekly options data   |
| `OPTIONS_MONTHLY`  | Monthly options data  |

**Sample Request:**

```
https://www.alphavantage.co/query?function=OPTIONS_DAILY&symbol=SPY&apikey=YOUR_API_KEY
```

---

## 8. Alpha Intelligence™

AI-powered financial intelligence including news and sentiment analysis.

| Function               | Description                         |
|------------------------|-------------------------------------|
| `NEWS_SENTIMENT`       | News articles with sentiment scores |
| `TOP_GAINERS_LOSERS`   | Top market movers                   |
| `INSIDER_TRANSACTIONS` | Insider trading activity            |

**Sample Request:**

```
https://www.alphavantage.co/query?function=NEWS_SENTIMENT&symbol=AAPL&apikey=YOUR_API_KEY
```

---

## 9. Fundamental Data APIs

Comprehensive company financial data including overviews, financial statements, earnings, and dividends.

### 9.1 Company Overview — `OVERVIEW`

Returns descriptive company information: sector, industry, market cap, P/E ratio, dividend yield, etc.

**Sample Request:**

```
https://www.alphavantage.co/query?function=OVERVIEW&symbol=IBM&apikey=demo
```

**Sample Response:**

```json
{
  "Symbol": "IBM",
  "Name": "International Business Machines Corporation",
  "Sector": "Technology",
  "Industry": "Information Technology Services",
  "MarketCapitalization": "145678901234",
  "PERatio": "22.5",
  "DividendYield": "3.2",
  "EPS": "6.45",
  "Description": "International Business Machines Corporation..."
}
```

### 9.2 Income Statement — `INCOME_STATEMENT`

Returns quarterly and annual income statement data showing revenue, expenses, and profit metrics.

**Sample Request:**

```
https://www.alphavantage.co/query?function=INCOME_STATEMENT&symbol=IBM&apikey=demo
```

### 9.3 Balance Sheet — `BALANCE_SHEET`

Returns quarterly and annual balance sheet data.

**Sample Request:**

```
https://www.alphavantage.co/query?function=BALANCE_SHEET&symbol=IBM&apikey=demo
```

### 9.4 Cash Flow — `CASH_FLOW`

Returns quarterly and annual cash flow statements.

**Sample Request:**

```
https://www.alphavantage.co/query?function=CASH_FLOW&symbol=IBM&apikey=demo
```

### 9.5 Earnings — `EARNINGS`

Returns historical earnings data including EPS estimate and actual EPS.

**Sample Request:**

```
https://www.alphavantage.co/query?function=EARNINGS&symbol=IBM&apikey=demo
```

### 9.6 Earnings Estimates — `EARNINGS_ESTIMATES`

Returns analysts' earnings estimates for future quarters.

### 9.7 Dividends — `DIVIDENDS`

Returns dividend history.

### 9.8 Splits — `SPLITS`

Returns stock split history.

### 9.9 Listing Status — `LISTING_STATUS`

Returns current listing status of securities.

### 9.10 Earnings Calendar — `EARNINGS_CALENDAR`

Returns upcoming earnings announcements.

### 9.11 IPO Calendar — `IPO_CALENDAR`

Returns upcoming IPOs.

### 9.12 ETF Profile — `ETF_PROFILE`

Returns ETF profile information.

**Complete List of Fundamentals Functions:**

| Function             | Description         |
|----------------------|---------------------|
| `OVERVIEW`           | Company overview    |
| `INCOME_STATEMENT`   | Income statement    |
| `BALANCE_SHEET`      | Balance sheet       |
| `CASH_FLOW`          | Cash flow statement |
| `EARNINGS`           | Historical earnings |
| `EARNINGS_ESTIMATES` | Earnings estimates  |
| `DIVIDENDS`          | Dividend history    |
| `SPLITS`             | Split history       |
| `LISTING_STATUS`     | Listing status      |
| `EARNINGS_CALENDAR`  | Earnings calendar   |
| `IPO_CALENDAR`       | IPO calendar        |
| `ETF_PROFILE`        | ETF profile         |

---

## 10. Physical & Crypto Currencies APIs

Provides real-time and historical data for forex and cryptocurrencies.

### 10.1 Currency Exchange Rate — `CURRENCY_EXCHANGE_RATE`

Returns the current exchange rate between two currencies.

**Sample Request:**

```
https://www.alphavantage.co/query?function=CURRENCY_EXCHANGE_RATE&from_currency=USD&to_currency=EUR&apikey=demo
```

**Sample Response:**

```json
{
  "Realtime Currency Exchange Rate": {
    "1. From_Currency Code": "USD",
    "2. From_Currency Name": "United States Dollar",
    "3. To_Currency Code": "EUR",
    "4. To_Currency Name": "Euro",
    "5. Exchange Rate": "0.9234",
    "6. Last Refreshed": "2026-07-02 16:00:00",
    "7. Time Zone": "UTC",
    "8. Bid Price": "0.9232",
    "9. Ask Price": "0.9236"
  }
}
```

### 10.2 Forex Intraday — `FX_INTRADAY`

Returns intraday forex time series.

**Sample Request:**

```
https://www.alphavantage.co/query?function=FX_INTRADAY&from_symbol=USD&to_symbol=EUR&interval=5min&apikey=demo
```

### 10.3 Forex Daily — `FX_DAILY`

Returns daily forex time series.

### 10.4 Forex Weekly — `FX_WEEKLY`

Returns weekly forex time series.

### 10.5 Forex Monthly — `FX_MONTHLY`

Returns monthly forex time series.

### 10.6 Digital Currency Intraday — `CRYPTO_INTRADAY`

Returns intraday cryptocurrency time series.

**Sample Request:**

```
https://www.alphavantage.co/query?function=CRYPTO_INTRADAY&symbol=BTC&market=USD&interval=5min&apikey=demo
```

### 10.7 Digital Currency Daily — `DIGITAL_CURRENCY_DAILY`

Returns daily cryptocurrency time series.

### 10.8 Digital Currency Weekly — `DIGITAL_CURRENCY_WEEKLY`

Returns weekly cryptocurrency time series.

### 10.9 Digital Currency Monthly — `DIGITAL_CURRENCY_MONTHLY`

Returns monthly cryptocurrency time series.

### 10.10 Crypto Rating — `CRYPTO_RATING`

Returns cryptocurrency rating/health index.

**Complete List of Forex/Crypto Functions:**

| Function                   | Description             |
|----------------------------|-------------------------|
| `CURRENCY_EXCHANGE_RATE`   | Real-time exchange rate |
| `FX_INTRADAY`              | Intraday forex          |
| `FX_DAILY`                 | Daily forex             |
| `FX_WEEKLY`                | Weekly forex            |
| `FX_MONTHLY`               | Monthly forex           |
| `CRYPTO_INTRADAY`          | Intraday crypto         |
| `DIGITAL_CURRENCY_DAILY`   | Daily crypto            |
| `DIGITAL_CURRENCY_WEEKLY`  | Weekly crypto           |
| `DIGITAL_CURRENCY_MONTHLY` | Monthly crypto          |
| `CRYPTO_RATING`            | Crypto rating           |

---

## 11. Commodities APIs

Provides commodity price data including oil, gas, metals, agriculture, and composite indices.

| Function          | Description               |
|-------------------|---------------------------|
| `WTI`             | WTI Crude Oil prices      |
| `BRENT`           | Brent Crude Oil prices    |
| `NATURAL_GAS`     | Natural Gas prices        |
| `GOLD`            | Gold prices               |
| `SILVER`          | Silver prices             |
| `COPPER`          | Copper prices             |
| `WHEAT`           | Wheat prices              |
| `CORN`            | Corn prices               |
| `COMMODITY_CHAIN` | Commodity composite index |

**Sample Request:**

```
https://www.alphavantage.co/query?function=WTI&interval=weekly&apikey=demo
```

---

## 12. Economic Indicators APIs

Access to key macroeconomic indicators.

| Function               | Description                 |
|------------------------|-----------------------------|
| `REAL_GDP`             | Real GDP                    |
| `REAL_GDP_PER_CAPITA`  | Real GDP per capita         |
| `TREASURY_YIELD`       | Treasury yield              |
| `FEDERAL_FUNDS_RATE`   | Federal funds interest rate |
| `CPI`                  | Consumer Price Index        |
| `INFLATION`            | Inflation rate              |
| `RETAIL_SALES`         | Retail sales                |
| `DURABLE_GOODS_ORDERS` | Durable goods orders        |
| `UNEMPLOYMENT`         | Unemployment rate           |
| `NONFARM_PAYROLL`      | Nonfarm payroll             |

**Sample Request:**

```
https://www.alphavantage.co/query?function=REAL_GDP&apikey=demo
```

---

## 13. Technical Indicators APIs (60+ functions)

Alpha Vantage provides **60+ technical indicators** across multiple categories.

### Common Parameters for All Indicators

| Parameter     | Required | Description                                                             |
|---------------|----------|-------------------------------------------------------------------------|
| `function`    | Yes      | Indicator name (e.g., `SMA`, `RSI`, `MACD`)                             |
| `symbol`      | Yes      | Equity symbol                                                           |
| `interval`    | Yes      | `1min`, `5min`, `15min`, `30min`, `60min`, `daily`, `weekly`, `monthly` |
| `time_period` | Yes      | Number of data points used for calculation                              |
| `series_type` | Yes      | `close`, `open`, `high`, `low`                                          |
| `apikey`      | Yes      | Your API key                                                            |

### 13.1 Trend Indicators

| Function   | Description                                 |
|------------|---------------------------------------------|
| `SMA`      | Simple Moving Average                       |
| `EMA`      | Exponential Moving Average                  |
| `WMA`      | Weighted Moving Average                     |
| `DEMA`     | Double Exponential Moving Average           |
| `TEMA`     | Triple Exponential Moving Average           |
| `TRIMA`    | Triangular Moving Average                   |
| `KAMA`     | Kaufman Adaptive Moving Average             |
| `MAMA`     | MESA Adaptive Moving Average                |
| `T3`       | T3 Moving Average                           |
| `MACD`     | Moving Average Convergence Divergence       |
| `MACDEXT`  | MACD with configurable moving averages      |
| `STOCH`    | Stochastic Oscillator                       |
| `STOCHF`   | Stochastic Fast                             |
| `RSI`      | Relative Strength Index                     |
| `STOCHRSI` | Stochastic RSI                              |
| `WILLR`    | Williams %R                                 |
| `ADX`      | Average Directional Index                   |
| `ADXR`     | Average Directional Movement Index Rating   |
| `APO`      | Absolute Price Oscillator                   |
| `PPO`      | Percentage Price Oscillator                 |
| `MOM`      | Momentum                                    |
| `BOP`      | Balance of Power                            |
| `CCI`      | Commodity Channel Index                     |
| `CMO`      | Chande Momentum Oscillator                  |
| `ROC`      | Rate of Change                              |
| `ROCR`     | Rate of Change Ratio                        |
| `TRIX`     | 1-day Rate of Change of a Triple Smooth EMA |

### 13.2 Volatility Indicators

| Function       | Description                               |
|----------------|-------------------------------------------|
| `BBANDS`       | Bollinger Bands                           |
| `ATR`          | Average True Range                        |
| `NATR`         | Normalized Average True Range             |
| `TRANGE`       | True Range                                |
| `HT_TRENDLINE` | Hilbert Transform Instantaneous Trendline |
| `HT_SINE`      | Hilbert Transform Sine Wave               |
| `HT_DCPERIOD`  | Hilbert Transform Dominant Cycle Period   |
| `HT_DCPHASE`   | Hilbert Transform Dominant Cycle Phase    |
| `HT_PHASOR`    | Hilbert Transform Phasor Components       |

### 13.3 Volume Indicators

| Function | Description            |
|----------|------------------------|
| `OBV`    | On Balance Volume      |
| `MFI`    | Money Flow Index       |
| `AD`     | Chaikin A/D Line       |
| `ADOSC`  | Chaikin A/D Oscillator |

### 13.4 Other Indicators

| Function   | Description                         |
|------------|-------------------------------------|
| `SAR`      | Parabolic SAR                       |
| `ULTOSC`   | Ultimate Oscillator                 |
| `DX`       | Directional Movement Index          |
| `MINUS_DI` | Minus Directional Indicator         |
| `PLUS_DI`  | Plus Directional Indicator          |
| `MINUS_DM` | Minus Directional Movement          |
| `PLUS_DM`  | Plus Directional Movement           |
| `MIDPRICE` | Midpoint Price                      |
| `MIDPOINT` | Midpoint                            |
| `MAVP`     | Moving Average with Variable Period |
| `VAR`      | Variance                            |
| `STDDEV`   | Standard Deviation                  |

### Sample Request — SMA

```
https://www.alphavantage.co/query?function=SMA&symbol=IBM&interval=daily&time_period=20&series_type=close&apikey=demo
```

### Sample Request — RSI

```
https://www.alphavantage.co/query?function=RSI&symbol=IBM&interval=daily&time_period=14&series_type=close&apikey=demo
```

### Sample Request — MACD

```
https://www.alphavantage.co/query?function=MACD&symbol=IBM&interval=daily&series_type=close&apikey=demo
```

### Sample Response — SMA

```json
{
  "Meta Data": {
    "1: Symbol": "IBM",
    "2: Indicator": "Simple Moving Average (SMA)",
    "3: Last Refreshed": "2026-07-02",
    "4: Interval": "daily",
    "5: Time Period": 20,
    "6: Series Type": "close",
    "7: Time Zone": "US/Eastern"
  },
  "Technical Analysis: SMA": {
    "2026-07-02": {
      "SMA": "144.85"
    },
    "2026-07-01": {
      "SMA": "144.32"
    }
  }
}
```

---

## 14. Error Handling & Status Codes

Alpha Vantage **returns errors in the response body, not HTTP status codes**.

### Error Response Format

```json
{
  "Error Message": "Invalid API call. Please retry or visit the documentation (https://www.alphavantage.co/documentation/) for TIME_SERIES_INTRADAY."
}
```

### Common Errors

| Error Type            | Description                             | Resolution                         |
|-----------------------|-----------------------------------------|------------------------------------|
| `Invalid API call`    | Malformed request or invalid parameters | Check function name and parameters |
| `Invalid symbol`      | Symbol not found                        | Verify symbol via search           |
| `Rate limit exceeded` | Too many requests                       | Wait and retry with backoff        |
| `Missing API key`     | No apikey provided                      | Add `&apikey=YOUR_KEY`             |
| `Invalid API key`     | Key is invalid or expired               | Get a new key                      |

### Rate Limit Error Handling

```python
import time
import requests

def call_alpha_vantage(params):
    response = requests.get('https://www.alphavantage.co/query', params=params)
    data = response.json()
    
    if 'Error Message' in data and 'rate limit' in data['Error Message'].lower():
        time.sleep(60)  # Wait a minute
        return call_alpha_vantage(params)
    
    return data
```

---

## 15. Best Practices

### 1. Respect Rate Limits

Free tier: **5 requests per minute, 500 per day**. Implement **12-second delays** between requests.

```python
import time
time.sleep(12)  # Between each request
```

### 2. Use `compact` Output Size

Use `outputsize=compact` (100 data points) instead of `full` to reduce response size and latency.

### 3. Cache Responses

Cache data that doesn't change frequently (fundamentals, EOD prices) to conserve API calls.

### 4. Use CSV for Large Data

For large datasets, use `datatype=csv` to reduce response size and parsing overhead.

### 5. Batch Requests

Alpha Vantage does not support native batching. Queue multiple requests with appropriate delays.

### 6. Premium Upgrade for `full`

`outputsize=full` and real-time intraday data require a premium subscription.

### 7. Error Handling

Always check for `"Error Message"` in responses and implement retry logic with exponential backoff.

### 8. Keep API Key Server-Side

Never expose your API key in client-side code. Proxy requests through your backend.

### 9. Monitor Usage

Track your request count to avoid hitting daily limits.

---

## Quick Reference: All Functions by Category

### Core Stock Time Series (8)

- `TIME_SERIES_INTRADAY`
- `TIME_SERIES_DAILY`
- `TIME_SERIES_DAILY_ADJUSTED`
- `TIME_SERIES_WEEKLY`
- `TIME_SERIES_WEEKLY_ADJUSTED`
- `TIME_SERIES_MONTHLY`
- `TIME_SERIES_MONTHLY_ADJUSTED`
- `GLOBAL_QUOTE`

### Index Data (~5)

- `INDEX_INTRADAY`
- `INDEX_DAILY`
- `INDEX_WEEKLY`
- `INDEX_MONTHLY`

### US Options (~5)

- `OPTIONS_INTRADAY`
- `OPTIONS_DAILY`
- `OPTIONS_WEEKLY`
- `OPTIONS_MONTHLY`

### Alpha Intelligence (~5)

- `NEWS_SENTIMENT`
- `TOP_GAINERS_LOSERS`
- `INSIDER_TRANSACTIONS`

### Fundamental Data (12)

- `OVERVIEW`
- `INCOME_STATEMENT`
- `BALANCE_SHEET`
- `CASH_FLOW`
- `EARNINGS`
- `EARNINGS_ESTIMATES`
- `DIVIDENDS`
- `SPLITS`
- `LISTING_STATUS`
- `EARNINGS_CALENDAR`
- `IPO_CALENDAR`
- `ETF_PROFILE`

### Physical & Crypto Currencies (10)

- `CURRENCY_EXCHANGE_RATE`
- `FX_INTRADAY`
- `FX_DAILY`
- `FX_WEEKLY`
- `FX_MONTHLY`
- `CRYPTO_INTRADAY`
- `DIGITAL_CURRENCY_DAILY`
- `DIGITAL_CURRENCY_WEEKLY`
- `DIGITAL_CURRENCY_MONTHLY`
- `CRYPTO_RATING`

### Commodities (~9)

- `WTI`, `BRENT`, `NATURAL_GAS`, `GOLD`, `SILVER`, `COPPER`, `WHEAT`, `CORN`, `COMMODITY_CHAIN`

### Economic Indicators (~10)

- `REAL_GDP`, `REAL_GDP_PER_CAPITA`, `TREASURY_YIELD`, `FEDERAL_FUNDS_RATE`, `CPI`, `INFLATION`, `RETAIL_SALES`,
  `DURABLE_GOODS_ORDERS`, `UNEMPLOYMENT`, `NONFARM_PAYROLL`

### Technical Indicators (60+)

- See Section 13 for complete list

---

*Source: Compiled from Alpha Vantage's official documentation (alphavantage.co/documentation) and support pages, current
as of July 2026. Functions and parameters are subject to change — always confirm against the live documentation before
production use.*