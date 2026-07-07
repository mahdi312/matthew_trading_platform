# Frankfurter API — Complete Documentation

Frankfurter is a **free, open-source (MIT)** currency exchange rates API that blends foreign-exchange reference rates
published by 50+ central banks and monetary authorities. It hosts a **free, no-key public endpoint** at
`api.frankfurter.dev` and ships as a Docker image for unlimited self-hosting.

---

## 1. Authentication & Headers

### Authentication

**Frankfurter requires NO authentication and NO API keys.**

All requests can be made directly over HTTPS without any credentials.

```http
GET https://api.frankfurter.dev/v1/latest
```

### Request Headers (Optional but Recommended)

| Header       | Value                  | Description                    |
|:-------------|:-----------------------|:-------------------------------|
| `Accept`     | `application/json`     | Default response format        |
| `Accept`     | `text/csv`             | CSV response (v2 only)         |
| `Accept`     | `application/x-ndjson` | NDJSON streaming (v2 only)     |
| `User-Agent` | `YourApp/1.0`          | Recommended for identification |

### Response Headers

| Header          | Description                  |
|:----------------|:-----------------------------|
| `Content-Type`  | `application/json` (default) |
| `Cache-Control` | Caching hints for rate data  |

---

## 2. Base Information

| Property             | v1 (Frozen, Legacy)                   | v2 (Current, Recommended)            |
|:---------------------|:--------------------------------------|:-------------------------------------|
| **Base URL**         | `https://api.frankfurter.dev/v1`      | `https://api.frankfurter.dev/v2`     |
| **Data Source**      | ECB reference rates only              | Blended rates from 50+ central banks |
| **Historical Depth** | From 1999-01-04                       | From 1948                            |
| **Response Format**  | JSON only                             | JSON, CSV, NDJSON                    |
| **Status**           | Maintained for backward compatibility | **Recommended for new integrations** |

> **Rates update around 4PM CET every working day.**

---

## 3. V1 API Endpoints (Frozen, ECB Only)

### 3.1 GET /v1/currencies — List Supported Currencies

Returns all supported currency codes with their full names.

**Request:**

```bash
curl https://api.frankfurter.dev/v1/currencies
```

**Response (200 OK):**

