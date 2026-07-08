# CoinGecko API — Full Reference (Requests, Responses, Headers, Auth, Errors, Plan Tiers)

Complete catalog of CoinGecko API endpoints with full request/response detail. Every endpoint is tagged with its
required plan tier.

**Plan tags**

- 🆓 **Free** — works on the free **Demo** plan and all paid plans
- 💼 **Paid** — requires **Analyst plan or above** (Lite / Pro / Pro+) — locked on Demo & Basic
- 👑 **Enterprise** — **Enterprise plan only**

---

## 0. Global Reference (applies to every endpoint below)

### Base URLs

| Plan                        | Base URL                                   |
|-----------------------------|--------------------------------------------|
| Demo                        | `https://api.coingecko.com/api/v3`         |
| Pro / Analyst / Enterprise  | `https://pro-api.coingecko.com/api/v3`     |
| Onchain DEX (GeckoTerminal) | append `/onchain` to either base URL above |

### Authentication

No OAuth — a static API key sent as a header or query parameter. All endpoints below require this key (Demo key on Demo
base URL, Pro key on Pro base URL) except `/ping`, which works without one but accepts it too.

| Plan | Header                        | Query param                   |
|------|-------------------------------|-------------------------------|
| Demo | `x-cg-demo-api-key: YOUR_KEY` | `?x_cg_demo_api_key=YOUR_KEY` |
| Pro  | `x-cg-pro-api-key: YOUR_KEY`  | `?x_cg_pro_api_key=YOUR_KEY`  |

Standard request headers:

```
Accept: application/json
x-cg-demo-api-key: YOUR_KEY      # or x-cg-pro-api-key
```

All endpoints listed are `GET` requests with no request body — parameters are passed via query string and/or URL path.

### Standard Error Response

```json
{
  "status": {
    "error_code": 10002,
    "error_message": "Your API Key is invalid."
  }
}
```

| HTTP Code | Meaning           | Cause                                                                  |
|-----------|-------------------|------------------------------------------------------------------------|
| 200       | OK                | Success                                                                |
| 400       | Bad Request       | Invalid/missing query parameter                                        |
| 401       | Unauthorized      | Missing/invalid key, or key used on wrong base URL                     |
| 403       | Forbidden         | Endpoint not included in your plan (💼/👑 endpoint called with 🆓 key) |
| 404       | Not Found         | Invalid coin/exchange/network/pool ID, or bad path                     |
| 429       | Too Many Requests | Rate limit exceeded                                                    |
| 500 / 503 | Server Error      | CoinGecko-side issue — retry with backoff                              |

This block is not repeated per-endpoint below; assume it applies to all.

---

## 1. General / Account

### 1.1 `GET /ping` — 🆓 Free

Check server status.

```bash
curl "https://api.coingecko.com/api/v3/ping" -H "x-cg-demo-api-key: YOUR_KEY"
```

```json
{
  "gecko_says": "(V3) To the Moon!"
}
```

Errors: none typical (always reachable); `429` if rate-limited.

### 1.2 `GET /key` — 💼 Paid

Check account usage/quota.

```bash
curl "https://pro-api.coingecko.com/api/v3/key" -H "x-cg-pro-api-key: YOUR_KEY"
```

```json
{
  "plan": "Analyst",
  "rate_limit_request_per_minute": 500,
  "monthly_call_credit": 500000,
  "current_total_monthly_calls": 124533,
  "current_remaining_monthly_calls": 375467
}
```

Errors: `403` if called with a Demo/Basic key (feature not in plan).

---

## 2. Simple

### 2.1 `GET /simple/price` — 🆓 Free

| Param                     | Type   | Required | Notes                      |
|---------------------------|--------|----------|----------------------------|
| `ids`                     | string | yes      | comma-separated coin IDs   |
| `vs_currencies`           | string | yes      | comma-separated currencies |
| `include_market_cap`      | bool   | no       | default false              |
| `include_24hr_vol`        | bool   | no       | default false              |
| `include_24hr_change`     | bool   | no       | default false              |
| `include_last_updated_at` | bool   | no       | default false              |
| `precision`               | string | no       | decimals or `full`         |

