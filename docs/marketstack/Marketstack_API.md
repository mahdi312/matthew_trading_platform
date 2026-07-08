# Marketstack API — Complete Documentation

Marketstack is a RESTful JSON API that provides global stock market data including real-time, intraday, and historical
end-of-day (EOD) prices, tickers, exchanges, splits, dividends, currencies, and related market metadata.

---

## 1. Versioning & Base Information

| Property          | V1 (Legacy)                      | V2 (Current, Recommended)                |
|:------------------|:---------------------------------|:-----------------------------------------|
| **Base URL**      | `https://api.marketstack.com/v1` | `https://api.marketstack.com/v2`         |
| **Status**        | Deprecated after June 30, 2025   | **Recommended for all new integrations** |
| **Data Coverage** | 70+ exchanges, 170,000+ tickers  | 2,700+ exchanges, 30,000+ tickers        |
| **New Endpoints** | Limited                          | Tickers list, enhanced EDGAR/SEC data    |

> **⚠️ Important:** V1 endpoints will be deprecated after **June 30, 2025**. All new integrations should use **V2**.

---

## 2. Authentication

### API Key Required

Every request must include your API access key as a query parameter. No other headers are required for basic requests.

```http
GET https://api.marketstack.com/v2/eod?access_key=YOUR_ACCESS_KEY&symbols=AAPL
```