```json
{
  "AED": "United Arab Emirates Dirham",
  "AFN": "Afghan Afghani",
  "ALL": "Albanian Lek",
  "AMD": "Armenian Dram",
  "ANG": "Netherlands Antillean Guilder",
  "AOA": "Angolan Kwanza",
  "ARS": "Argentine Peso",
  "AUD": "Australian Dollar",
  "AWG": "Aruban Florin",
  "AZN": "Azerbaijani Manat",
  "BAM": "Bosnia-Herzegovina Convertible Mark",
  "BBD": "Barbadian Dollar",
  "BDT": "Bangladeshi Taka",
  "BGN": "Bulgarian Lev",
  "BHD": "Bahraini Dinar",
  "BIF": "Burundian Franc",
  "BMD": "Bermudan Dollar",
  "BND": "Brunei Dollar",
  "BOB": "Bolivian Boliviano",
  "BRL": "Brazilian Real",
  "BSD": "Bahamian Dollar",
  "BTC": "Bitcoin",
  "BTN": "Bhutanese Ngultrum",
  "BWP": "Botswanan Pula",
  "BYN": "Belarusian Ruble",
  "BYR": "Belarusian Ruble (pre-2016)",
  "BZD": "Belize Dollar",
  "CAD": "Canadian Dollar",
  "CDF": "Congolese Franc",
  "CHF": "Swiss Franc",
  "CLF": "Chilean Unit of Account (UF)",
  "CLP": "Chilean Peso",
  "CNY": "Chinese Yuan",
  "COP": "Colombian Peso",
  "CRC": "Costa Rican Colón",
  "CUC": "Cuban Convertible Peso",
  "CUP": "Cuban Peso",
  "CVE": "Cape Verdean Escudo",
  "CZK": "Czech Republic Koruna",
  "DJF": "Djiboutian Franc",
  "DKK": "Danish Krone",
  "DOP": "Dominican Peso",
  "DZD": "Algerian Dinar",
  "EGP": "Egyptian Pound",
  "ERN": "Eritrean Nakfa",
  "ETB": "Ethiopian Birr",
  "EUR": "Euro",
  "FJD": "Fijian Dollar",
  "FKP": "Falkland Islands Pound",
  "GBP": "British Pound Sterling",
  "GEL": "Georgian Lari",
  "GGP": "Guernsey Pound",
  "GHS": "Ghanaian Cedi",
  "GIP": "Gibraltar Pound",
  "GMD": "Gambian Dalasi",
  "GNF": "Guinean Franc",
  "GTQ": "Guatemalan Quetzal",
  "GYD": "Guyanaese Dollar",
  "HKD": "Hong Kong Dollar",
  "HNL": "Honduran Lempira",
  "HRK": "Croatian Kuna",
  "HTG": "Haitian Gourde",
  "HUF": "Hungarian Forint",
  "IDR": "Indonesian Rupiah",
  "ILS": "Israeli New Shekel",
  "IMP": "Manx pound",
  "INR": "Indian Rupee",
  "IQD": "Iraqi Dinar",
  "IRR": "Iranian Rial",
  "ISK": "Icelandic Króna",
  "JEP": "Jersey Pound",
  "JMD": "Jamaican Dollar",
  "JOD": "Jordanian Dinar",
  "JPY": "Japanese Yen",
  "KES": "Kenyan Shilling",
  "KGS": "Kyrgystani Som",
  "KHR": "Cambodian Riel",
  "KMF": "Comorian Franc",
  "KPW": "North Korean Won",
  "KRW": "South Korean Won",
  "KWD": "Kuwaiti Dinar",
  "KYD": "Cayman Islands Dollar",
  "KZT": "Kazakhstani Tenge",
  "LAK": "Laotian Kip",
  "LBP": "Lebanese Pound",
  "LKR": "Sri Lankan Rupee",
  "LRD": "Liberian Dollar",
  "LSL": "Lesotho Loti",
  "LTL": "Lithuanian Litas",
  "LVL": "Latvian Lats",
  "LYD": "Libyan Dinar",
  "MAD": "Moroccan Dirham",
  "MDL": "Moldovan Leu",
  "MGA": "Malagasy Ariary",
  "MKD": "Macedonian Denar",
  "MMK": "Myanma Kyat",
  "MNT": "Mongolian Tugrik",
  "MOP": "Macanese Pataca",
  "MRU": "Mauritanian Ouguiya",
  "MUR": "Mauritian Rupee",
  "MVR": "Maldivian Rufiyaa",
  "MWK": "Malawian Kwacha",
  "MXN": "Mexican Peso",
  "MYR": "Malaysian Ringgit",
  "MZN": "Mozambican Metical",
  "NAD": "Namibian Dollar",
  "NGN": "Nigerian Naira",
  "NIO": "Nicaraguan Córdoba",
  "NOK": "Norwegian Krone",
  "NPR": "Nepalese Rupee",
  "NZD": "New Zealand Dollar",
  "OMR": "Omani Rial",
  "PAB": "Panamanian Balboa",
  "PEN": "Peruvian Nuevo Sol",
  "PGK": "Papua New Guinean Kina",
  "PHP": "Philippine Peso",
  "PKR": "Pakistani Rupee",
  "PLN": "Polish Zloty",
  "PYG": "Paraguayan Guarani",
  "QAR": "Qatari Rial",
  "RON": "Romanian Leu",
  "RSD": "Serbian Dinar",
  "RUB": "Russian Ruble",
  "RWF": "Rwandan Franc",
  "SAR": "Saudi Riyal",
  "SBD": "Solomon Islands Dollar",
  "SCR": "Seychellois Rupee",
  "SDG": "Sudanese Pound",
  "SEK": "Swedish Krona",
  "SGD": "Singapore Dollar",
  "SHP": "Saint Helena Pound",
  "SLE": "Sierra Leonean Leone",
  "SLL": "Sierra Leonean Leone",
  "SOS": "Somali Shilling",
  "SRD": "Surinamese Dollar",
  "SSP": "South Sudanese Pound",
  "STD": "São Tomé and Príncipe Dobra (pre-2018)",
  "STN": "São Tomé and Príncipe Dobra",
  "SVC": "Salvadoran Colón",
  "SYP": "Syrian Pound",
  "SZL": "Swazi Lilangeni",
  "THB": "Thai Baht",
  "TJS": "Tajikistani Somoni",
  "TMT": "Turkmenistani Manat",
  "TND": "Tunisian Dinar",
  "TOP": "Tongan Pa'anga",
  "TRY": "Turkish Lira",
  "TTD": "Trinidad and Tobago Dollar",
  "TWD": "New Taiwan Dollar",
  "TZS": "Tanzanian Shilling",
  "UAH": "Ukrainian Hryvnia",
  "UGX": "Ugandan Shilling",
  "USD": "United States Dollar",
  "UYU": "Uruguayan Peso",
  "UZS": "Uzbekistan Som",
  "VEF": "Venezuelan Bolívar Fuerte (old)",
  "VES": "Venezuelan Bolívar Soberano",
  "VND": "Vietnamese Dong",
  "VUV": "Vanuatu Vatu",
  "WST": "Samoan Tala",
  "XAF": "CFA Franc BEAC",
  "XAG": "Silver (troy ounce)",
  "XAU": "Gold (troy ounce)",
  "XCD": "East Caribbean Dollar",
  "XDR": "Special Drawing Rights",
  "XOF": "CFA Franc BCEAO",
  "XPF": "CFP Franc",
  "YER": "Yemeni Rial",
  "ZAR": "South African Rand",
  "ZMK": "Zambian Kwacha (pre-2013)",
  "ZMW": "Zambian Kwacha",
  "ZWL": "Zimbabwean Dollar"
}
```

