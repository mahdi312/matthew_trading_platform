# Finnhub API — Complete Reference Documentation

This is the **complete** guide to the Finnhub financial data API. It covers every publicly available endpoint, including
real‑time quotes, candles, company fundamentals, technical indicators, economic data, alternative data, and WebSocket
streaming.

> **Important:** Finnhub is a **data‑only** API. It does **not** handle order execution, account management, or trading.
> All data is read‑only.

---

## Base Information

| Item               | Value                               |
|--------------------|-------------------------------------|
| **Base URL**       | `https://finnhub.io/api/v1`         |
| **WebSocket**      | `wss://ws.finnhub.io`               |
| **API Version**    | v1 (stable)                         |
| **Data Formats**   | JSON (REST) / JSON (WebSocket)      |
| **Authentication** | API key (via header or query param) |

---

## Authentication

Two ways to provide your API key:

- **HTTP Header** (recommended):  
  `X-Finnhub-Token: YOUR_API_KEY`
- **Query Parameter** (for simplicity):  
  `?token=YOUR_API_KEY`

**Example**

```bash
curl "https://finnhub.io/api/v1/quote?symbol=AAPL" -H "X-Finnhub-Token: YOUR_API_KEY"
```

---

## HTTP Status Codes

| Code | Meaning                                                                       |
|------|-------------------------------------------------------------------------------|
| 200  | OK – successful                                                               |
| 400  | Bad Request – invalid symbol, missing parameter, wrong date format            |
| 401  | Unauthorized – invalid or missing API key                                     |
| 403  | Forbidden – not enough permissions (e.g., free tier tries a premium endpoint) |
| 404  | Not Found – endpoint or symbol does not exist                                 |
| 429  | Too Many Requests – rate limit exceeded                                       |
| 500  | Internal Server Error – try again later                                       |

**Standard error payload:**

```json
{
  "error": "Human‑readable message"
}
```

---

## Rate Limits

| Tier    | Requests / minute        |
|---------|--------------------------|
| Free    | 60                       |
| Premium | varies (check your plan) |

**Handling 429:** implement exponential backoff (start with 1s, double each retry up to 60s).

---

## REST Endpoints Overview

| Category             | Endpoint Prefix                                         |
|----------------------|---------------------------------------------------------|
| Stock Market Data    | `/quote`, `/stock/*`                                    |
| Forex (FX)           | `/forex/*`                                              |
| Cryptocurrency       | `/crypto/*`                                             |
| News & Sentiment     | `/news`, `/company-news`, `/news-sentiment`             |
| Fundamentals         | `/stock/*` (profile, earnings, dividends, splits, etc.) |
| Financial Reports    | `/financials/*`, `/financials/reported/*`               |
| Metrics & Ratios     | `/metrics`, `/ratios`                                   |
| Technical Indicators | `/indicator/*`, `/pattern`, `/scan/*`                   |
| Economic Data        | `/economic`, `/economic/code`                           |
| Alternative Data     | Insider, ownership, ESG, senate trading, etc.           |
| Calendar             | `/calendar/*` (IPO, earnings, dividends, splits, etc.)  |
| Mutual Funds / ETFs  | `/etf/*`, `/fund/*`, `/stock/etf-holdings`              |
| Indices              | `/index/*`                                              |
| Webhooks             | `/webhook/*`                                            |

We cover **every** endpoint below with full request/response examples.

---

## 1. Stock Market Data

### 1.1 Quote — Real‑time Price

```http
GET /quote?symbol={symbol}
```

| Parameter | Required | Description                 |
|-----------|----------|-----------------------------|
| `symbol`  | yes      | Stock ticker (e.g., `AAPL`) |

**Response fields**

| Field | Meaning                  |
|-------|--------------------------|
| `c`   | Current price            |
| `h`   | High price of the day    |
| `l`   | Low price of the day     |
| `o`   | Open price of the day    |
| `pc`  | Previous close price     |
| `t`   | Timestamp (UNIX seconds) |

**Example request**

```bash
curl "https://finnhub.io/api/v1/quote?symbol=AAPL" -H "X-Finnhub-Token: YOUR_KEY"
```

**Example response**

```json
{
  "c": 261.74,
  "h": 263.31,
  "l": 260.68,
  "o": 261.07,
  "pc": 259.45,
  "t": 1582641000
}
```

**Errors**

- `400` – invalid symbol
- `401` – missing/incorrect key
- `429` – rate limit

---

### 1.2 Stock Candles (OHLCV)