```bash
curl "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin,ethereum&vs_currencies=usd&include_24hr_change=true" \
  -H "x-cg-demo-api-key: YOUR_KEY"
```

```json
{
  "bitcoin": {
    "usd": 68234.12,
    "usd_24h_change": 1.23456
  },
  "ethereum": {
    "usd": 3542.67,
    "usd_24h_change": -0.87654
  }
}
```

Errors: `400` if `ids`/`vs_currencies` missing or unknown.

### 2.2 `GET /simple/token_price/{id}` — 🆓 Free

`{id}` = asset platform ID (e.g. `ethereum`). Params: `contract_addresses` (required, comma-sep), `vs_currencies` (
required), plus same `include_*`/`precision` options as 2.1.

```bash
curl "https://api.coingecko.com/api/v3/simple/token_price/ethereum?contract_addresses=0xdAC17F958D2ee523a2206206994597C13D831ec&vs_currencies=usd" \
  -H "x-cg-demo-api-key: YOUR_KEY"
```

```json
{
  "0xdac17f958d2ee523a2206206994597c13d831ec": {
    "usd": 1.0001
  }
}
```

Errors: `404` if platform ID invalid; `400` if address malformed.

### 2.3 `GET /simple/supported_vs_currencies` — 🆓 Free

No params.

```bash
curl "https://api.coingecko.com/api/v3/simple/supported_vs_currencies" -H "x-cg-demo-api-key: YOUR_KEY"
```

```json
[
  "usd",
  "eur",
  "jpy",
  "btc",
  "eth",
  "gbp",
  "aud"
]
```

---

## 3. Coins

### 3.1 `GET /coins/list` — 🆓 Free

Param: `include_platform` (bool, optional).

```bash
curl "https://api.coingecko.com/api/v3/coins/list" -H "x-cg-demo-api-key: YOUR_KEY"
```

```json
[
  {
    "id": "bitcoin",
    "symbol": "btc",
    "name": "Bitcoin"
  }
]
```

### 3.2 `GET /coins/markets` — 🆓 Free

| Param                                                                                            | Type    | Required |
|--------------------------------------------------------------------------------------------------|---------|----------|
| `vs_currency`                                                                                    | string  | yes      |
| `ids`, `category`, `order`, `per_page`, `page`, `sparkline`, `price_change_percentage`, `locale` | various | no       |

```bash
curl "https://api.coingecko.com/api/v3/coins/markets?vs_currency=usd&order=market_cap_desc&per_page=50&page=1" \
  -H "x-cg-demo-api-key: YOUR_KEY"
```

```json
[
  {
    "id": "bitcoin",
    "symbol": "btc",
    "name": "Bitcoin",
    "current_price": 68234.12,
    "market_cap": 1342567890123,
    "market_cap_rank": 1,
    "total_volume": 28934567123,
    "high_24h": 69120.5,
    "low_24h": 67012.3,
    "price_change_percentage_24h": 1.25,
    "circulating_supply": 19680000,
    "ath": 73738,
    "last_updated": "2026-06-30T12:00:00.000Z"
  }
]
```

Errors: `400` for invalid `vs_currency`.

### 3.3 `GET /coins/{id}` — 🆓 Free

Params: `localization`, `tickers`, `market_data`, `community_data`, `developer_data`, `sparkline` (all bool, default
true).

```bash
curl "https://api.coingecko.com/api/v3/coins/bitcoin?localization=false&tickers=false" \
  -H "x-cg-demo-api-key: YOUR_KEY"
```

```json
{
  "id": "bitcoin",
  "symbol": "btc",
  "name": "Bitcoin",
  "links": {
    "homepage": [
      "https://bitcoin.org"
    ]
  },
  "market_data": {
    "current_price": {
      "usd": 68234.12
    }
  }
}
```

Errors: `404` if coin ID doesn't exist.

### 3.4 `GET /coins/{id}/tickers` — 🆓 Free

Params: `exchange_ids`, `include_exchange_logo`, `page`, `order`, `depth`.

```bash
curl "https://api.coingecko.com/api/v3/coins/bitcoin/tickers?depth=true" -H "x-cg-demo-api-key: YOUR_KEY"
```