---

### 3.2 GET /v1/latest — Latest Exchange Rates

**Default:** Base currency is **EUR**. Returns rates against all other currencies.

**Query Parameters:**

| Parameter | Type   | Description                                         |
|:----------|:-------|:----------------------------------------------------|
| `base`    | string | Base currency (e.g., `USD`, `GBP`)                  |
| `symbols` | string | Comma-separated list of target currencies to filter |
| `amount`  | number | Amount to convert (returns `amount` field)          |

**Request Examples:**

```bash
# Latest rates (default base EUR)
curl https://api.frankfurter.dev/v1/latest

# Latest rates with USD as base
curl https://api.frankfurter.dev/v1/latest?base=USD

# Latest rates filtered to specific currencies
curl https://api.frankfurter.dev/v1/latest?symbols=CHF,GBP

# Currency conversion with amount
curl https://api.frankfurter.dev/v1/latest?base=EUR&symbols=GBP&amount=100
```

**Response (200 OK):**

```json
{
  "amount": 1.0,
  "base": "EUR",
  "date": "2026-07-07",
  "rates": {
    "AED": 3.9566,
    "AFN": 83.034,
    "ALL": 97.437,
    "AMD": 422.14,
    "ANG": 1.9475,
    "AOA": 908.62,
    "ARS": 1212.5,
    "AUD": 1.6128,
    "AWG": 1.9365,
    "AZN": 1.8323,
    "BAM": 1.9565,
    "BBD": 2.1551,
    "BDT": 126.48,
    "BGN": 1.9559,
    "BHD": 0.40664,
    "BIF": 3100.4,
    "BMD": 1.0776,
    "BND": 1.4513,
    "BOB": 7.4451,
    "BRL": 5.9014,
    "BSD": 1.0776,
    "BTC": 0.000017,
    "BTN": 89.971,
    "BWP": 14.608,
    "BYN": 3.5252,
    "BYR": 21121,
    "BZD": 2.1719,
    "CAD": 1.4693,
    "CDF": 3077.6,
    "CHF": 0.96618,
    "CLF": 0.036382,
    "CLP": 1003.8,
    "CNY": 7.8331,
    "COP": 4375.5,
    "CRC": 562.94,
    "CUC": 1.0776,
    "CUP": 27.319,
    "CVE": 110.27,
    "CZK": 24.977,
    "DJF": 191.52,
    "DKK": 7.4581,
    "DOP": 63.755,
    "DZD": 144.08,
    "EGP": 51.598,
    "ERN": 16.164,
    "ETB": 124.36,
    "EUR": 1.0,
    "FJD": 2.4116,
    "FKP": 0.84259,
    "GBP": 0.84259,
    "GEL": 3.0055,
    "GGP": 0.84259,
    "GHS": 16.706,
    "GIP": 0.84259,
    "GMD": 76.304,
    "GNF": 9280.5,
    "GTQ": 8.3627,
    "GYD": 225.48,
    "HKD": 8.4137,
    "HNL": 26.626,
    "HRK": 7.5319,
    "HTG": 142.21,
    "HUF": 390.75,
    "IDR": 17576,
    "ILS": 3.9744,
    "IMP": 0.84259,
    "INR": 89.971,
    "IQD": 1412.4,
    "IRR": 45361,
    "ISK": 148.35,
    "JEP": 0.84259,
    "JMD": 168.45,
    "JOD": 0.7637,
    "JPY": 173.24,
    "KES": 139.01,
    "KGS": 92.052,
    "KHR": 4438.7,
    "KMF": 491.92,
    "KPW": 969.82,
    "KRW": 1488.1,
    "KWD": 0.33006,
    "KYD": 0.89806,
    "KZT": 516.52,
    "LAK": 23393,
    "LBP": 96548,
    "LKR": 326.77,
    "LRD": 209.57,
    "LSL": 19.558,
    "LTL": 3.1819,
    "LVL": 0.65176,
    "LYD": 5.2004,
    "MAD": 10.556,
    "MDL": 19.076,
    "MGA": 4816.8,
    "MKD": 61.565,
    "MMK": 2261.6,
    "MNT": 3659.5,
    "MOP": 8.6654,
    "MRU": 42.711,
    "MUR": 49.555,
    "MVR": 16.625,
    "MWK": 1870.6,
    "MXN": 19.401,
    "MYR": 5.0716,
    "MZN": 68.865,
    "NAD": 19.558,
    "NGN": 1646.9,
    "NIO": 39.647,
    "NOK": 11.423,
    "NPR": 143.95,
    "NZD": 1.7621,
    "OMR": 0.41485,
    "PAB": 1.0776,
    "PEN": 4.0826,
    "PGK": 4.2342,
    "PHP": 63.003,
    "PKR": 300.04,
    "PLN": 4.2863,
    "PYG": 8131.5,
    "QAR": 3.9229,
    "RON": 4.9762,
    "RSD": 117.04,
    "RUB": 93.821,
    "RWF": 1408.9,
    "SAR": 4.0412,
    "SBD": 9.0491,
    "SCR": 14.83,
    "SDG": 647.45,
    "SEK": 11.248,
    "SGD": 1.4513,
    "SHP": 0.84259,
    "SLE": 24.465,
    "SLL": 24465,
    "SOS": 615.89,
    "SRD": 31.891,
    "SSP": 140.34,
    "STD": 22302,
    "STN": 24.465,
    "SVC": 9.4291,
    "SYP": 2706.4,
    "SZL": 19.558,
    "THB": 39.247,
    "TJS": 11.427,
    "TMT": 3.7715,
    "TND": 3.3589,
    "TOP": 2.5435,
    "TRY": 34.958,
    "TTD": 7.3143,
    "TWD": 34.934,
    "TZS": 2863.7,
    "UAH": 43.969,
    "UGX": 3987.6,
    "USD": 1.0776,
    "UYU": 44.782,
    "UZS": 13516,
    "VEF": 3899942,
    "VES": 39.34,
    "VND": 27378,
    "VUV": 127.94,
    "WST": 2.9516,
    "XAF": 655.55,
    "XAG": 0.03438,
    "XAU": 0.00043,
    "XCD": 2.9124,
    "XDR": 0.81274,
    "XOF": 655.55,
    "XPF": 119.33,
    "YER": 269.78,
    "ZAR": 19.558,
    "ZMK": 9698.7,
    "ZMW": 28.625,
    "ZWL": 346.84
  }
}
```