```http
GET /stock/candle?symbol={symbol}&resolution={resolution}&from={from}&to={to}
```

| Parameter    | Required | Description                                                                |
|--------------|----------|----------------------------------------------------------------------------|
| `symbol`     | yes      | Ticker symbol                                                              |
| `resolution` | yes      | `1`, `5`, `15`, `30`, `60` (min), `D` (daily), `W` (weekly), `M` (monthly) |
| `from`       | yes      | Start UNIX timestamp                                                       |
| `to`         | yes      | End UNIX timestamp                                                         |

**Response fields**

| Field | Description                |
|-------|----------------------------|
| `c`   | Close prices (array)       |
| `h`   | High prices                |
| `l`   | Low prices                 |
| `o`   | Open prices                |
| `t`   | Timestamps (UNIX)          |
| `v`   | Volumes                    |
| `s`   | Status (`ok` or `no_data`) |

**Example request**

```bash
curl "https://finnhub.io/api/v1/stock/candle?symbol=AAPL&resolution=D&from=1590988249&to=1591852249" -H "X-Finnhub-Token: YOUR_KEY"
```

**Example response**

```json
{
  "c": [
    217.68,
    221.03,
    219.89
  ],
  "h": [
    222.49,
    221.5,
    220.94
  ],
  "l": [
    217.19,
    217.1402,
    218.83
  ],
  "o": [
    221.03,
    218.55,
    220.0
  ],
  "s": "ok",
  "t": [
    1569297600,
    1569384000,
    1569470400
  ],
  "v": [
    33413220,
    24018876,
    20710632
  ]
}
```

**Notes**

- Daily data is adjusted for splits.
- Intraday data (≤1h) is unadjusted.
- You can request up to 1 year of daily data; intraday limited to ~1 month.

---

### 1.3 Company Profile

```http
GET /stock/profile2?symbol={symbol}
```

**Example request**

```bash
curl "https://finnhub.io/api/v1/stock/profile2?symbol=AAPL" -H "X-Finnhub-Token: YOUR_KEY"
```

**Response**

```json
{
  "country": "US",
  "currency": "USD",
  "exchange": "NASDAQ NMS - GLOBAL MARKET",
  "ipo": "1980-12-12",
  "marketCapitalization": 2000000000000,
  "name": "Apple Inc",
  "phone": "14089961010",
  "shareOutstanding": 17000000000,
  "ticker": "AAPL",
  "weburl": "https://www.apple.com/",
  "logo": "https://static.finnhub.io/logo/...",
  "finnhubIndustry": "Technology"
}
```

---

### 1.4 Peers

```http
GET /stock/peers?symbol={symbol}
```

Returns list of peer symbols.

**Example response**

```json
[
  "AAPL",
  "MSFT",
  "GOOGL",
  "AMZN",
  "META"
]
```

---

### 1.5 List of Stock Symbols

```http
GET /stock/symbol?exchange={exchange}
```

| Parameter  | Required | Description                                  |
|------------|----------|----------------------------------------------|
| `exchange` | yes      | Exchange code (e.g., `US`, `NASDAQ`, `NYSE`) |

Returns all symbols on that exchange.

**Example**

```json
[
  {
    "description": "APPLE INC",
    "displaySymbol": "AAPL",
    "symbol": "AAPL",
    "type": "Common Stock"
  }
]
```

---

### 1.6 Symbol Search

```http
GET /search?q={query}
```

**Example**  
`/search?q=apple` returns matching symbols and their basic info.

---

### 1.7 Dividends

