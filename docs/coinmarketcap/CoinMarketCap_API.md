# CoinMarketCap API — Complete Reference Documentation

This is the **complete** guide to the CoinMarketCap Professional API. It covers all available endpoints across every
category: cryptocurrency data, exchange data, DEX data, global metrics, indices, content, community, and utility tools.

> **Base URL:** `https://pro-api.coinmarketcap.com`

## Authentication

Every request to the CoinMarketCap Pro API requires a valid API key. You can supply your API key in one of two ways:

- **Preferred method:** Via a custom header named `X-CMC_PRO_API_KEY`
- **Convenience method:** Via a query string parameter named `CMC_PRO_API_KEY`

> **Security warning:** The custom header option is strongly recommended over the query string option for production
> environments.

### Getting an API Key

1. Visit the [API Developer Portal](https://pro.coinmarketcap.com/signup) to register
2. Choose a plan (free Basic tier available)
3. Copy your API key from the dashboard

### Example Authenticated Request

```bash
curl -X GET "https://pro-api.coinmarketcap.com/v1/cryptocurrency/listings/latest" \
  -H "X-CMC_PRO_API_KEY: YOUR_API_KEY" \
  -H "Accept: application/json"
```

### Keyless Public API (No Authentication)

Certain endpoints can be called without an API key by prefixing the path with `/public-api`:

```bash
curl -X GET "https://pro-api.coinmarketcap.com/public-api/v1/exchange/map"
```

This is intended for early testing before a full authenticated integration.

## Common HTTP Headers

Every HTTP request must contain the following header:

| Header              | Required            | Description                                 |
|---------------------|---------------------|---------------------------------------------|
| `Accept`            | Yes                 | Must be `application/json`                  |
| `Accept-Encoding`   | Recommended         | `deflate, gzip` for efficient data transfer |
| `X-CMC_PRO_API_KEY` | Yes (authenticated) | Your API key                                |

## Response Format

All endpoints return data in JSON format. A `data` object contains the results of your query, and a `status` object is
always included.

### Success Response Structure

```json
{
  "data": {
    // Your requested data here
  },
  "status": {
    "timestamp": "2018-06-06T07:52:27.273Z",
    "error_code": 0,
    "error_message": null,
    "elapsed": 0,
    "credit_count": 1
  }
}
```

### Status Object Fields

| Field           | Description                                        |
|-----------------|----------------------------------------------------|
| `timestamp`     | Server time when the call was executed (ISO 8601)  |
| `error_code`    | `0` for success; error code for failures           |
| `error_message` | `null` for success; error description for failures |
| `elapsed`       | Milliseconds to process the request                |
| `credit_count`  | Number of API call credits used                    |

### Bundled Responses

Many endpoints support passing multiple comma-separated values. When you do, the `data` object returns as an object map
instead of an array:

```json
{
  "data": {
    "BTC": {
      ...
    },
    "ETH": {
      ...
    }
  },
  "status": {
    ...
  }
}
```

## HTTP Status Codes

The API uses standard HTTP status codes to indicate success or failure:

| Status | Meaning               | What to do                                                    |
|--------|-----------------------|---------------------------------------------------------------|
| 200    | OK                    | Successful request                                            |
| 400    | Bad Request           | Invalid argument — check query parameters                     |
| 401    | Unauthorized          | Invalid or missing API key — verify `X-CMC_PRO_API_KEY`       |
| 402    | Payment Required      | Overdue balance on paid plan — pay in Developer Portal        |
| 403    | Forbidden             | Plan doesn't include this endpoint — check pricing or upgrade |
| 429    | Too Many Requests     | Rate limit exceeded — slow down or upgrade                    |
| 500    | Internal Server Error | Unexpected server issue — retry with exponential backoff      |

## Error Response Codes

A `status` object is always included. During errors, reference the `error_code` and `error_message` properties:

| HTTP Status | Error Code | Error Message                            | Resolution                                       |
|-------------|------------|------------------------------------------|--------------------------------------------------|
| 401         | 1001       | `API_KEY_INVALID`                        | Regenerate your key in Developer Portal          |
| 401         | 1002       | `API_KEY_MISSING`                        | Add `X-CMC_PRO_API_KEY` header                   |
| 402         | 1003       | `API_KEY_PLAN_REQUIRES_PAYEMENT`         | Activate plan                                    |
| 402         | 1004       | `API_KEY_PLAN_PAYMENT_EXPIRED`           | Renew subscription                               |
| 403         | 1005       | `API_KEY_REQUIRED`                       | Include API key — endpoint not available keyless |
| 403         | 1006       | `API_KEY_PLAN_NOT_AUTHORIZED`            | Upgrade plan                                     |
| 403         | 1007       | `API_KEY_DISABLED`                       | Contact support                                  |
| 429         | 1008       | `API_KEY_PLAN_MINUTE_RATE_LIMIT_REACHED` | Wait 60 seconds                                  |
| 429         | 1009       | `API_KEY_PLAN_DAILY_RATE_LIMIT_REACHED`  | Upgrade or wait for reset                        |

## Rate Limits & Credits

### Request Throttling

API call rate limiting scales with your usage tier and resets every 60 seconds.

### Call Credits

Most plans include daily and monthly limits ("hard caps"):

- Successful (HTTP 200) data calls count as 1 credit each
- Paginated endpoints: additional credit for every 100 data points beyond default
- Bundled resources: 1 credit per 100 resources returned
- Currency conversion (`convert` parameter): additional credit per conversion beyond the first
- Account management, usage stats, and error responses are not counted
- Lightweight/map endpoints always count as 1 credit

Monitor usage via:

- `status.credit_count` in each response
- `/v1/key/info` endpoint
- [Developer Portal dashboard](https://pro.coinmarketcap.com/account)

### Keyless Public API Rate Limits

Keyless endpoints have lower rate limits — see
the [Keyless Public API](https://coinmarketcap.com/api/documentation/pro-api-reference/keyless-public-api) documentation
for details.

## Identifiers

Cryptocurrencies, exchanges, and fiat currencies can be identified in multiple ways:

| Entity         | Preferred                         | Alternative                    | Lookup Endpoint          |
|----------------|-----------------------------------|--------------------------------|--------------------------|
| Cryptocurrency | `id` (e.g., `id=1` for Bitcoin)   | `symbol` (e.g., `BTC`), `slug` | `/v1/cryptocurrency/map` |
| Exchange       | `id` (e.g., `id=270` for Binance) | `slug` (e.g., `slug=binance`)  | `/v1/exchange/map`       |
| Fiat currency  | ISO 4217 code (e.g., `USD`)       | CoinMarketCap ID               | `/v1/fiat/map`           |

> **Important:** Using CoinMarketCap IDs is always recommended. Cryptocurrency symbols are not always unique and can
> change with rebrands.

## Complete Endpoint Reference

### 1. Cryptocurrency (19 endpoints)

Core cryptocurrency data: prices, listings, metadata, trending coins, and OHLCV.

| Endpoint                                            | Method | Description                               |
|-----------------------------------------------------|--------|-------------------------------------------|
| `/v1/cryptocurrency/map`                            | GET    | Map names/symbols to CMC IDs              |
| `/v3/cryptocurrency/listings/latest`                | GET    | Current listings with market data         |
| `/v1/cryptocurrency/listings/new`                   | GET    | Newly added cryptocurrencies              |
| `/v1/cryptocurrency/listings/historical`            | GET    | Historical listings snapshot              |
| `/v3/cryptocurrency/quotes/latest`                  | GET    | Latest price quotes                       |
| `/v3/cryptocurrency/quotes/historical`              | GET    | Historical price quotes                   |
| `/v2/cryptocurrency/ohlcv/latest`                   | GET    | Latest OHLCV data                         |
| `/v2/cryptocurrency/ohlcv/historical`               | GET    | Historical OHLCV candles                  |
| `/v2/cryptocurrency/market-pairs/latest`            | GET    | Trading pairs for a coin                  |
| `/v2/cryptocurrency/price-performance-stats/latest` | GET    | Price performance stats                   |
| `/v2/cryptocurrency/info`                           | GET    | Static metadata (logo, description, URLs) |
| `/v1/cryptocurrency/trending/latest`                | GET    | Currently trending coins                  |
| `/v1/cryptocurrency/trending/gainers-losers`        | GET    | Top gainers and losers                    |
| `/v1/cryptocurrency/trending/most-visited`          | GET    | Most visited on CMC                       |
| `/v1/simple/price`                                  | GET    | Simple price lookup                       |
| `/v1/cryptocurrency/categories`                     | GET    | List all categories with market metrics   |
| `/v1/cryptocurrency/category`                       | GET    | Single category details                   |
| `/v1/cryptocurrency/airdrops`                       | GET    | List airdrops                             |
| `/v1/cryptocurrency/airdrop`                        | GET    | Single airdrop details                    |

#### Example: Cryptocurrency ID Map

```bash
curl -X GET "https://pro-api.coinmarketcap.com/v1/cryptocurrency/map?symbol=BTC,ETH" \
  -H "X-CMC_PRO_API_KEY: YOUR_API_KEY"
```

**Response:**

```json
{
  "data": [
    {
      "id": 1,
      "name": "Bitcoin",
      "symbol": "BTC",
      "slug": "bitcoin",
      "is_active": 1,
      "first_historical_data": "2013-04-28T18:47:21.000Z",
      "last_historical_data": "2024-01-15T00:00:00.000Z"
    },
    {
      "id": 1027,
      "name": "Ethereum",
      "symbol": "ETH",
      "slug": "ethereum",
      "is_active": 1,
      "first_historical_data": "2015-08-07T14:55:54.000Z",
      "last_historical_data": "2024-01-15T00:00:00.000Z"
    }
  ],
  "status": {
    "timestamp": "2024-01-15T12:00:00.000Z",
    "error_code": 0,
    "error_message": null,
    "elapsed": 0,
    "credit_count": 1
  }
}
```

#### Example: Top 100 Coins by Market Cap

```bash
curl -X GET "https://pro-api.coinmarketcap.com/v1/cryptocurrency/listings/latest?limit=100&sort=market_cap" \
  -H "X-CMC_PRO_API_KEY: YOUR_API_KEY"
```

#### Example: Latest Price Quote

```bash
curl -X GET "https://pro-api.coinmarketcap.com/v2/cryptocurrency/quotes/latest?id=1,1027" \
  -H "X-CMC_PRO_API_KEY: YOUR_API_KEY"
```

#### Example: Historical Price Data

```bash
curl -X GET "https://pro-api.coinmarketcap.com/v3/cryptocurrency/quotes/historical?id=1&time_start=2024-01-01&time_end=2024-01-31&interval=daily" \
  -H "X-CMC_PRO_API_KEY: YOUR_API_KEY"
```

#### Example: Token Metadata

```bash
curl -X GET "https://pro-api.coinmarketcap.com/v2/cryptocurrency/info?id=1,1027" \
  -H "X-CMC_PRO_API_KEY: YOUR_API_KEY"
```

### 2. Exchange (7 endpoints)

Centralized exchange data, listings, quotes, and market pairs.

| Endpoint                           | Method | Description                |
|------------------------------------|--------|----------------------------|
| `/v1/exchange/map`                 | GET    | Exchange ID Map            |
| `/v1/exchange/listings/latest`     | GET    | Exchange Listings Latest   |
| `/v1/exchange/quotes/latest`       | GET    | Exchange Quotes Latest     |
| `/v1/exchange/quotes/historical`   | GET    | Exchange Quotes Historical |
| `/v1/exchange/market-pairs/latest` | GET    | Market Pairs Latest        |
| `/v1/exchange/assets`              | GET    | Exchange Assets            |
| `/v1/exchange/info`                | GET    | Exchange Metadata          |

> **Keyless available:** `/v1/exchange/map` can be called without an API key using `/public-api/v1/exchange/map`.

#### Example: Exchange ID Map

```bash
curl -X GET "https://pro-api.coinmarketcap.com/v1/exchange/map?slug=binance" \
  -H "X-CMC_PRO_API_KEY: YOUR_API_KEY"
```

### 3. Token / DEX Data (16 endpoints)

Decentralized exchange (DEX) trading and on-chain data across Ethereum, Solana, BNB Chain, and more.

| Endpoint                        | Method | Description                |
|---------------------------------|--------|----------------------------|
| `/v4/dex/spot-pairs/latest`     | GET    | Pairs Listings Latest      |
| `/v4/dex/pairs/quotes/latest`   | GET    | Quotes Latest              |
| `/v1/dex/token`                 | GET    | Get Token Detail           |
| `/v1/dex/token/price`           | GET    | Get Token Price            |
| `/v1/dex/token/price/batch`     | GET    | Batch Get Token Prices     |
| `/v1/dex/tokens/batch-query`    | POST   | Batch Query Tokens         |
| `/v1/dex/token/pools`           | GET    | Get Token Pools            |
| `/v1/dex/token-liquidity/query` | GET    | Query Token Liquidity      |
| `/v1/dex/tokens/transactions`   | GET    | Get Swap List              |
| `/v1/dex/tokens/trending/list`  | POST   | Get Trending Tokens        |
| `/v1/dex/new/list`              | GET    | Get New Tokens             |
| `/v1/dex/meme/list`             | GET    | Get Meme Tokens            |
| `/v1/dex/gainer-loser/list`     | GET    | Get Top Gainers and Losers |
| `/v1/dex/security/detail`       | GET    | Get Security Detail        |
| `/v1/dex/search`                | GET    | Search Tokens              |
| `/v1/dex/liquidity-change/list` | GET    | Get Liquidity Change List  |

#### Example: Get Trending Tokens

```bash
curl -X POST "https://pro-api.coinmarketcap.com/v1/dex/tokens/trending/list" \
  -H "X-CMC_PRO_API_KEY: YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "platformIds": "ethereum,solana",
    "interval": "1h",
    "pageSize": 10,
    "sortBy": "volume",
    "sortType": "desc"
  }'
```

### 4. Platform (2 endpoints)

DEX platform and network information.

| Endpoint                  | Method | Description         |
|---------------------------|--------|---------------------|
| `/v1/dex/platform/list`   | GET    | Get Platform List   |
| `/v1/dex/platform/detail` | GET    | Get Platform Detail |

> **Keyless available:** Both endpoints can be called without an API key using the `/public-api` prefix.

#### Example: Get Platform List

```bash
curl -X GET "https://pro-api.coinmarketcap.com/public-api/v1/dex/platform/list"
```

### 5. OHLCV / K-Line (2 endpoints)

K-line candlestick and OHLCV price data for DEX pairs.

| Endpoint             | Method | Description        |
|----------------------|--------|--------------------|
| `/v1/k-line/points`  | GET    | Get K-line Points  |
| `/v1/k-line/candles` | GET    | Get K-line Candles |

**K-line Points Response Format:** Each point is an array with 3 elements — `[price, volume, timestamp]`.

**K-line Candles Response Format:** Each candle is an array with 7 elements —
`[open, high, low, close, volume, timestamp, traders]`.

**Supported intervals:** `1s`, `5s`, `30s`, `1min`, `3min`, `5min`, `15min`, `30min`, `1h`, `2h`, `4h`, `6h`, `8h`,
`12h`, `1d`, `3d`, `1w`, `1m`.

> **Keyless available:** Both endpoints can be called without an API key using the `/public-api` prefix.

#### Example: Get K-line Candles

```bash
curl -X GET "https://pro-api.coinmarketcap.com/public-api/v1/k-line/candles?platform=ethereum&address=0x...&interval=1h&from=1705363200&to=1705449600"
```

### 6. Global Metrics (6 endpoints)

Global aggregate market data, fear & greed index, and altcoin season index.

| Endpoint                               | Method | Description                            |
|----------------------------------------|--------|----------------------------------------|
| `/v1/global-metrics/quotes/latest`     | GET    | Latest total market cap, BTC dominance |
| `/v1/global-metrics/quotes/historical` | GET    | Historical global market metrics       |
| `/v3/fear-and-greed/latest`            | GET    | Current market sentiment score         |
| `/v3/fear-and-greed/historical`        | GET    | Historical fear/greed values           |
| `/v1/altcoin-season-index/latest`      | GET    | Altcoin Season Index Latest            |
| `/v1/altcoin-season-index/historical`  | GET    | Altcoin Season Index Historical        |

> **Keyless available:** Fear & Greed endpoints can be called without an API key using `/public-api`.

#### Example: Fear & Greed Latest

```bash
curl -X GET "https://pro-api.coinmarketcap.com/public-api/v3/fear-and-greed/latest"
```

**Response:**

```json
{
  "data": [
    {
      "value": 72,
      "value_classification": "Greed",
      "timestamp": "2024-01-15T00:00:00.000Z",
      "time_until_update": "2024-01-16T00:00:00.000Z"
    }
  ],
  "status": {
    ...
  }
}
```

### 7. CMC Index (4 endpoints)

CoinMarketCap market indices.

| Endpoint                      | Method | Description                           |
|-------------------------------|--------|---------------------------------------|
| `/v3/index/cmc100-latest`     | GET    | CMC100 current value and constituents |
| `/v3/index/cmc100-historical` | GET    | CMC100 index history                  |
| `/v3/index/cmc20-latest`      | GET    | CMC20 current value                   |
| `/v3/index/cmc20-historical`  | GET    | CMC20 index history                   |

**Supported intervals:** `5m`, `15m`, `daily`.

> **Keyless available:** All index endpoints can be called without an API key using `/public-api`.

#### Example: CMC100 Historical

```bash
curl -X GET "https://pro-api.coinmarketcap.com/public-api/v3/index/cmc100-historical?interval=daily&count=30"
```

### 8. Content (4 endpoints)

News, headlines, and Alexandria articles.

| Endpoint                     | Method | Description                         |
|------------------------------|--------|-------------------------------------|
| `/v1/content/latest`         | GET    | Latest news and Alexandria articles |
| `/v1/content/posts/top`      | GET    | Top ranked community posts          |
| `/v1/content/posts/latest`   | GET    | Latest community posts              |
| `/v1/content/posts/comments` | GET    | Comments on a specific post         |

> **Plan requirement:** Growth tier or higher.
> **Credit cost:** 0 credits.

#### Example: Content Latest

```bash
curl -X GET "https://pro-api.coinmarketcap.com/v1/content/latest?symbol=BTC&limit=10" \
  -H "X-CMC_PRO_API_KEY: YOUR_API_KEY"
```

### 9. Community (2 endpoints)

Community trending data.

| Endpoint                       | Method | Description                           |
|--------------------------------|--------|---------------------------------------|
| `/v1/community/trending/token` | GET    | Trending tokens by community activity |
| `/v1/community/trending/topic` | GET    | Trending discussion topics            |

> **Plan requirement:** Growth tier or higher.
> **Credit cost:** 0 credits.
> **Cache frequency:** Every minute.

#### Example: Community Trending Tokens

```bash
curl -X GET "https://pro-api.coinmarketcap.com/v1/community/trending/token?limit=5" \
  -H "X-CMC_PRO_API_KEY: YOUR_API_KEY"
```

### 10. Tools / Utilities (4 endpoints)

Utility endpoints for conversions and monitoring.

| Endpoint                     | Method | Description                    |
|------------------------------|--------|--------------------------------|
| `/v2/tools/price-conversion` | GET    | Convert between currencies     |
| `/v1/fiat/map`               | GET    | Map fiat currencies to CMC IDs |
| `/v1/key/info`               | GET    | API key usage and plan details |
| `/v1/tools/postman`          | GET    | Export APIs to Postman format  |

> **Key Info credit cost:** 0 credits (but contributes to minute-based rate limit).

#### Example: Fiat ID Map

```bash
curl -X GET "https://pro-api.coinmarketcap.com/v1/fiat/map?include_metals=true" \
  -H "X-CMC_PRO_API_KEY: YOUR_API_KEY"
```

#### Example: Key Info

```bash
curl -X GET "https://pro-api.coinmarketcap.com/v1/key/info" \
  -H "X-CMC_PRO_API_KEY: YOUR_API_KEY"
```

**Response:**

```json
{
  "data": {
    "usage": {
      "current_month": {
        "credits_used": 1234,
        "credits_limit": 10000
      },
      "current_day": {
        "credits_used": 56,
        "credits_limit": 500
      }
    },
    "plan": {
      "name": "Hobbyist",
      "price": 0
    }
  },
  "status": {
    ...
  }
}
```

## WebSocket (Beta)

CoinMarketCap also offers WebSocket streaming for real-time data:

```
wss://pro-stream.coinmarketcap.com/v1
```

**Authentication:** `X-CMC_PRO_API_KEY` header.

> See the [WebSocket (Beta) Overview](https://coinmarketcap.com/api/documentation/pro-api-reference/websocket) for
> channel details.

## x402 Protocol (Pay-per-Request)

x402 is an open protocol enabling automatic stablecoin payments (USDC) for individual API requests — no API keys,
subscriptions, or accounts required.

x402 endpoints do **not** require `X-CMC_PRO_API_KEY` or any other authentication header.

See the [x402 documentation](https://coinmarketcap.com/api/documentation/x402) for current per-request pricing and
supported routes.

## Best Practices

1. **Use CoinMarketCap IDs** — more stable than symbols
2. **Use `/map` endpoints** — quickly find corresponding IDs
3. **Use `/listings` for sorted, paginated lists** — use `/quotes` and `/info` when you already know which assets you
   care about
4. **Use `*/latest` for current data** — use `*/historical` for time-series data
5. **Use `*/info` for metadata** — use `*/map` for stable identifiers
6. **Bundle multiple IDs** — pass comma-separated values to reduce calls
7. **Cache data** — global metrics updates every few minutes; fear/greed updates daily
8. **Monitor usage** — use `/v1/key/info` before heavy usage

## Summary Table of All Endpoints

| Category       | # Endpoints | Keyless Available |
|----------------|-------------|-------------------|
| Cryptocurrency | 19          | Limited           |
| Exchange       | 7           | Map endpoint only |
| Token / DEX    | 16          | Limited           |
| Platform       | 2           | Yes               |
| OHLCV          | 2           | Yes               |
| Global Metrics | 6           | Fear & Greed only |
| CMC Index      | 4           | Yes               |
| Content        | 4           | No                |
| Community      | 2           | No                |
| Tools          | 4           | Limited           |
| **Total**      | **~66**     |                   |

## Getting Help

| Resource          | Link                                                                                       |
|-------------------|--------------------------------------------------------------------------------------------|
| Official API docs | [pro.coinmarketcap.com/api/documentation](https://pro.coinmarketcap.com/api/documentation) |
| API Pricing       | [coinmarketcap.com/api/pricing](https://coinmarketcap.com/api/pricing)                     |
| API Status        | [status.coinmarketcap.com](https://status.coinmarketcap.com)                               |
| FAQ               | [CoinMarketCap API FAQ](https://coinmarketcap.com/api/documentation/faq)                   |
| Changelog         | [API Changelog](https://coinmarketcap.com/api/documentation/changelog)                     |
| Developer Portal  | [pro.coinmarketcap.com/signup](https://pro.coinmarketcap.com/signup)                       |

---

**This document covers all publicly available CoinMarketCap Pro API endpoints as of version 3. For future updates,
always refer to the official documentation.**