**Field Descriptions:**

| Field    | Description                                                            |
|:---------|:-----------------------------------------------------------------------|
| `amount` | The amount converted (1.0 by default, or the `amount` parameter value) |
| `base`   | Base currency code                                                     |
| `date`   | Date of the rates (YYYY-MM-DD)                                         |
| `rates`  | Object mapping currency codes → exchange rates                         |

---

### 3.3 GET /v1/{date} — Historical Rates for a Specific Date

**Path Parameter:**

| Parameter | Format       | Description                               |
|:----------|:-------------|:------------------------------------------|
| `date`    | `YYYY-MM-DD` | Historical date (from 1999-01-04 onwards) |

**Query Parameters:** Same as `/latest` (`base`, `symbols`, `amount`)

**Request:**

```bash
# Rates for January 3, 2000
curl https://api.frankfurter.dev/v1/2000-01-03

# Rates for a specific date with custom base
curl https://api.frankfurter.dev/v1/2000-01-03?base=USD
```

**Response (200 OK):** Same structure as `/latest`, with the specified date.

---

### 3.4 GET /v1/{start_date}..{end_date} — Time Series

**Path Parameters:**

| Parameter    | Format       | Description |
|:-------------|:-------------|:------------|
| `start_date` | `YYYY-MM-DD` | Start date  |
| `end_date`   | `YYYY-MM-DD` | End date    |