```http
GET /stock/dividend?symbol={symbol}&from={from}&to={to}
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `symbol`  | yes      | Ticker      |
| `from`    | yes      | YYYY-MM-DD  |
| `to`      | yes      | YYYY-MM-DD  |

**Example response**

```json
[
  {
    "symbol": "AAPL",
    "date": "2024-02-09",
    "amount": 0.24,
    "adjustedAmount": 0.24,
    "payDate": "2024-02-15",
    "recordDate": "2024-02-12",
    "declarationDate": "2024-02-01"
  }
]
```

---

### 1.8 Stock Splits

```http
GET /stock/split?symbol={symbol}&from={from}&to={to}
```

Returns list of split events with `fromFactor`, `toFactor`.

---

### 1.9 Earnings Surprises

```http
GET /stock/earnings?symbol={symbol}&limit={limit}
```

| Parameter | Required | Description                        |
|-----------|----------|------------------------------------|
| `symbol`  | yes      | Ticker                             |
| `limit`   | no       | Max number of quarters (default 5) |

**Response**

```json
[
  {
    "actual": 1.52,
    "estimate": 1.42,
    "quarter": 1,
    "year": 2024,
    "surprise": 0.10,
    "surprisePercent": 7.04
  }
]
```

---

### 1.10 Recommendation Trends

```http
GET /stock/recommendation?symbol={symbol}
```

Returns analyst ratings (strong buy, buy, hold, sell, strong sell) for each period.

---

### 1.11 Price Target

```http
GET /stock/price-target?symbol={symbol}
```

```json
{
  "symbol": "AAPL",
  "targetHigh": 300,
  "targetLow": 200,
  "targetMean": 250,
  "targetMedian": 245,
  "lastUpdated": "2024-01-15"
}
```

---

### 1.12 Insider Transactions

```http
GET /stock/insider-transactions?symbol={symbol}&from={from}&to={to}
```

Returns insider buys/sells with `transactionType`, `filingDate`, `shares`, `price`.

---

### 1.13 Institutional Ownership

```http
GET /stock/ownership?symbol={symbol}&limit={limit}
```

Returns top institutional holders.

---

### 1.14 Senate Trading

```http
GET /stock/senate-trading?symbol={symbol}
```

Politician stock trades (US Senate).

---

### 1.15 Fund Ownership

```http
GET /stock/fund-ownership?symbol={symbol}
```

Returns list of mutual funds and ETFs that hold the stock.

---

### 1.16 ETF Profile

```http
GET /etf/profile?symbol={symbol}
```

Basic info about an ETF.

---

### 1.17 ETF Holdings

```http
GET /stock/etf-holdings?symbol={symbol}
```

Top holdings of an ETF.

---

## 2. Forex (FX)

### 2.1 List Forex Symbols

```http
GET /forex/symbol?exchange={exchange}
```

| Parameter  | Required | Description         |
|------------|----------|---------------------|
| `exchange` | no       | Defaults to `OANDA` |

**Example response**

```json
[
  {
    "symbol": "OANDA:EUR_USD",
    "displaySymbol": "EUR/USD",
    "description": "Euro / US Dollar"
  }
]
```

---

### 2.2 Forex Candles

```http
GET /forex/candle?symbol={symbol}&resolution={res}&from={from}&to={to}
```

Exactly same as stock candles, but `symbol` must be a forex symbol (e.g., `OANDA:EUR_USD`).

---

### 2.3 Forex Rates (Real‑time)

```http
GET /forex/rates?base={base}
```

| Parameter | Required | Description                 |
|-----------|----------|-----------------------------|
| `base`    | yes      | Base currency (e.g., `EUR`) |

**Response**

```json
{
  "base": "EUR",
  "rates": {
    "USD": 1.08,
    "GBP": 0.85,
    "JPY": 160.12
  }
}
```

---

## 3. Cryptocurrency

### 3.1 List Crypto Symbols

```http
GET /crypto/symbol?exchange={exchange}
```

| Parameter  | Required | Description                           |
|------------|----------|---------------------------------------|
| `exchange` | yes      | e.g., `BINANCE`, `COINBASE`, `KRAKEN` |

---

### 3.2 Crypto Candles

```http
GET /crypto/candle?symbol={symbol}&resolution={res}&from={from}&to={to}
```

Same format as stock/forex candles. Symbol format: `BINANCE:BTCUSDT`.

---

## 4. News & Sentiment

### 4.1 Market News (by Category)

```http
GET /news?category={category}
```

| Parameter  | Required | Description                            |
|------------|----------|----------------------------------------|
| `category` | yes      | `general`, `forex`, `crypto`, `merger` |

Returns latest news articles.

**Example response**

```json
[
  {
    "category": "general",
    "datetime": 1704067200,
    "headline": "Apple launches new product",
    "id": 123456,
    "image": "https://...",
    "related": "AAPL",
    "source": "Reuters",
    "summary": "...",
    "url": "https://..."
  }
]
```

---

### 4.2 Company News

```http
GET /company-news?symbol={symbol}&from={from}&to={to}
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `symbol`  | yes      | Ticker      |
| `from`    | yes      | YYYY-MM-DD  |
| `to`      | yes      | YYYY-MM-DD  |

---

### 4.3 News Sentiment

```http
GET /news-sentiment?symbol={symbol}
```

Aggregated sentiment for a stock over the last period.

```json
{
  "symbol": "AAPL",
  "buzz": 12345,
  "newsScore": 0.8,
  "sentiment": "bullish",
  "sectorAverage": 0.5
}
```