```json
{
  "name": "Bitcoin",
  "tickers": [
    {
      "base": "BTC",
      "target": "USDT",
      "market": {
        "name": "Binance",
        "identifier": "binance"
      },
      "last": 68210.5,
      "volume": 18452.33,
      "bid_ask_spread_percentage": 0.012,
      "trust_score": "green",
      "trade_url": "https://www.binance.com/en/trade/BTC_USDT"
    }
  ]
}
```

### 3.5 `GET /coins/{id}/history` — 🆓 Free

Params: `date` (required, `dd-mm-yyyy`), `localization`.

```bash
curl "https://api.coingecko.com/api/v3/coins/bitcoin/history?date=30-12-2025" -H "x-cg-demo-api-key: YOUR_KEY"
```

```json
{
  "id": "bitcoin",
  "market_data": {
    "current_price": {
      "usd": 95234.5
    }
  }
}
```

Errors: `400` if `date` format wrong; `404` for dates before coin existed.

### 3.6 `GET /coins/{id}/market_chart` — 🆓 Free

Params: `vs_currency` (required), `days` (required), `interval`, `precision`.

```bash
curl "https://api.coingecko.com/api/v3/coins/bitcoin/market_chart?vs_currency=usd&days=30" -H "x-cg-demo-api-key: YOUR_KEY"
```

```json
{
  "prices": [
    [
      1718668800000,
      66523.12
    ]
  ],
  "market_caps": [
    [
      1718668800000,
      1312345678901
    ]
  ],
  "total_volumes": [
    [
      1718668800000,
      24567891234
    ]
  ]
}
```

Errors: `401`/`403` if requesting historical depth beyond your plan's retention window.

### 3.7 `GET /coins/{id}/market_chart/range` — 🆓 Free

Params: `vs_currency`, `from` (unix sec), `to` (unix sec), `precision` — all required except precision.

```bash
curl "https://api.coingecko.com/api/v3/coins/bitcoin/market_chart/range?vs_currency=usd&from=1717200000&to=1719792000" \
  -H "x-cg-demo-api-key: YOUR_KEY"
```

Response shape identical to 3.6.

### 3.8 `GET /coins/{id}/ohlc` — 🆓 Free

Params: `vs_currency` (required), `days` (required: `1,7,14,30,90,180,365,max`), `precision`.

```bash
curl "https://api.coingecko.com/api/v3/coins/bitcoin/ohlc?vs_currency=usd&days=7" -H "x-cg-demo-api-key: YOUR_KEY"
```

```json
[
  [
    1719446400000,
    67890.1,
    68210.5,
    67650.2,
    68050.3
  ]
]
```

### 3.9 `GET /coins/{id}/ohlc/range` — 💼 Paid

Params: `vs_currency`, `from`, `to`, `interval` (`5m`,`hourly`,`daily`).

```bash
curl "https://pro-api.coingecko.com/api/v3/coins/bitcoin/ohlc/range?vs_currency=usd&from=1719446400&to=1719792000&interval=hourly" \
  -H "x-cg-pro-api-key: YOUR_KEY"
```

Response shape identical to 3.8. Errors: `403` on Demo/Basic.

### 3.10 `GET /coins/{id}/circulating_supply_chart` — 👑 Enterprise

Params: `days` (required), `interval`.

```bash
curl "https://pro-api.coingecko.com/api/v3/coins/bitcoin/circulating_supply_chart?days=30" \
  -H "x-cg-pro-api-key: YOUR_KEY"
```

```json
{
  "circulating_supply": [
    [
      1718668800000,
      19680000
    ]
  ]
}
```

Errors: `403` unless on Enterprise.

### 3.11 `GET /coins/{id}/circulating_supply_chart/range` — 👑 Enterprise

Params: `from`, `to` (unix sec, required).
Response shape identical to 3.10.

### 3.12 `GET /coins/{id}/total_supply_chart` — 👑 Enterprise

Params: `days` (required), `interval`.

```json
{
  "total_supply": [
    [
      1718668800000,
      21000000
    ]
  ]
}
```