**Query Parameters:**

| Parameter | Type   | Description                               |
|:----------|:-------|:------------------------------------------|
| `base`    | string | Base currency (default: EUR)              |
| `symbols` | string | Comma-separated list of target currencies |

**Request:**

```bash
# Time series for January 2026
curl https://api.frankfurter.dev/v1/2026-01-01..2026-01-31?base=USD&symbols=EUR,GBP,JPY
```

**Response (200 OK):**

```json
{
  "amount": 1.0,
  "base": "USD",
  "start_date": "2026-01-01",
  "end_date": "2026-01-31",
  "rates": {
    "2026-01-01": {
      "EUR": 0.9234,
      "GBP": 0.7891,
      "JPY": 145.23
    },
    "2026-01-02": {
      "EUR": 0.9241,
      "GBP": 0.7887,
      "JPY": 145.67
    }
    // ... each date in range
  }
}
```

---

## 4. V2 API Endpoints (Current, Recommended)

> **V2 is the recommended version for new integrations.** It offers blended rates from 50+ providers, deeper historical
> data (from 1948), and multiple response formats (JSON, CSV, NDJSON).

### 4.1 GET /v2/rate/{base}/{quote} — Single Currency Pair

**Path Parameters:**

| Parameter | Description         |
|:----------|:--------------------|
| `base`    | Base currency code  |
| `quote`   | Quote currency code |

**Query Parameters:**

| Parameter | Type   | Description                                   |
|:----------|:-------|:----------------------------------------------|
| `date`    | string | Historical date (YYYY-MM-DD). Default: latest |

**Request:**