---

### 4.4 Press Releases

```http
GET /press-releases?symbol={symbol}&from={from}&to={to}
```

---

## 5. Fundamentals & Financials

### 5.1 Financials (as‑reported)

```http
GET /financials/reported?symbol={symbol}&freq={freq}
```

| Parameter | Required | Description                                |
|-----------|----------|--------------------------------------------|
| `symbol`  | yes      | Ticker                                     |
| `freq`    | no       | `annual` or `quarterly` (default `annual`) |

Returns **all** reported financials (income, balance sheet, cash flow) for each period.

**Response structure** (abbreviated)

```json
{
  "symbol": "AAPL",
  "financials": [
    {
      "period": "2024-09-30",
      "income": {
        "revenue": 393000000000,
        ...
      },
      "balanceSheet": {
        "totalAssets": 352000000000,
        ...
      },
      "cashFlow": {
        "operatingCashFlow": 110000000000,
        ...
      }
    }
  ]
}
```

---

### 5.2 Income Statement

```http
GET /financials/income-statement?symbol={symbol}&frequency={freq}
```

Returns income statement rows (revenue, gross profit, net income, EPS, etc.) over multiple periods.

---

### 5.3 Balance Sheet

```http
GET /financials/balance-sheet?symbol={symbol}&frequency={freq}
```

---

### 5.4 Cash Flow Statement

```http
GET /financials/cash-flow?symbol={symbol}&frequency={freq}
```

---

### 5.5 Basic Financials (Metrics)

```http
GET /stock/metric?symbol={symbol}
```

**Response**

```json
{
  "metric": {
    "pe": 28.5,
    "eps": 6.5,
    "dividendYield": 0.005,
    "marketCap": 2800000000000,
    "high52": 300,
    "low52": 200,
    "beta": 1.2,
    "volAvg": 80000000
  }
}
```

---

### 5.6 Key Ratios

```http
GET /ratios?symbol={symbol}
```

Returns historical ratios: P/E, P/B, P/S, ROE, ROA, debt/equity, etc.

---

### 5.7 Industry Metrics (Growth)

```http
GET /industry?symbol={symbol}
```

Shows industry averages for the stock's sector.

---

## 6. Technical Indicators

### 6.1 Simple Moving Average (SMA)

```http
GET /indicator/sma?symbol={symbol}&resolution={res}&from={from}&to={to}&timeperiod={timeperiod}
```

| Parameter    | Required | Description       |
|--------------|----------|-------------------|
| `timeperiod` | yes      | Number of periods |

Returns `sma` array.

Same pattern for **EMA**, **RSI**, **MACD**, **STOCH**, **BB**, etc.:

- `/indicator/ema`
- `/indicator/rsi`
- `/indicator/macd`
- `/indicator/stoch`
- `/indicator/bb` (Bollinger Bands)

### 6.2 Pattern Recognition

```http
GET /scan/pattern?symbol={symbol}&resolution={res}
```

Returns detected patterns (e.g., `"HeadAndShoulders"`).

### 6.3 Aggregate Indicators (multiple stocks)

```http
GET /scan/technical-indicator?symbol={symbol}&indicator={indicator}
```

Used for screening.

---

## 7. Economic Data

### 7.1 List Economic Codes

```http
GET /economic/code
```

Returns all available economic indicator codes (e.g., `GDP.US`, `CPI.US`).

### 7.2 Get Economic Data

```http
GET /economic?code={code}
```

**Example**  
`/economic?code=GDP.US` returns quarterly GDP figures.

**Response**

```json
[
  {
    "timestamp": 1704067200,
    "value": 26500.0
  }
]
```

---

## 8. Calendar Endpoints

These endpoints give upcoming or historical events.

### 8.1 IPO Calendar

```http
GET /calendar/ipo?from={from}&to={to}
```

### 8.2 Earnings Calendar

```http
GET /calendar/earnings?from={from}&to={to}
```

### 8.3 Economic Calendar

```http
GET /calendar/economic?from={from}&to={to}
```

### 8.4 Dividend Calendar

```http
GET /calendar/dividend?from={from}&to={to}
```

### 8.5 Split Calendar

```http
GET /calendar/split?from={from}&to={to}
```

### 8.6 ETF Calendar

```http
GET /calendar/etf?from={from}&to={to}
```

All calendar endpoints return arrays of events with relevant dates and figures.

---

## 9. Alternative Data

### 9.1 Insider Sentiment