### 3.13 `GET /coins/{id}/total_supply_chart/range` — 👑 Enterprise

Params: `from`, `to` (unix sec).
Response shape identical to 3.12.

### 3.14 `GET /coins/top_gainers_losers` — 💼 Paid

Params: `vs_currency` (required), `duration` (`1h..1y`), `top_coins` (`300,500,1000,all`).

```bash
curl "https://pro-api.coingecko.com/api/v3/coins/top_gainers_losers?vs_currency=usd&duration=24h" \
  -H "x-cg-pro-api-key: YOUR_KEY"
```

```json
{
  "top_gainers": [
    {
      "id": "some-coin",
      "usd": 0.0234,
      "usd_24h_change": 87.5
    }
  ],
  "top_losers": [
    {
      "id": "other-coin",
      "usd": 0.0011,
      "usd_24h_change": -45.2
    }
  ]
}
```

### 3.15 `GET /coins/list/new` — 💼 Paid

No params.

```bash
curl "https://pro-api.coingecko.com/api/v3/coins/list/new" -H "x-cg-pro-api-key: YOUR_KEY"
```

```json
[
  {
    "id": "new-coin",
    "symbol": "nc",
    "name": "New Coin",
    "activated_at": 1719750000
  }
]
```

### 3.16 `GET /coins/categories/list` — 🆓 Free

```bash
curl "https://api.coingecko.com/api/v3/coins/categories/list" -H "x-cg-demo-api-key: YOUR_KEY"
```

```json
[
  {
    "category_id": "depin",
    "name": "DePIN"
  }
]
```

### 3.17 `GET /coins/categories` — 🆓 Free

Param: `order` (optional).

```json
[
  {
    "id": "depin",
    "name": "DePIN",
    "market_cap": 24500000000,
    "volume_24h": 1200000000
  }
]
```

---

## 4. Contract / Token Address

### 4.1 `GET /coins/{platform}/contract/{address}` — 🆓 Free

```bash
curl "https://api.coingecko.com/api/v3/coins/ethereum/contract/0xdAC17F958D2ee523a2206206994597C13D831ec" \
  -H "x-cg-demo-api-key: YOUR_KEY"
```

```json
{
  "id": "tether",
  "symbol": "usdt",
  "name": "Tether"
}
```

Errors: `404` if contract address not tracked.

### 4.2 `GET /coins/{platform}/contract/{address}/market_chart` — 🆓 Free

Params: `vs_currency`, `days` (required).
Response shape identical to 3.6.

### 4.3 `GET /coins/{platform}/contract/{address}/market_chart/range` — 🆓 Free

Params: `vs_currency`, `from`, `to` (required).
Response shape identical to 3.6.

---

## 5. NFTs

### 5.1 `GET /nfts/list` — 🆓 Free

Params: `order`, `per_page`, `page`.

```json
[
  {
    "id": "bored-ape-yacht-club",
    "contract_address": "0xbc4ca...",
    "name": "Bored Ape Yacht Club",
    "asset_platform_id": "ethereum",
    "symbol": "BAYC"
  }
]
```

### 5.2 `GET /nfts/{id}` — 🆓 Free

```bash
curl "https://api.coingecko.com/api/v3/nfts/bored-ape-yacht-club" -H "x-cg-demo-api-key: YOUR_KEY"
```

```json
{
  "id": "bored-ape-yacht-club",
  "name": "Bored Ape Yacht Club",
  "floor_price": {
    "native_currency": 12.5,
    "usd": 42850.5
  },
  "market_cap": {
    "usd": 428500000
  },
  "volume_24h": {
    "usd": 1250000
  }
}
```

### 5.3 `GET /nfts/{platform}/contract/{address}` — 🆓 Free

Same response shape as 5.2, keyed by contract.

### 5.4 `GET /nfts/markets` — 💼 Paid

Params: `asset_platform_id`, `order`, `per_page`, `page`.

```json
[
  {
    "id": "bored-ape-yacht-club",
    "floor_price_usd": 42850.5,
    "market_cap_usd": 428500000
  }
]
```

### 5.5 `GET /nfts/{id}/market_chart` — 💼 Paid

Params: `days` (required).

