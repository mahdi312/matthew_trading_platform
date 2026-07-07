Here is the complete, detailed documentation for **all free, publicly accessible HTTP APIs** for Yahoo Finance.

> **⚠️ CRITICAL DISCLAIMER:**  
> Yahoo **does not** provide an official free API. The endpoints below are reverse-engineered from Yahoo's internal web
> services. They require **no API key** but are **highly unstable**, have **strict rate-limiting**, and **frequently
change or break**. **DO NOT use these for live trade execution**—use them strictly for research, screening, or
> backtesting.

---

## 1. Base Domains & Authentication

All free endpoints use the following base domains:

| Domain                     | Purpose                                  |
|:---------------------------|:-----------------------------------------|
| `query1.finance.yahoo.com` | Chart data, historical downloads, search |
| `query2.finance.yahoo.com` | Quote summaries and fundamentals         |

### Authentication / Headers

**Auth:** **NONE.** No API keys, no OAuth, no tokens.

**Required Headers (MUST include to avoid 403 blocks):**

```http
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36
Accept: application/json, text/plain, */*
Accept-Language: en-US,en;q=0.9
Origin: https://finance.yahoo.com
Referer: https://finance.yahoo.com/
```

> **Cookie Note:** Occasionally, Yahoo requires a session cookie (`B` or `A1`). If you get a `401`, fetch a fresh cookie
> by visiting `https://finance.yahoo.com` in a browser and copying the cookie string, or use a session library that
> handles it automatically (e.g., `requests.Session` in Python).

---

## 2. Endpoint 1: OHLCV Chart Data (JSON)

**Best for:** Price charts, real-time (delayed) candles, intraday data.

**Endpoint:**

```
GET https://query1.finance.yahoo.com/v8/finance/chart/{SYMBOL}
```

### Query Parameters

| Parameter        | Type    | Default   | Options                                                         |
|:-----------------|:--------|:----------|:----------------------------------------------------------------|
| `interval`       | string  | `1d`      | `1m`, `2m`, `5m`, `15m`, `30m`, `60m`, `1d`, `1wk`, `1mo`       |
| `range`          | string  | `1mo`     | `1d`, `5d`, `1mo`, `3mo`, `6mo`, `1y`, `2y`, `5y`, `10y`, `max` |
| `includePrePost` | boolean | `false`   | `true` / `false` (includes pre/post market data)                |
| `events`         | string  | `history` | `history`, `div`, `split` (use `                                ||` to combine)              |

### Request Example (cURL)

```bash
curl --request GET \
  --url 'https://query1.finance.yahoo.com/v8/finance/chart/AAPL?interval=1d&range=1mo' \
  --header 'User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36' \
  --header 'Accept: application/json'
```

### Response Body (200 OK)

The response is deeply nested. Here is the fully mapped structure:

```json
{
  "chart": {
    "result": [
      {
        "meta": {
          "currency": "USD",
          "symbol": "AAPL",
          "exchangeName": "NMS",
          "instrumentType": "EQUITY",
          "firstTradeDate": 345479400,
          "regularMarketTime": 1720454400,
          "gmtoffset": -14400,
          "timezone": "EDT",
          "exchangeTimezoneName": "America/New_York",
          "regularMarketPrice": 227.82,
          "chartPreviousClose": 226.34,
          "priceHint": 2,
          "currentTradingPeriod": {
            "pre": {
              "timezone": "EDT",
              "start": 1720449000,
              "end": 1720468800,
              "gmtoffset": -14400
            },
            "regular": {
              "timezone": "EDT",
              "start": 1720468800,
              "end": 1720492200,
              "gmtoffset": -14400
            },
            "post": {
              "timezone": "EDT",
              "start": 1720492200,
              "end": 1720504800,
              "gmtoffset": -14400
            }
          },
          "dataGranularity": "1d",
          "range": "1mo",
          "validRanges": [
            "1d",
            "5d",
            "1mo",
            "3mo",
            "6mo",
            "1y",
            "2y",
            "5y",
            "max"
          ]
        },
        "timestamp": [
          1720449000,
          1720535400,
          1720621800
        ],
        "indicators": {
          "quote": [
            {
              "open": [
                226.34,
                227.01,
                228.50
              ],
              "high": [
                228.12,
                229.45,
                230.10
              ],
              "low": [
                225.78,
                226.50,
                227.90
              ],
              "close": [
                227.82,
                228.75,
                229.88
              ],
              "volume": [
                45678900,
                52340000,
                48900000
              ]
            }
          ],
          "adjclose": [
            {
              "adjclose": [
                227.82,
                228.75,
                229.88
              ]
            }
          ]
        }
      }
    ],
    "error": null
  }
}
```

### Field Mapping for Trading Platforms

| JSON Path                         | Description                                    |
|:----------------------------------|:-----------------------------------------------|
| `meta.regularMarketPrice`         | Latest current price                           |
| `meta.chartPreviousClose`         | Previous day's close                           |
| `timestamp`                       | Array of Unix timestamps (seconds)             |
| `indicators.quote[0].open`        | Array of open prices                           |
| `indicators.quote[0].high`        | Array of high prices                           |
| `indicators.quote[0].low`         | Array of low prices                            |
| `indicators.quote[0].close`       | Array of close prices                          |
| `indicators.quote[0].volume`      | Array of volumes                               |
| `indicators.adjclose[0].adjclose` | Array of adjusted closes (use for backtesting) |