```http
GET /insider-sentiment?symbol={symbol}&from={from}&to={to}
```

Aggregated insider transaction sentiment.

### 9.2 ESG Scores

```http
GET /esg?symbol={symbol}
```

```json
{
  "symbol": "AAPL",
  "esg": {
    "environmental": 75,
    "social": 82,
    "governance": 90,
    "total": 82.3
  }
}
```

### 9.3 Supply Chain (customers/suppliers)

```http
GET /stock/supply-chain?symbol={symbol}
```

Returns a list of customers and suppliers.

### 9.4 Company Earnings Calendar (detailed)

```http
GET /stock/earnings-calendar?symbol={symbol}
```

---

## 10. Index Data

### 10.1 Indices Constituents

```http
GET /index/constituents?symbol={symbol}
```

`symbol` can be `^GSPC` (S&P 500), `^NDX` (Nasdaq 100), etc.

Returns list of component symbols.

### 10.2 Index Historical Data

```http
GET /index/candle?symbol={symbol}&resolution={res}&from={from}&to={to}
```

---

## 11. WebSocket Streaming (Real‑time)

**Endpoint:** `wss://ws.finnhub.io?token=YOUR_API_KEY`

### 11.1 Connection and Authentication

The API key is passed in the URL query string. Once connected, you can subscribe/unsubscribe to symbols.

### 11.2 Subscribe to Trades

```javascript
const ws = new WebSocket('wss://ws.finnhub.io?token=YOUR_KEY');

ws.onopen = () => {
    ws.send(JSON.stringify({type: 'subscribe', symbol: 'AAPL'}));
};

ws.onmessage = (event) => {
    const data = JSON.parse(event.data);
    if (data.type === 'trade') {
        data.data.forEach(trade => {
            console.log(`${trade.s} ${trade.p} ${trade.v}`);
        });
    }
};
```

**Trade message**

```json
{
  "type": "trade",
  "data": [
    {
      "s": "AAPL",
      "p": 261.74,
      "v": 1000,
      "t": 1704067200
    }
  ]
}
```

### 11.3 Subscribe to News

Send: `{ "type": "subscribe", "symbol": "news" }`  
News messages include headline, summary, url, datetime.

### 11.4 Unsubscribe

```javascript
ws.send(JSON.stringify({type: 'unsubscribe', symbol: 'AAPL'}));
```

### 11.5 Ping / Keep‑alive

Finnhub automatically sends a ping frame; you should respond if required, but the library usually handles it.

---

## 12. Webhooks (for real‑time alerts)

Finnhub provides a webhook system to receive events (price changes, news, etc.) via HTTP POST.

### 12.1 Manage Webhooks

```http
GET /webhook/list
POST /webhook/register
DELETE /webhook/delete
```

Register a webhook with a `url`, `eventType`, `symbol`, and optional filters.

---

## 13. Headers

Besides the authentication header, you can include:

| Header         | Purpose                                  |
|----------------|------------------------------------------|
| `Accept`       | `application/json` (default)             |
| `Content-Type` | only needed for POST requests (webhooks) |

Most endpoints are GET, so no request body.

---

## 14. Request & Response Bodies

- **All GET endpoints** have **no request body** – parameters are in the URL query.
- **POST endpoints** (webhooks registration) may have a JSON body.

**Example POST to register a webhook**

```bash
curl -X POST "https://finnhub.io/api/v1/webhook/register" \
  -H "X-Finnhub-Token: YOUR_KEY" \
  -H "Content-Type: application/json" \
  -d '{"url": "https://myapp.com/webhook", "eventType": "price", "symbol": "AAPL"}'
```

Response for POST is usually:

```json
{
  "id": "webhook_123",
  "status": "active"
}
```

---

## 15. Error Handling Summary

| HTTP | error message example                       | Cause                          |
|------|---------------------------------------------|--------------------------------|
| 400  | `"Symbol not found"`                        | ticker doesn't exist           |
| 400  | `"Invalid resolution"`                      | wrong candle interval          |
| 400  | `"from must be less than to"`               | date range invalid             |
| 401  | `"Invalid API key"`                         | key missing or wrong           |
| 403  | `"You don't have access to this resource."` | premium endpoint with free key |
| 429  | `"Rate limit exceeded"`                     | too many calls                 |
| 500  | `"Internal server error"`                   | server issue – retry later     |

Always check the `error` field in the response body.

---

## 16. Best Practices