```json
{
  "floor_price_usd": [
    [
      1718668800000,
      41200.0
    ]
  ],
  "volume_usd": [
    [
      1718668800000,
      980000
    ]
  ]
}
```

### 5.6 `GET /nfts/{platform}/contract/{address}/market_chart` — 💼 Paid

Same response shape as 5.5, keyed by contract.

### 5.7 `GET /nfts/{id}/tickers` — 💼 Paid

```json
{
  "tickers": [
    {
      "floor_price_in_native_currency": 12.5,
      "marketplace": "opensea"
    }
  ]
}
```

---

## 6. Exchanges & Derivatives

### 6.1 `GET /exchanges` — 🆓 Free

Params: `per_page`, `page`.

```json
[
  {
    "id": "binance",
    "name": "Binance",
    "trust_score": 10,
    "trade_volume_24h_btc": 312456.78
  }
]
```

### 6.2 `GET /exchanges/list` — 🆓 Free

```json
[
  {
    "id": "binance",
    "name": "Binance"
  }
]
```

### 6.3 `GET /exchanges/{id}` — 🆓 Free

```bash
curl "https://api.coingecko.com/api/v3/exchanges/binance" -H "x-cg-demo-api-key: YOUR_KEY"
```

```json
{
  "id": "binance",
  "name": "Binance",
  "year_established": 2017,
  "country": "Cayman Islands",
  "trust_score": 10
}
```

Errors: `404` if exchange ID unknown.

### 6.4 `GET /exchanges/{id}/tickers` — 🆓 Free

Params: `coin_ids`, `include_exchange_logo`, `page`, `depth`, `order`.
Response shape same as 3.4's `tickers` array.

### 6.5 `GET /exchanges/{id}/volume_chart` — 🆓 Free

Param: `days` (required).

```json
[
  [
    1718668800000,
    "312456.78"
  ]
]
```

### 6.6 `GET /exchanges/{id}/volume_chart/range` — 💼 Paid

Params: `from`, `to` (unix sec, required).
Response shape identical to 6.5.

### 6.7 `GET /derivatives` — 🆓 Free

```json
[
  {
    "market": "Binance (Futures)",
    "symbol": "BTCUSDT",
    "price": "68234.5",
    "open_interest": 845000000,
    "funding_rate": 0.0083
  }
]
```

### 6.8 `GET /derivatives/exchanges` — 🆓 Free

Params: `order`, `per_page`, `page`.

```json
[
  {
    "id": "binance_futures",
    "name": "Binance (Futures)",
    "open_interest_btc": 78000
  }
]
```

### 6.9 `GET /derivatives/exchanges/{id}` — 🆓 Free

Param: `include_tickers` (`all`/`unexpired`).

```json
{
  "id": "binance_futures",
  "name": "Binance (Futures)",
  "tickers": []
}
```

### 6.10 `GET /derivatives/exchanges/list` — 🆓 Free

```json
[
  {
    "id": "binance_futures",
    "name": "Binance (Futures)"
  }
]
```

---

## 7. Public Treasury

### 7.1 `GET /entities/list` — 🆓 Free

```json
[
  {
    "id": "microstrategy",
    "name": "MicroStrategy",
    "symbol": "MSTR",
    "country": "US"
  }
]
```

### 7.2 `GET /{entity}/public_treasury/{coin_id}` — 🆓 Free

e.g. `entity` = `companies`, `coin_id` = `bitcoin`.

```bash
curl "https://api.coingecko.com/api/v3/companies/public_treasury/bitcoin" -H "x-cg-demo-api-key: YOUR_KEY"
```

```json
{
  "total_holdings": 850000,
  "total_value_usd": 58000000000,
  "companies": [
    {
      "name": "MicroStrategy",
      "total_holdings": 226500
    }
  ]
}
```

### 7.3 `GET /public_treasury/{entity_id}` — 🆓 Free

```json
{
  "id": "microstrategy",
  "holdings": [
    {
      "coin_id": "bitcoin",
      "amount": 226500
    }
  ]
}
```

### 7.4 `GET /public_treasury/{entity_id}/{coin_id}/holding_chart` — 🆓 Free