---

## 3. Endpoint 2: Historical Data (CSV Download)

**Best for:** Bulk downloading daily/weekly/monthly historical data in CSV format.

**Endpoint:**

```
GET https://query1.finance.yahoo.com/v7/finance/download/{SYMBOL}
```

### Query Parameters

| Parameter              | Type    | Required | Description                                                    |
|:-----------------------|:--------|:---------|:---------------------------------------------------------------|
| `period1`              | integer | **Yes**  | Start timestamp (Unix epoch in **seconds**)                    |
| `period2`              | integer | **Yes**  | End timestamp (Unix epoch in **seconds**)                      |
| `interval`             | string  | **Yes**  | `1d`, `1wk`, `1mo`                                             |
| `events`               | string  | No       | `history` (default), `div` (dividends), `split` (stock splits) |
| `includeAdjustedClose` | boolean | No       | `true` / `false` (default is `true`)                           |

### Request Example (cURL)

```bash
# Get AAPL from July 1, 2026 to July 8, 2026
curl --request GET \
  --url 'https://query1.finance.yahoo.com/v7/finance/download/AAPL?period1=1751299200&period2=1751904000&interval=1d&events=history' \
  --header 'User-Agent: Mozilla/5.0' \
  --header 'Accept: text/csv'
```

### Response Body (200 OK)

**Content-Type:** `text/csv`

```csv
Date,Open,High,Low,Close,Adj Close,Volume
2026-07-01,225.50,228.10,224.80,227.82,227.82,45678900
2026-07-02,227.82,229.45,226.50,228.75,228.75,52340000
2026-07-03,228.75,230.10,227.90,229.88,229.88,48900000
...
```

---

## 4. Endpoint 3: Quote Summary (Fundamentals & Live Stats)

**Best for:** Getting current bid/ask, market cap, P/E, dividend yield, and company info.

**Endpoint:**

```
GET https://query2.finance.yahoo.com/v10/finance/quoteSummary/{SYMBOL}
```

### Query Parameters

| Parameter | Type   | Default | Options                                                                                                                                                                                                    |
|:----------|:-------|:--------|:-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `modules` | string | all     | `price`, `summaryDetail`, `defaultKeyStatistics`, `assetProfile`, `incomeStatementHistory`, `balanceSheetHistory`, `cashflowStatementHistory`, `earnings`, `financialData`, `calendarEvents`, `secFilings` |

> **Tip:** Use `modules=price,summaryDetail,defaultKeyStatistics` to reduce payload size and avoid 429 errors.

### Request Example

```bash
curl --request GET \
  --url 'https://query2.finance.yahoo.com/v10/finance/quoteSummary/AAPL?modules=price,summaryDetail' \
  --header 'User-Agent: Mozilla/5.0' \
  --header 'Accept: application/json'
```

### Response Body (200 OK)

```json
{
  "quoteSummary": {
    "result": [
      {
        "price": {
          "regularMarketPrice": {
            "fmt": "227.82",
            "raw": 227.82
          },
          "regularMarketChange": {
            "fmt": "+1.48",
            "raw": 1.48
          },
          "regularMarketChangePercent": {
            "fmt": "+0.65%",
            "raw": 0.0065
          },
          "regularMarketDayHigh": {
            "fmt": "230.10",
            "raw": 230.10
          },
          "regularMarketDayLow": {
            "fmt": "224.80",
            "raw": 224.80
          },
          "regularMarketVolume": {
            "fmt": "45,678,900",
            "raw": 45678900
          },
          "bid": {
            "fmt": "227.80",
            "raw": 227.80
          },
          "ask": {
            "fmt": "227.85",
            "raw": 227.85
          },
          "marketCap": {
            "fmt": "3.12T",
            "raw": 3120000000000
          },
          "currency": "USD",
          "exchangeName": "NMS"
        },
        "summaryDetail": {
          "previousClose": {
            "fmt": "226.34",
            "raw": 226.34
          },
          "open": {
            "fmt": "225.50",
            "raw": 225.50
          },
          "volume": {
            "fmt": "45,678,900",
            "raw": 45678900
          },
          "averageVolume": {
            "fmt": "52,340,000",
            "raw": 52340000
          },
          "peRatio": {
            "fmt": "28.50",
            "raw": 28.50
          },
          "dividendYield": {
            "fmt": "0.45%",
            "raw": 0.0045
          },
          "dividendRate": {
            "fmt": "1.02",
            "raw": 1.02
          },
          "beta": {
            "fmt": "1.23",
            "raw": 1.23
          }
        }
      }
    ],
    "error": null
  }
}
```

**Always use `raw` (number) for calculations; use `fmt` (string) for display only.**

---

## 5. Endpoint 4: Search / Ticker Autocomplete

**Best for:** Finding the correct symbol for a company name.