1. **Cache data** – many endpoints (profile, financials) change infrequently.
2. **Use WebSocket** for real‑time streaming instead of polling `/quote`.
3. **Batch requests** if possible – use `/scan` for multi‑symbol indicators.
4. **Monitor your usage** – your plan dashboard shows current usage.
5. **Retry with backoff** on 429 and 5xx errors.

---

## 17. Summary Table of All Endpoints

| Method    | Endpoint                       | Description                                          |
|-----------|--------------------------------|------------------------------------------------------|
| GET       | `/quote`                       | Real‑time price                                      |
| GET       | `/stock/candle`                | OHLCV candles                                        |
| GET       | `/stock/profile2`              | Company profile                                      |
| GET       | `/stock/peers`                 | Peer companies                                       |
| GET       | `/stock/symbol`                | List all symbols on exchange                         |
| GET       | `/search`                      | Symbol search                                        |
| GET       | `/stock/dividend`              | Dividend history                                     |
| GET       | `/stock/split`                 | Split history                                        |
| GET       | `/stock/earnings`              | Earnings surprises                                   |
| GET       | `/stock/recommendation`        | Analyst recommendations                              |
| GET       | `/stock/price-target`          | Average price targets                                |
| GET       | `/stock/insider-transactions`  | Insider trades                                       |
| GET       | `/stock/ownership`             | Institutional holdings                               |
| GET       | `/stock/senate-trading`        | US Senator trades                                    |
| GET       | `/stock/fund-ownership`        | Mutual fund holdings                                 |
| GET       | `/etf/profile`                 | ETF profile                                          |
| GET       | `/stock/etf-holdings`          | ETF holdings                                         |
| GET       | `/forex/symbol`                | List forex pairs                                     |
| GET       | `/forex/candle`                | Forex candles                                        |
| GET       | `/forex/rates`                 | Real‑time FX rates                                   |
| GET       | `/crypto/symbol`               | List crypto pairs                                    |
| GET       | `/crypto/candle`               | Crypto candles                                       |
| GET       | `/news`                        | Market news                                          |
| GET       | `/company-news`                | Company news                                         |
| GET       | `/news-sentiment`              | Sentiment score                                      |
| GET       | `/press-releases`              | Official press releases                              |
| GET       | `/financials/reported`         | Full financial statements                            |
| GET       | `/financials/income-statement` | Income statement (time series)                       |
| GET       | `/financials/balance-sheet`    | Balance sheet                                        |
| GET       | `/financials/cash-flow`        | Cash flow                                            |
| GET       | `/stock/metric`                | Key metrics                                          |
| GET       | `/ratios`                      | Financial ratios                                     |
| GET       | `/industry`                    | Industry averages                                    |
| GET       | `/indicator/{type}`            | Technical indicator (SMA, EMA, RSI, MACD, STOCH, BB) |
| GET       | `/scan/pattern`                | Candlestick pattern scan                             |
| GET       | `/scan/technical-indicator`    | Aggregate indicator screener                         |
| GET       | `/economic/code`               | Economic codes                                       |
| GET       | `/economic`                    | Economic data by code                                |
| GET       | `/calendar/ipo`                | IPO calendar                                         |
| GET       | `/calendar/earnings`           | Earnings calendar                                    |
| GET       | `/calendar/economic`           | Economic calendar                                    |
| GET       | `/calendar/dividend`           | Dividend calendar                                    |
| GET       | `/calendar/split`              | Split calendar                                       |
| GET       | `/calendar/etf`                | ETF calendar                                         |
| GET       | `/insider-sentiment`           | Insider sentiment aggregate                          |
| GET       | `/esg`                         | ESG scores                                           |
| GET       | `/stock/supply-chain`          | Supplier/customer list                               |
| GET       | `/index/constituents`          | Index components                                     |
| GET       | `/index/candle`                | Index candles                                        |
| POST      | `/webhook/register`            | Create a webhook                                     |
| GET       | `/webhook/list`                | List webhooks                                        |
| DELETE    | `/webhook/delete`              | Delete webhook                                       |
| WebSocket | `wss://ws.finnhub.io`          | Real‑time trades and news                            |

---

## 18. Getting Help

- Official API docs: [finnhub.io/docs/api](https://finnhub.io/docs/api)
- API status: [status.finnhub.io](https://status.finnhub.io)
- Support: support@finnhub.io
- Community SDKs: Python, Rust, PHP, .NET, etc. (see Finnhub GitHub)

---

**This document covers every publicly available Finnhub API endpoint as of version 1. For future updates, always refer
to the official documentation.**