Param: `days` (required).

```json
{
  "holdings": [
    [
      1718668800000,
      226500
    ]
  ]
}
```

### 7.5 `GET /public_treasury/{entity_id}/transaction_history` — 🆓 Free

```json
[
  {
    "date": "2026-06-20",
    "coin_id": "bitcoin",
    "amount_change": 5000,
    "type": "purchase"
  }
]
```

---

## 8. General / Market-Wide

### 8.1 `GET /asset_platforms` — 🆓 Free

```json
[
  {
    "id": "ethereum",
    "chain_identifier": 1,
    "name": "Ethereum"
  }
]
```

### 8.2 `GET /token_lists/{asset_platform_id}/all.json` — 🆓 Free

```json
{
  "name": "CoinGecko",
  "tokens": [
    {
      "address": "0xdAC17F...",
      "symbol": "USDT"
    }
  ]
}
```

### 8.3 `GET /exchange_rates` — 🆓 Free

```json
{
  "rates": {
    "btc": {
      "name": "Bitcoin",
      "value": 1.0,
      "type": "crypto"
    },
    "usd": {
      "name": "US Dollar",
      "value": 68234.5,
      "type": "fiat"
    }
  }
}
```

### 8.4 `GET /search` — 🆓 Free

Param: `query` (required).

```json
{
  "coins": [
    {
      "id": "solana",
      "name": "Solana",
      "symbol": "SOL"
    }
  ],
  "exchanges": [],
  "categories": [],
  "nfts": []
}
```

### 8.5 `GET /search/trending` — 🆓 Free

```json
{
  "coins": [
    {
      "item": {
        "id": "pepe",
        "name": "Pepe",
        "symbol": "PEPE",
        "market_cap_rank": 42
      }
    }
  ],
  "nfts": [],
  "categories": []
}
```

### 8.6 `GET /global` — 🆓 Free

```json
{
  "data": {
    "active_cryptocurrencies": 17234,
    "total_market_cap": {
      "usd": 2480000000000
    },
    "market_cap_percentage": {
      "btc": 54.1,
      "eth": 17.2
    }
  }
}
```

### 8.7 `GET /global/decentralized_finance_defi` — 🆓 Free

```json
{
  "data": {
    "defi_market_cap": "112000000000",
    "trading_volume_24h": "4500000000",
    "defi_dominance": "4.5"
  }
}
```

### 8.8 `GET /global/market_cap_chart` — 💼 Paid

Param: `days` (required).

```json
{
  "market_cap_chart": {
    "market_cap": [
      [
        1718668800000,
        2410000000000
      ]
    ],
    "volume": [
      [
        1718668800000,
        95000000000
      ]
    ]
  }
}
```

### 8.9 `GET /news` — 💼 Paid

Params: `data_type`, `page`.

```json
{
  "data": [
    {
      "title": "Bitcoin tests new resistance level",
      "url": "https://...",
      "published_at": "2026-06-30T08:00:00Z"
    }
  ]
}
```

---

## 9. Onchain DEX API (GeckoTerminal — included with CoinGecko Pro key)

> These also work with a Demo key at lower rate limits via `api.coingecko.com/api/v3/onchain/...`.

### 9.1 `GET /onchain/simple/networks/{network}/token_price/{addresses}` — 🆓 Free

```bash
curl "https://api.coingecko.com/api/v3/onchain/simple/networks/eth/token_price/0xdAC17F958D2ee523a2206206994597C13D831ec" \
  -H "x-cg-demo-api-key: YOUR_KEY"
```

```json
{
  "data": {
    "attributes": {
      "token_prices": {
        "0xdac17f958d2ee523a2206206994597c13d831ec": "1.0001"
      }
    }
  }
}
```

### 9.2 `GET /onchain/networks` — 🆓 Free

```json
{
  "data": [
    {
      "id": "eth",
      "attributes": {
        "name": "Ethereum"
      }
    }
  ]
}
```

### 9.3 `GET /onchain/networks/{network}/dexes` — 🆓 Free

```json
{
  "data": [
    {
      "id": "uniswap_v3",
      "attributes": {
        "name": "Uniswap V3"
      }
    }
  ]
}
```