| Parameter    | Location     | Required | Description                                                                 |
|:-------------|:-------------|:---------|:----------------------------------------------------------------------------|
| `access_key` | Query string | **Yes**  | Your unique API key from the [dashboard](https://marketstack.com/dashboard) |

### HTTPS Encryption

All requests must use HTTPS. Both free and paid plans support 256-bit HTTPS encryption.

```http
https://api.marketstack.com/v2/...
```

### Security Best Practices

- **Never expose your API key** in client-side code, public repositories, or logs
- If compromised, reset your key in the [account dashboard](https://marketstack.com/dashboard)
- Use environment variables or secrets management for production deployments

---

## 3. Headers

### Request Headers (Optional)

| Header       | Value              | Description                    |
|:-------------|:-------------------|:-------------------------------|
| `Accept`     | `application/json` | Default response format        |
| `User-Agent` | `YourApp/1.0`      | Recommended for identification |

No authentication headers are required—the `access_key` query parameter handles authentication.

### Response Headers

| Header          | Description                 |
|:----------------|:----------------------------|
| `Content-Type`  | `application/json`          |
| `Cache-Control` | Caching hints for rate data |

---

## 4. End-of-Day (EOD) Data

### GET /eod

Obtain end-of-day data for one or multiple stock tickers.

**Base URL:** `https://api.marketstack.com/v2/eod`

### Query Parameters

| Parameter    | Type    | Required | Description                                        |
|:-------------|:--------|:---------|:---------------------------------------------------|
| `access_key` | string  | **Yes**  | Your API access key                                |
| `symbols`    | string  | **Yes**  | One or multiple comma-separated tickers (max: 100) |
| `exchange`   | string  | No       | Filter by exchange MIC code                        |
| `date_from`  | string  | No       | Start date (YYYY-MM-DD)                            |
| `date_to`    | string  | No       | End date (YYYY-MM-DD)                              |
| `sort`       | string  | No       | `ASC` or `DESC` (default: DESC)                    |
| `limit`      | integer | No       | Results per page (default: 100, max: 1000)         |
| `offset`     | integer | No       | Pagination offset (default: 0)                     |

### Request Examples

**Latest EOD for a single symbol:**

```bash
curl "https://api.marketstack.com/v2/eod?access_key=YOUR_KEY&symbols=AAPL"
```

**Historical range for multiple symbols:**

```bash
curl "https://api.marketstack.com/v2/eod?access_key=YOUR_KEY&symbols=AAPL,MSFT,GOOGL&date_from=2026-01-01&date_to=2026-07-01"
```

**With pagination:**

```bash
curl "https://api.marketstack.com/v2/eod?access_key=YOUR_KEY&symbols=AAPL&limit=50&offset=100"
```

### Response Body (200 OK)

```json
{
  "pagination": {
    "limit": 100,
    "offset": 0,
    "count": 100,
    "total": 9944
  },
  "data": [
    {
      "date": "2021-04-09T00:00:00+0000",
      "symbol": "AAPL",
      "exchange": "XNAS",
      "open": 129.8,
      "high": 133.04,
      "low": 129.47,
      "close": 132.995,
      "volume": 106686703.0,
      "adj_open": 129.8,
      "adj_high": 133.04,
      "adj_low": 129.47,
      "adj_close": 132.995,
      "adj_volume": 106686703.0,
      "split_factor": 1.0,
      "dividend": 0.0
    }
  ]
}
```

### Response Fields

| Field               | Description                                            |
|:--------------------|:-------------------------------------------------------|
| `pagination.limit`  | Results per page                                       |
| `pagination.offset` | Current offset                                         |
| `pagination.count`  | Results on current page                                |
| `pagination.total`  | Total available results                                |
| `date`              | UTC date/time in ISO-8601 format                       |
| `symbol`            | Stock ticker symbol                                    |
| `exchange`          | Exchange MIC code                                      |
| `open`              | Raw opening price                                      |
| `high`              | Raw high price                                         |
| `low`               | Raw low price                                          |
| `close`             | Raw closing price                                      |
| `volume`            | Raw volume                                             |
| `adj_open`          | Adjusted opening price (accounts for splits/dividends) |
| `adj_high`          | Adjusted high price                                    |
| `adj_low`           | Adjusted low price                                     |
| `adj_close`         | Adjusted closing price                                 |
| `adj_volume`        | Adjusted volume                                        |
| `split_factor`      | Split factor for corporate actions                     |
| `dividend`          | Dividend distribution                                  |

> **Adjusted Prices:** "Adjusted" prices are stock price values amended to accurately reflect the stock's value after
> accounting for corporate actions such as splits or dividends, following the "CRSP Calculations" methodology.

---

## 5. Latest EOD Data

### GET /eod/latest

Obtain the most recent end-of-day data for one or multiple tickers.

**Base URL:** `https://api.marketstack.com/v2/eod/latest`

### Query Parameters

| Parameter    | Type   | Required | Description                                        |
|:-------------|:-------|:---------|:---------------------------------------------------|
| `access_key` | string | **Yes**  | Your API access key                                |
| `symbols`    | string | **Yes**  | One or multiple comma-separated tickers (max: 100) |

### Request Example

```bash
curl "https://api.marketstack.com/v2/eod/latest?access_key=YOUR_KEY&symbols=AAPL,MSFT"
```

### Response Body

Same structure as `/eod`, returning only the most recent data point for each symbol.

---

## 6. Intraday Data

### GET /intraday

Obtain intraday data with intervals as short as one minute. Intraday prices are available for US stock tickers included
in the IEX exchange.

**Base URL:** `https://api.marketstack.com/v2/intraday`

### Query Parameters

| Parameter    | Type    | Required | Description                                                                                        |
|:-------------|:--------|:---------|:---------------------------------------------------------------------------------------------------|
| `access_key` | string  | **Yes**  | Your API access key                                                                                |
| `symbols`    | string  | **Yes**  | One or multiple tickers (max: 100)                                                                 |
| `exchange`   | string  | No       | Filter by exchange MIC                                                                             |
| `interval`   | string  | No       | `1min`, `5min`, `10min`, `15min`, `30min`, `1hour` (default), `3hour`, `6hour`, `12hour`, `24hour` |
| `sort`       | string  | No       | `ASC` or `DESC` (default: DESC)                                                                    |
| `date_from`  | string  | No       | Start date/time (YYYY-MM-DD or ISO-8601)                                                           |
| `date_to`    | string  | No       | End date/time (YYYY-MM-DD or ISO-8601)                                                             |
| `limit`      | integer | No       | Results per page (default: 100, max: 1000)                                                         |
| `offset`     | integer | No       | Pagination offset                                                                                  |

> **⚠️ Note:** Intervals below 15 minutes (1min, 5min, 10min) require the **Professional Plan or higher**. Free and
> Basic plans are limited to 15min and above.

### Request Examples

**Default intraday (1-hour intervals):**

```bash
curl "https://api.marketstack.com/v2/intraday?access_key=YOUR_KEY&symbols=AAPL"
```

**1-minute intervals (Professional Plan required):**

```bash
curl "https://api.marketstack.com/v2/intraday?access_key=YOUR_KEY&symbols=AAPL&interval=1min"
```

**Historical intraday with date range:**

```bash
curl "https://api.marketstack.com/v2/intraday?access_key=YOUR_KEY&symbols=AAPL&date_from=2026-07-01&date_to=2026-07-08"
```

### Response Body (200 OK)

```json
{
  "pagination": {
    "limit": 100,
    "offset": 0,
    "count": 100,
    "total": 5000
  },
  "data": [
    {
      "date": "2020-06-02T00:00:00+0000",
      "symbol": "AAPL",
      "exchange": "IEXG",
      "open": 317.75,
      "high": 322.35,
      "low": 317.21,
      "close": 317.94,
      "last": 318.91,
      "volume": 41551000
    }
  ]
}
```

### Response Fields (Intraday)

| Field      | Description                      |
|:-----------|:---------------------------------|
| `date`     | UTC date/time in ISO-8601 format |
| `symbol`   | Stock ticker symbol              |
| `exchange` | Exchange MIC code                |
| `open`     | Opening price                    |
| `high`     | High price                       |
| `low`      | Low price                        |
| `close`    | Closing price                    |
| `last`     | Last executed trade              |
| `volume`   | Trading volume                   |

### Real-Time Updates (Professional Plan)

For Professional Plan subscribers, the intraday endpoint can provide real-time market data updated every minute, 5
minutes, or 10 minutes.

```bash
curl "https://api.marketstack.com/v2/intraday?access_key=YOUR_KEY&symbols=AAPL&interval=1min"
```

---

## 7. Splits Data

### GET /splits

Obtain stock split factor information for different symbols.

**Base URL:** `https://api.marketstack.com/v2/splits`

### Query Parameters

| Parameter    | Type    | Required | Description                                |
|:-------------|:--------|:---------|:-------------------------------------------|
| `access_key` | string  | **Yes**  | Your API access key                        |
| `symbols`    | string  | **Yes**  | One or multiple tickers (max: 100)         |
| `sort`       | string  | No       | `ASC` or `DESC` (default: DESC)            |
| `date_from`  | string  | No       | Start date (YYYY-MM-DD)                    |
| `date_to`    | string  | No       | End date (YYYY-MM-DD)                      |
| `limit`      | integer | No       | Results per page (default: 100, max: 1000) |
| `offset`     | integer | No       | Pagination offset                          |

### Request Example

```bash
curl "https://api.marketstack.com/v2/splits?access_key=YOUR_KEY&symbols=AAPL"
```

### Response Body (200 OK)

```json
{
  "pagination": {
    "limit": 100,
    "offset": 0,
    "count": 100,
    "total": 50765
  },
  "data": [
    {
      "date": "2021-05-24",
      "split_factor": 0.5,
      "symbol": "IAU"
    }
  ]
}
```

| Field          | Description                               |
|:---------------|:------------------------------------------|
| `date`         | Date of the split                         |
| `symbol`       | Stock ticker symbol                       |
| `split_factor` | Split factor for that symbol on that date |

---

## 8. Dividends Data

### GET /dividends

Obtain dividend information for different symbols.

**Base URL:** `https://api.marketstack.com/v2/dividends`

### Query Parameters

| Parameter    | Type    | Required | Description                                |
|:-------------|:--------|:---------|:-------------------------------------------|
| `access_key` | string  | **Yes**  | Your API access key                        |
| `symbols`    | string  | **Yes**  | One or multiple tickers (max: 100)         |
| `sort`       | string  | No       | `ASC` or `DESC` (default: DESC)            |
| `date_from`  | string  | No       | Start date (YYYY-MM-DD)                    |
| `date_to`    | string  | No       | End date (YYYY-MM-DD)                      |
| `limit`      | integer | No       | Results per page (default: 100, max: 1000) |
| `offset`     | integer | No       | Pagination offset                          |

### Request Example

```bash
curl "https://api.marketstack.com/v2/dividends?access_key=YOUR_KEY&symbols=AAPL"
```

### Response Body (200 OK)

```json
{
  "pagination": {
    "limit": 100,
    "offset": 0,
    "count": 100,
    "total": 50765
  },
  "data": [
    {
      "date": "2021-05-24",
      "dividend": 0.5,
      "symbol": "IAU"
    }
  ]
}
```

| Field      | Description                                  |
|:-----------|:---------------------------------------------|
| `date`     | Date of the dividend                         |
| `symbol`   | Stock ticker symbol                          |
| `dividend` | Dividend amount for that symbol on that date |

---

## 9. Tickers

### GET /tickers

Look up information about one or multiple stock ticker symbols.

**Base URL:** `https://api.marketstack.com/v2/tickers`

### Sub-Endpoints

| Endpoint                       | Description                              |
|:-------------------------------|:-----------------------------------------|
| `/tickers`                     | List all available tickers               |
| `/tickers/{symbol}`            | Get information about a specific ticker  |
| `/tickers/{symbol}/eod`        | Get EOD data for a specific ticker       |
| `/tickers/{symbol}/intraday`   | Get intraday data for a specific ticker  |
| `/tickers/{symbol}/splits`     | Get splits data for a specific ticker    |
| `/tickers/{symbol}/dividends`  | Get dividends data for a specific ticker |
| `/tickers/{symbol}/eod/{date}` | Get EOD data for a specific date         |

### Request Examples

**List tickers:**

```bash
curl "https://api.marketstack.com/v2/tickers?access_key=YOUR_KEY"
```

**Get specific ticker info:**

```bash
curl "https://api.marketstack.com/v2/tickers/AAPL?access_key=YOUR_KEY"
```

**Get EOD data for a ticker:**

```bash
curl "https://api.marketstack.com/v2/tickers/AAPL/eod?access_key=YOUR_KEY&date_from=2026-01-01&date_to=2026-07-01"
```

---

## 10. Exchanges

### GET /exchanges

List all supported stock exchanges and their metadata.

**Base URL:** `https://api.marketstack.com/v2/exchanges`

### GET /exchanges/{mic}/tickers

List all tickers listed on a specific exchange.

**Base URL:** `https://api.marketstack.com/v2/exchanges/{mic}/tickers`

### Request Examples

**List all exchanges:**

```bash
curl "https://api.marketstack.com/v2/exchanges?access_key=YOUR_KEY"
```

**List tickers on NASDAQ:**

```bash
curl "https://api.marketstack.com/v2/exchanges/XNAS/tickers?access_key=YOUR_KEY"
```

### Market Indices

To access index data, pass `INDX` as the exchange MIC identification.

```bash
# Get Dow Jones Industrial Average data
curl "https://api.marketstack.com/v2/eod?access_key=YOUR_KEY&symbols=DJI.INDX"
```

| Endpoint                     | Description                       |
|:-----------------------------|:----------------------------------|
| `/exchanges/INDX/tickers`    | List all available market indices |
| `/tickers/{symbol}.INDX`     | Get metadata for a specific index |
| `/tickers/{symbol}.INDX/eod` | Get EOD data for a specific index |

---

## 11. Currencies

### GET /currencies

List all currencies supported by the Marketstack API.

**Base URL:** `https://api.marketstack.com/v2/currencies`

### Request Example

```bash
curl "https://api.marketstack.com/v2/currencies?access_key=YOUR_KEY"
```

---

## 12. Indices

### GET /indices

List stock market indices.

**Base URL:** `https://api.marketstack.com/v2/indices`

### Request Example

```bash
curl "https://api.marketstack.com/v2/indices?access_key=YOUR_KEY"
```

---

## 13. Pagination

All list endpoints support pagination via `limit` and `offset` parameters.

| Parameter | Default | Maximum | Description                          |
|:----------|:--------|:--------|:-------------------------------------|
| `limit`   | 100     | 1000    | Number of results per page           |
| `offset`  | 0       | —       | Starting position (0 = first result) |

### Pagination Example

```bash
# Page 1 (results 1-100)
curl "https://api.marketstack.com/v2/eod?access_key=YOUR_KEY&symbols=AAPL&limit=100&offset=0"

# Page 2 (results 101-200)
curl "https://api.marketstack.com/v2/eod?access_key=YOUR_KEY&symbols=AAPL&limit=100&offset=100"
```

### Pagination Response

```json
{
  "pagination": {
    "limit": 100,
    "offset": 0,
    "count": 100,
    "total": 9944
  },
  "data": [
    ...
  ]
}
```

| Field               | Description                       |
|:--------------------|:----------------------------------|
| `pagination.limit`  | The limit value used              |
| `pagination.offset` | The offset value used             |
| `pagination.count`  | Number of results on current page |
| `pagination.total`  | Total available results           |

---

## 14. Error Handling

### HTTP Status Codes

| Code    | Type                         | Description                                                      | Action                              |
|:--------|:-----------------------------|:-----------------------------------------------------------------|:------------------------------------|
| **200** | Success                      | Request successful                                               | Process response                    |
| **401** | `unauthorized`               | Authentication failed. Verify your access key or account status. | Check API key                       |
| **403** | `https_access_restricted`    | HTTPS not supported on current plan                              | Upgrade plan or check protocol      |
| **403** | `function_access_restricted` | Endpoint not available on current plan                           | Upgrade plan                        |
| **404** | `invalid_api_function`       | The API endpoint does not exist                                  | Check endpoint path                 |
| **404** | `404_not_found`              | Resource not found                                               | Verify symbol or resource           |
| **406** | `data_not_available`         | Requested data is currently unavailable                          | Retry later                         |
| **429** | `too_many_requests`          | Monthly request quota exceeded                                   | Upgrade plan or wait for next month |
| **429** | `rate_limit_reached`         | Rate limit reached (5 req/sec)                                   | Implement throttling                |
| **500** | `internal_error`             | Internal server error                                            | Retry with backoff                  |

### Rate Limiting

| Limit              | Value                  |
|:-------------------|:-----------------------|
| **Per-second**     | 5 requests per second  |
| **Monthly (Free)** | 100 requests per month |

> **Each symbol consumes one API request**. For example, requesting 5 symbols counts as 5 requests.

### Error Response Body

**Validation Error:**

```json
{
  "error": {
    "code": "validation_error",
    "message": "You have to specify at least one symbol and not more than 100"
  }
}
```

**Validation Error with Context:**

```json
{
  "error": {
    "code": "validation_error",
    "message": "Request failed with validation error",
    "context": {
      "symbols": [
        {
          "key": "missing_symbols",
          "message": "You did not specify any symbols."
        }
      ]
    }
  }
}
```

---

## 15. Summary: All Endpoints Reference

| Endpoint                     | Method | Description                  | Plan               |
|:-----------------------------|:-------|:-----------------------------|:-------------------|
| `/eod`                       | GET    | End-of-day historical data   | All plans          |
| `/eod/latest`                | GET    | Latest EOD data              | All plans          |
| `/intraday`                  | GET    | Intraday bar data            | All plans (15min+) |
| `/intraday/latest`           | GET    | Latest intraday data         | All plans          |
| `/splits`                    | GET    | Stock split data             | All plans          |
| `/dividends`                 | GET    | Dividend data                | All plans          |
| `/tickers`                   | GET    | List/search tickers          | All plans          |
| `/tickers/{symbol}`          | GET    | Single ticker metadata       | All plans          |
| `/tickers/{symbol}/eod`      | GET    | EOD for specific ticker      | All plans          |
| `/tickers/{symbol}/intraday` | GET    | Intraday for specific ticker | All plans          |
| `/exchanges`                 | GET    | List exchanges               | All plans          |
| `/exchanges/{mic}/tickers`   | GET    | Tickers on specific exchange | All plans          |
| `/indices`                   | GET    | List market indices          | All plans          |
| `/currencies`                | GET    | List supported currencies    | All plans          |

---

## 16. Trading Platform Integration Notes

| Consideration        | Recommendation                                                                              |
|:---------------------|:--------------------------------------------------------------------------------------------|
| **Free Tier**        | 100 requests/month with 12 months of history                                                |
| **Paid Tiers**       | 10,000 / 100,000 / 500,000 requests/month                                                   |
| **Rate Limit**       | 5 requests per second                                                                       |
| **Symbol Cost**      | Each symbol in a request counts as one request                                              |
| **Cache Strategy**   | Cache EOD data for 24 hours; cache intraday data for 5–15 minutes                           |
| **Retry Policy**     | Exponential backoff for 429 and 500 errors                                                  |
| **Not Suitable For** | High-frequency trading or tick-by-tick execution (use Polygon.io or similar for production) |

### Recommended Retry Logic

```
Retry 1: wait 1s
Retry 2: wait 2s
Retry 3: wait 4s
Retry 4: wait 8s
Max Retries: 4
If all fail: Log error and resume
```