```bash
# Current BRL/EUR rate
curl https://api.frankfurter.dev/v2/rate/BRL/EUR

# Historical BRL/EUR rate
curl https://api.frankfurter.dev/v2/rate/BRL/EUR?date=2026-01-01
```

**Response (200 OK):**

```json
{
  "base": "BRL",
  "quote": "EUR",
  "date": "2026-07-07",
  "rate": 0.1695
}
```

---

### 4.2 GET /v2/rates — Blended Reference Rates

Returns blended reference rates for the latest day or a specific date.

**Query Parameters:**

| Parameter   | Type    | Description                                   |
|:------------|:--------|:----------------------------------------------|
| `base`      | string  | Base currency (default: EUR)                  |
| `quotes`    | string  | Comma-separated list of quote currencies      |
| `date`      | string  | Historical date (YYYY-MM-DD). Default: latest |
| `providers` | boolean | Include provider breakdown                    |
| `interval`  | string  | `week` or `month` for downsampling            |

**Request:**

```bash
# Latest rates
curl https://api.frankfurter.dev/v2/rates

# With custom base and quotes
curl https://api.frankfurter.dev/v2/rates?base=USD&quotes=EUR,GBP,JPY

# Historical date
curl https://api.frankfurter.dev/v2/rates?date=2026-01-01

# With provider breakdown
curl https://api.frankfurter.dev/v2/rates?base=USD&providers=true
```

**Response (200 OK):**

```json
{
  "base": "USD",
  "date": "2026-07-07",
  "rates": {
    "EUR": 0.9234,
    "GBP": 0.7891,
    "JPY": 145.23
  }
}
```

**With Providers (`providers=true`):**

```json
{
  "base": "USD",
  "date": "2026-07-07",
  "rates": {
    "EUR": {
      "rate": 0.9234,
      "providers": {
        "ecb": 0.9234,
        "fed": 0.9238,
        "boe": 0.9231
      }
    }
  }
}
```

---

### 4.3 GET /v2/currencies — List Supported Currencies

Returns supported currency codes with details.

**Request:**

```bash
curl https://api.frankfurter.dev/v2/currencies
```

**Response (200 OK):**

```json
{
  "currencies": {
    "USD": {
      "name": "United States Dollar",
      "minor_units": 2
    },
    "EUR": {
      "name": "Euro",
      "minor_units": 2
    },
    "JPY": {
      "name": "Japanese Yen",
      "minor_units": 0
    },
    "GBP": {
      "name": "British Pound Sterling",
      "minor_units": 2
    }
    // ... all supported currencies
  }
}
```

---

### 4.4 GET /v2/convert — Currency Conversion

Convert an amount between two currencies.

**Query Parameters:**

| Parameter | Type   | Required | Description                                   |
|:----------|:-------|:---------|:----------------------------------------------|
| `from`    | string | Yes      | Source currency                               |
| `to`      | string | Yes      | Target currency                               |
| `amount`  | number | Yes      | Amount to convert                             |
| `date`    | string | No       | Historical date (YYYY-MM-DD). Default: latest |

**Request:**

```bash
# Convert 100 USD to EUR
curl "https://api.frankfurter.dev/v2/convert?from=USD&to=EUR&amount=100"

# Historical conversion
curl "https://api.frankfurter.dev/v2/convert?from=USD&to=EUR&amount=100&date=2026-01-01"
```

**Response (200 OK):**

```json
{
  "from": "USD",
  "to": "EUR",
  "amount": 100,
  "converted": 92.34,
  "date": "2026-07-07",
  "rate": 0.9234
}
```

---

### 4.5 GET /v2/openapi.json — OpenAPI Specification

Returns the complete OpenAPI specification for the V2 API.

**Request:**

```bash
curl https://api.frankfurter.dev/v2/openapi.json
```

---

## 5. Response Formats (V2 Only)

V2 supports multiple response formats via the `Accept` header:

| Accept Header          | Format           |
|:-----------------------|:-----------------|
| `application/json`     | JSON (default)   |
| `text/csv`             | CSV              |
| `application/x-ndjson` | NDJSON streaming |