### 9.4 `GET /onchain/networks/{network}/pools/{address}` — 🆓 Free

```json
{
  "data": {
    "id": "eth_0x88e6...",
    "attributes": {
      "base_token_price_usd": "68234.5",
      "reserve_in_usd": "45000000"
    }
  }
}
```

### 9.5 `GET /onchain/networks/{network}/pools/multi/{addresses}` — 🆓 Free

Same shape as 9.4 but `data` is an array.

### 9.6 `GET /onchain/networks/trending_pools` — 🆓 Free

```json
{
  "data": [
    {
      "id": "eth_0x88e6...",
      "attributes": {
        "name": "WETH/USDC"
      }
    }
  ]
}
```

### 9.7 `GET /onchain/networks/{network}/trending_pools` — 🆓 Free

Same shape as 9.6, filtered to one network.

### 9.8 `GET /onchain/networks/{network}/pools` — 🆓 Free

Params: `page`, `sort`.
Same shape as 9.6.

### 9.9 `GET /onchain/networks/{network}/dexes/{dex}/pools` — 🆓 Free

Same shape as 9.6, filtered by DEX.

### 9.10 `GET /onchain/networks/new_pools` — 🆓 Free

Same shape as 9.6.

### 9.11 `GET /onchain/networks/{network}/new_pools` — 🆓 Free

Same shape as 9.6, filtered to one network.

### 9.12 `GET /onchain/pools/megafilter` — 💼 Paid

Params: many filters — `networks`, `dexes`, `min_liquidity`, `max_liquidity`, `min_volume_24h`, `category`, etc.

```json
{
  "data": [
    {
      "id": "eth_0x88e6...",
      "attributes": {
        "reserve_in_usd": "45000000"
      }
    }
  ]
}
```

Errors: `403` on Demo/Basic.

### 9.13 `GET /onchain/search/pools` — 🆓 Free

Param: `query` (required).
Same shape as 9.6.

### 9.14 `GET /onchain/pools/trending_search` — 💼 Paid

Same shape as 9.6.

### 9.15 `GET /onchain/networks/{network}/tokens/{address}/pools` — 🆓 Free

Same shape as 9.6, filtered by token.

### 9.16 `GET /onchain/networks/{network}/tokens/{address}` — 🆓 Free

```json
{
  "data": {
    "id": "eth_0xdac1...",
    "attributes": {
      "symbol": "USDT",
      "price_usd": "1.0001"
    }
  }
}
```

### 9.17 `GET /onchain/networks/{network}/tokens/multi/{addresses}` — 🆓 Free

Same shape as 9.16, `data` array.

### 9.18 `GET /onchain/networks/{network}/tokens/{address}/info` — 🆓 Free

```json
{
  "data": {
    "attributes": {
      "name": "Tether",
      "symbol": "USDT",
      "websites": [
        "https://tether.to"
      ]
    }
  }
}
```

### 9.19 `GET /onchain/networks/{network}/pools/{address}/info` — 🆓 Free

```json
{
  "data": {
    "attributes": {
      "base_token": {
        "symbol": "WETH"
      },
      "quote_token": {
        "symbol": "USDC"
      }
    }
  }
}
```

### 9.20 `GET /onchain/tokens/info_recently_updated` — 🆓 Free

```json
{
  "data": [
    {
      "id": "eth_0xdac1...",
      "attributes": {
        "symbol": "USDT"
      }
    }
  ]
}
```

### 9.21 `GET /onchain/networks/{network}/tokens/{address}/top_traders` — 💼 Paid

```json
{
  "data": [
    {
      "attributes": {
        "wallet_address": "0x123...",
        "realized_pnl_usd": 124500
      }
    }
  ]
}
```

### 9.22 `GET /onchain/networks/{network}/tokens/{address}/top_holders` — 💼 Paid

```json
{
  "data": [
    {
      "attributes": {
        "wallet_address": "0x456...",
        "percentage_relative_to_total_supply": 2.3
      }
    }
  ]
}
```

### 9.23 `GET /onchain/networks/{network}/tokens/{address}/holders_chart` — 💼 Paid