**Endpoint:**

```
GET https://query1.finance.yahoo.com/v1/finance/search
```

### Query Parameters

| Parameter     | Type    | Required | Description                                   |
|:--------------|:--------|:---------|:----------------------------------------------|
| `q`           | string  | **Yes**  | Search query (e.g., `"Apple"`, `"Microsoft"`) |
| `quotesCount` | integer | No       | Number of ticker results (default: 6)         |
| `newsCount`   | integer | No       | Number of news results (default: 0)           |

### Request Example

```bash
curl --request GET \
  --url 'https://query1.finance.yahoo.com/v1/finance/search?q=Apple&quotesCount=5' \
  --header 'User-Agent: Mozilla/5.0'
```

### Response Body (200 OK)

```json
{
  "quotes": [
    {
      "symbol": "AAPL",
      "shortname": "Apple Inc.",
      "longname": "Apple Inc.",
      "exchange": "NMS",
      "quoteType": "EQUITY",
      "score": 1.0
    },
    {
      "symbol": "AAPL34.SA",
      "shortname": "Apple Inc.",
      "exchange": "SAO",
      "quoteType": "EQUITY"
    }
  ],
  "news": []
}
```

---

## 6. Full Error Handling Reference

These endpoints return standard HTTP status codes. **Beware of HTML responses**—if you get HTML, you've been
rate-limited.

| HTTP Code     | Body / Response                                           | Meaning                                                                | Platform Action                                                  |
|:--------------|:----------------------------------------------------------|:-----------------------------------------------------------------------|:-----------------------------------------------------------------|
| **200**       | JSON or CSV                                               | Success.                                                               | Process data.                                                    |
| **400**       | `{"error": "Bad Request"}`                                | Invalid symbol or malformed timestamp.                                 | Validate input.                                                  |
| **401**       | `Unauthorized`                                            | Missing or expired cookie/session.                                     | Fetch a fresh cookie from finance.yahoo.com.                     |
| **404**       | `{"error": "Not Found"}`                                  | Symbol does not exist (e.g., `XYZ123`).                                | Invalidate ticker; alert user.                                   |
| **429**       | `{"error": "Rate limit exceeded"}` or **empty HTML page** | Too many requests. Yahoo aggressively limits ~100 requests per 15 min. | **Exponential backoff** (wait 5s, 10s, 30s). Cache aggressively. |
| **500 / 503** | `{"error": "Service unavailable"}`                        | Yahoo upstream is down or updating.                                    | Retry with circuit breaker (max 3 retries).                      |

---

## 7. Request Throttling & Best Practices (Critical for Free APIs)

Because these are **unauthorized** endpoints, Yahoo treats them as web scraping and bans IPs quickly. Follow these rules
strictly:

| Rule                    | Specification                                                                                                                 |
|:------------------------|:------------------------------------------------------------------------------------------------------------------------------|
| **Max Requests**        | **≤ 1 request per 2 seconds** per IP (30 req/min)                                                                             |
| **Burst Limit**         | **≤ 5 concurrent requests**                                                                                                   |
| **Batch Strategy**      | For multiple symbols, make sequential requests with a **1-second delay** between them.                                        |
| **Cache Policy**        | Cache 1-min intraday data for **30 seconds**. Cache daily data for **24 hours** (only fetch once per day after market close). |
| **User-Agent Rotation** | Rotate between 3–5 modern Chrome/Firefox User-Agent strings to avoid fingerprinting.                                          |

### Recommended Retry Logic (Exponential Backoff with Jitter)

```
Retry 1: wait 2s
Retry 2: wait 4s
Retry 3: wait 8s
Retry 4: wait 16s
Max Retries: 5
If all fail: Mark symbol as "unavailable" and resume.
```

---

## 8. Summary: Which Endpoint to Use?

| Use Case                                | Recommended Endpoint                                                       |
|:----------------------------------------|:---------------------------------------------------------------------------|
| **Real-time price / bid/ask**           | `/v10/finance/quoteSummary` (modules=`price`)                              |
| **Intraday chart (1m, 5m, 15m)**        | `/v8/finance/chart` (interval=`1m`/`5m`)                                   |
| **Daily historical OHLC**               | `/v8/finance/chart` (interval=`1d`) OR `/v7/finance/download` for CSV      |
| **Fundamentals (P/E, Div, Market Cap)** | `/v10/finance/quoteSummary` (modules=`summaryDetail,defaultKeyStatistics`) |
| **Ticker search**                       | `/v1/finance/search`                                                       |

---

## 9. Final Production Warning

| Issue                         | Impact                                                                                                       |
|:------------------------------|:-------------------------------------------------------------------------------------------------------------|
| **15-Minute Delay**           | US equity quotes are delayed by 15 minutes unless you pay for a premium feed.                                |
| **No WebSocket**              | You must poll continuously—no streaming tick data.                                                           |
| **Frequent Breaking Changes** | Yahoo changes JSON key names and CSV formats 2–4 times per year. Your parser **will break** without warning. |
| **IP Bans**                   | Exceeding 1 req/2s will get your IP temporarily banned (usually for 10–60 minutes).                          |