**CSV Example:**

```bash
curl -H "Accept: text/csv" https://api.frankfurter.dev/v2/rates?base=USD
```

```csv
date,base,EUR,GBP,JPY
2026-07-07,USD,0.9234,0.7891,145.23
```

---

## 6. Error Handling

### HTTP Status Codes

| Code    | Meaning               | Description                                                     | Action                            |
|:--------|:----------------------|:----------------------------------------------------------------|:----------------------------------|
| **200** | OK                    | Request successful                                              | Process response                  |
| **400** | Bad Request           | Invalid parameter (e.g., invalid currency code, malformed date) | Validate input parameters         |
| **404** | Not Found             | Symbol or date not found                                        | Check currency code or date range |
| **429** | Too Many Requests     | Rate limit exceeded                                             | Implement exponential backoff     |
| **500** | Internal Server Error | Upstream provider or server issue                               | Retry with backoff                |
| **503** | Service Unavailable   | API temporarily unavailable                                     | Retry after delay                 |

### Error Response Body (JSON)

```json
{
  "error": "Invalid currency code: 'XYZ'"
}
```

```json
{
  "error": "Date must be in YYYY-MM-DD format"
}
```

```json
{
  "error": "No data available for this date range"
}
```

---

## 7. Rate Limiting

| Tier                | Limit            | Notes                                                                     |
|:--------------------|:-----------------|:--------------------------------------------------------------------------|
| **Public Instance** | Reasonable usage | No explicit hard cap documented, but aggressive scraping may be throttled |
| **Self-Hosted**     | Unlimited        | No limits when self-hosting with Docker                                   |

> **Recommendation:** For production-critical use, consider self-hosting with Docker:
> ```bash
> docker run -d -p 80:8080 lineofflight/frankfurter
> ```

---

## 8. Self-Hosting

Frankfurter can be self-hosted for unlimited usage and full control.

### Docker Compose

```bash
docker-compose up -d
```

Access the API at `http://localhost:8080`

### Production Deployment

```bash
docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

Access at `https://yourdomain.com:8080`

---

## 9. Summary: Endpoint Reference

### V1 (Legacy, ECB Only)

| Endpoint                       | Method | Description                          |
|:-------------------------------|:-------|:-------------------------------------|
| `/v1/currencies`               | GET    | List all supported currencies        |
| `/v1/latest`                   | GET    | Latest rates (default base EUR)      |
| `/v1/latest?base={base}`       | GET    | Latest rates with custom base        |
| `/v1/latest?symbols={symbols}` | GET    | Latest rates filtered by symbols     |
| `/v1/{date}`                   | GET    | Historical rates for a specific date |
| `/v1/{start}..{end}`           | GET    | Time series between two dates        |

### V2 (Recommended)

| Endpoint                  | Method | Description                            |
|:--------------------------|:-------|:---------------------------------------|
| `/v2/rate/{base}/{quote}` | GET    | Single currency pair rate              |
| `/v2/rates`               | GET    | Blended reference rates                |
| `/v2/currencies`          | GET    | List supported currencies with details |
| `/v2/convert`             | GET    | Currency conversion                    |
| `/v2/openapi.json`        | GET    | OpenAPI specification                  |

---

## 10. Trading Platform Integration Notes

| Consideration            | Recommendation                                                                         |
|:-------------------------|:---------------------------------------------------------------------------------------|
| **Data Frequency**       | Rates update once daily (~4PM CET), not real-time                                      |
| **Use Case**             | Suitable for **daily reference rates**, backtesting, and **non-critical** applications |
| **Not Suitable For**     | High-frequency trading, real-time execution, or tick-by-tick strategies                |
| **Production Readiness** | Self-host for reliability; public instance is free but best-effort                     |
| **Disclaimer**           | Daily reference rates, **not** real-time trading rates. Not financial advice.          |