Param: `days` (required).

```json
{
  "data": {
    "attributes": {
      "holders_chart": [
        [
          1718668800000,
          12500
        ]
      ]
    }
  }
}
```

### 9.24 `GET /onchain/networks/{network}/pools/{address}/ohlcv/{timeframe}` — 🆓 Free

`{timeframe}` = `day`/`hour`/`minute`. Params: `aggregate`, `limit`, `before_timestamp`.

```bash
curl "https://api.coingecko.com/api/v3/onchain/networks/eth/pools/0x88e6a0c2ddd26feeb64f039a2c41296fcb3f5640/ohlcv/hour?aggregate=4&limit=100" \
  -H "x-cg-demo-api-key: YOUR_KEY"
```

```json
{
  "data": {
    "attributes": {
      "ohlcv_list": [
        [
          1719446400,
          68000.1,
          68210.5,
          67890.2,
          68050.3,
          152.5
        ]
      ]
    }
  }
}
```

> Array format: `[timestamp_sec, open, high, low, close, volume]`.

### 9.25 `GET /onchain/networks/{network}/tokens/{address}/ohlcv/{timeframe}` — 💼 Paid

Same params/shape as 9.24, aggregated across all pools for the token.

### 9.26 `GET /onchain/networks/{network}/pools/{address}/trades` — 🆓 Free

Param: `trade_volume_in_usd_greater_than` (optional filter).

```json
{
  "data": [
    {
      "attributes": {
        "tx_hash": "0xabc...",
        "from_token_amount": "1.5",
        "to_token_amount": "5100",
        "kind": "buy",
        "block_timestamp": "2026-06-30T12:00:00Z"
      }
    }
  ]
}
```

### 9.27 `GET /onchain/networks/{network}/tokens/{address}/trades` — 💼 Paid

Same shape as 9.26, aggregated across all pools.

### 9.28 `GET /onchain/categories` — 💼 Paid

```json
{
  "data": [
    {
      "id": "meme",
      "attributes": {
        "name": "Meme"
      }
    }
  ]
}
```

### 9.29 `GET /onchain/categories/{category}/pools` — 💼 Paid

Same shape as 9.6, filtered by category.

---

## 10. WebSocket (Real-Time Streaming)

| Service                                        | Plan                                                                   |
|------------------------------------------------|------------------------------------------------------------------------|
| WebSocket API — live price/trade/OHLCV streams | 💼 Paid — **Analyst plan and above only**, not available on Demo/Basic |

Auth for WebSocket uses the same `x-cg-pro-api-key` passed during the connection handshake (per CoinGecko's WebSocket
docs at `docs.coingecko.com/websocket`). REST polling on `/simple/price` or `/coins/markets` is the only real-time-ish
option on the free Demo plan.

---

## 11. Rate Limits by Plan (general guidance — confirm current numbers at signup)

| Plan                       | Calls/Month    | Calls/Minute   | Historical Depth              | Commercial License | WebSocket |
|----------------------------|----------------|----------------|-------------------------------|--------------------|-----------|
| Demo (🆓)                  | ~10,000        | ~30            | 1 year                        | No                 | No        |
| Basic                      | higher (paid)  | higher         | 1 year                        | Yes                | No        |
| Analyst/Lite/Pro/Pro+ (💼) | tiered, higher | tiered, higher | Full (12+ years on top tiers) | Yes                | Yes       |
| Enterprise (👑)            | custom         | custom         | Full + exclusive endpoints    | Yes, custom        | Yes       |

---

## 12. Sources

- Endpoint Overview: https://docs.coingecko.com/reference/endpoint-overview
- Authentication: https://docs.coingecko.com/reference/authentication
- Demo key setup: https://support.coingecko.com/hc/en-us/articles/21880397454233
- WebSocket docs: https://docs.coingecko.com/websocket
- Pricing/plans: https://www.coingecko.com/en/api/pricing
- GeckoTerminal API guide: https://apiguide.geckoterminal.com/

> Response bodies above are representative samples based on documented schemas — always validate exact field sets
> against the live reference pages at `docs.coingecko.com`, since CoinGecko periodically adds fields or changes plan
> gating.
