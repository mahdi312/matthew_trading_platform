# Fixer.io API — Complete Documentation

Fixer.io is a lightweight REST API for real-time and historical foreign exchange (forex) rates and currency conversion.
It delivers JSON-formatted data for **170+ world currencies** (including Bitcoin, Gold, and Silver), aggregated from *
*15+ trusted financial sources** including the European Central Bank.

---

## 1. Authentication

### API Key Required

Every request must include your API access key as a query parameter. Register for a free key
at [fixer.io/signup/free](https://fixer.io/signup/free).

| Parameter    | Location     | Required | Description                                                          |
|:-------------|:-------------|:---------|:---------------------------------------------------------------------|
| `access_key` | Query string | **Yes**  | Your unique API key from the [dashboard](https://fixer.io/dashboard) |

### Authentication Example

```http
GET https://data.fixer.io/api/latest?access_key=YOUR_API_KEY
```

### Security

All plans support **256-bit SSL encryption**. Use HTTPS for all requests.

---

## 2. Base Information

| Property                  | Value                        |
|:--------------------------|:-----------------------------|
| **Base URL**              | `https://data.fixer.io/api/` |
| **Method**                | All endpoints use `GET`      |
| **Data Format**           | `application/json`           |
| **Default Base Currency** | EUR                          |
| **Historical Data Start** | January 1, 1999              |

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

## 4. All Endpoints Reference

| Endpoint       | Method | Description                                  | Plan Availability  |
|:---------------|:-------|:---------------------------------------------|:-------------------|
| `/symbols`     | GET    | List all available currency codes and names  | All plans          |
| `/latest`      | GET    | Real-time exchange rates                     | All plans          |
| `/{date}`      | GET    | Historical rates for a specific date         | Paid plans only    |
| `/convert`     | GET    | Currency conversion                          | Paid plans only    |
| `/timeseries`  | GET    | Daily rates between two dates (max 365 days) | Professional plan+ |
| `/fluctuation` | GET    | Currency fluctuation data between two dates  | Professional Plus+ |

---

## 5. Endpoint Details

### 5.1 GET /symbols — List Supported Currencies

Returns all available currency codes with their full names.

**Request:**

```bash
curl "https://data.fixer.io/api/symbols?access_key=YOUR_API_KEY"
```

**Response (200 OK):**

```json
{
  "success": true,
  "symbols": {
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
}
```

---

### 5.2 GET /latest — Latest Exchange Rates

Returns real-time exchange rate data for all or a specific set of currencies.

**Default base currency is EUR.**

#### Query Parameters

| Parameter    | Type   | Required | Description                                           |
|:-------------|:-------|:---------|:------------------------------------------------------|
| `access_key` | string | **Yes**  | Your API Key                                          |
| `base`       | string | No       | Three-letter currency code of preferred base currency |
| `symbols`    | string | No       | Comma-separated list of currency codes to filter      |

#### Request Examples

```bash
# Latest rates (default base EUR)
curl "https://data.fixer.io/api/latest?access_key=YOUR_API_KEY"

# Latest rates with USD as base
curl "https://data.fixer.io/api/latest?access_key=YOUR_API_KEY&base=USD"

# Latest rates filtered to specific currencies
curl "https://data.fixer.io/api/latest?access_key=YOUR_API_KEY&symbols=USD,GBP,JPY"

# Latest rates with custom base and filters
curl "https://data.fixer.io/api/latest?access_key=YOUR_API_KEY&base=USD&symbols=GBP,JPY,EUR"
```

#### Response Body (200 OK)

```json
{
  "success": true,
  "timestamp": 1519296206,
  "base": "EUR",
  "date": "2026-04-06",
  "rates": {
    "AUD": 1.566015,
    "CAD": 1.560132,
    "CHF": 1.154727,
    "CNY": 7.827874,
    "GBP": 0.882047,
    "JPY": 132.360679,
    "USD": 1.23396
  }
}
```

#### Response Fields

| Field       | Description                                     |
|:------------|:------------------------------------------------|
| `success`   | Boolean indicating if the request succeeded     |
| `timestamp` | UNIX timestamp of when rates were collected     |
| `base`      | Three-letter currency code of the base currency |
| `date`      | Date the rates were collected (YYYY-MM-DD)      |
| `rates`     | Object mapping currency codes → exchange rates  |

---

### 5.3 GET /{date} — Historical Exchange Rates

Returns historical exchange rate data for a specific date.

**Availability:** Paid plans only.

#### Path Parameter

| Parameter | Format       | Description                               |
|:----------|:-------------|:------------------------------------------|
| `date`    | `YYYY-MM-DD` | Historical date (from 1999-01-01 onwards) |

#### Query Parameters

| Parameter    | Type   | Required | Description                                           |
|:-------------|:-------|:---------|:------------------------------------------------------|
| `access_key` | string | **Yes**  | Your API Key                                          |
| `base`       | string | No       | Three-letter currency code of preferred base currency |
| `symbols`    | string | No       | Comma-separated list of currency codes to filter      |

#### Request Example

```bash
# Historical rates for December 24, 2013
curl "https://data.fixer.io/api/2013-12-24?access_key=YOUR_API_KEY&base=USD&symbols=INR"
```

#### Response Body (200 OK)

```json
{
  "success": true,
  "historical": true,
  "date": "2013-12-24",
  "timestamp": 1387843199,
  "base": "USD",
  "rates": {
    "INR": 62.345
  }
}
```

#### Response Fields

| Field        | Description                                     |
|:-------------|:------------------------------------------------|
| `success`    | Boolean indicating if the request succeeded     |
| `historical` | Returns `true` for historical rates requests    |
| `date`       | Date for which historical rates were requested  |
| `timestamp`  | UNIX timestamp when rates were collected        |
| `base`       | Three-letter currency code of the base currency |
| `rates`      | Object mapping currency codes → exchange rates  |

---

### 5.4 GET /convert — Currency Conversion

Converts any amount from one currency to another. Supports both current and historical rates.

**Availability:** Paid plans only.

#### Query Parameters

| Parameter    | Type   | Required | Description                                |
|:-------------|:-------|:---------|:-------------------------------------------|
| `access_key` | string | **Yes**  | Your API Key                               |
| `from`       | string | **Yes**  | Three-letter currency code to convert from |
| `to`         | string | **Yes**  | Three-letter currency code to convert to   |
| `amount`     | number | **Yes**  | Amount to be converted                     |
| `date`       | string | No       | Date (YYYY-MM-DD) to use historical rates  |

#### Request Examples

```bash
# Convert 25 GBP to JPY (current rates)
curl "https://data.fixer.io/api/convert?access_key=YOUR_API_KEY&from=GBP&to=JPY&amount=25"

# Convert using historical rates
curl "https://data.fixer.io/api/convert?access_key=YOUR_API_KEY&from=USD&to=INR&amount=100&date=1999-01-01"
```

#### Response Body (200 OK)

```json
{
  "success": true,
  "query": {
    "from": "GBP",
    "to": "JPY",
    "amount": 25
  },
  "info": {
    "timestamp": 1519328414,
    "rate": 148.972231
  },
  "historical": "",
  "date": "2018-02-22",
  "result": 3724.305775
}
```

#### Response Fields

| Field            | Description                                  |
|:-----------------|:---------------------------------------------|
| `query.from`     | Currency code converted from                 |
| `query.to`       | Currency code converted to                   |
| `query.amount`   | Amount being converted                       |
| `info.timestamp` | UNIX timestamp when rate was collected       |
| `info.rate`      | Exchange rate used for conversion            |
| `historical`     | Returns `true` if historical rates were used |
| `date`           | Date the rate was collected                  |
| `result`         | Converted amount                             |

---

### 5.5 GET /timeseries — Time-Series Data

Returns daily historical rates between two dates, with a **maximum time frame of 365 days**.

**Availability:** Professional plan and above.

#### Query Parameters

| Parameter    | Type   | Required | Description                                           |
|:-------------|:-------|:---------|:------------------------------------------------------|
| `access_key` | string | **Yes**  | Your API Key                                          |
| `start_date` | string | **Yes**  | Start date (YYYY-MM-DD)                               |
| `end_date`   | string | **Yes**  | End date (YYYY-MM-DD)                                 |
| `base`       | string | No       | Three-letter currency code of preferred base currency |
| `symbols`    | string | No       | Comma-separated list of currency codes to filter      |

#### Request Example

```bash
curl "https://data.fixer.io/api/timeseries?access_key=YOUR_API_KEY&start_date=2012-05-01&end_date=2012-05-25"
```

#### Response Body (200 OK)

```json
{
  "success": true,
  "timeseries": true,
  "start_date": "2012-05-01",
  "end_date": "2012-05-03",
  "base": "EUR",
  "rates": {
    "2012-05-01": {
      "USD": 1.322891,
      "AUD": 1.278047,
      "CAD": 1.302303
    },
    "2012-05-02": {
      "USD": 1.315066,
      "AUD": 1.274202,
      "CAD": 1.299083
    }
  }
}
```

#### Response Fields

| Field        | Description                                     |
|:-------------|:------------------------------------------------|
| `success`    | Boolean indicating if the request succeeded     |
| `timeseries` | Returns `true` for timeseries requests          |
| `start_date` | Start date of the time frame                    |
| `end_date`   | End date of the time frame                      |
| `base`       | Three-letter currency code of the base currency |
| `rates`      | Object mapping dates → rate objects             |

> **Note:** Weekend days are not included in the response as trading/markets are closed. Weekend data reflects the last
> available known trading/market data.

---

### 5.6 GET /fluctuation — Fluctuation Data

Retrieves information about how currencies fluctuate on a day-to-day basis between two dates.

**Availability:** Professional Plus and Enterprise plans.

#### Query Parameters

| Parameter    | Type   | Required | Description                                           |
|:-------------|:-------|:---------|:------------------------------------------------------|
| `access_key` | string | **Yes**  | Your API Key                                          |
| `start_date` | string | **Yes**  | Start date of the fluctuation timeframe (YYYY-MM-DD)  |
| `end_date`   | string | **Yes**  | End date of the fluctuation timeframe (YYYY-MM-DD)    |
| `base`       | string | No       | Three-letter currency code of preferred base currency |
| `symbols`    | string | No       | Comma-separated list of currency codes to filter      |

#### Request Example

```bash
curl "https://data.fixer.io/api/fluctuation?access_key=YOUR_API_KEY&start_date=2015-12-01&end_date=2015-12-24"
```

#### Response Body (200 OK)

```json
{
  "success": true,
  "fluctuation": true,
  "start_date": "2018-02-25",
  "end_date": "2018-02-26",
  "base": "EUR",
  "rates": {
    "USD": {
      "start_rate": 1.228952,
      "end_rate": 1.232735,
      "change": 0.0038,
      "change_pct": 0.3078
    },
    "JPY": {
      "start_rate": 131.587611,
      "end_rate": 131.651142,
      "change": 0.0635,
      "change_pct": 0.0483
    }
  }
}
```

#### Response Fields

| Field                         | Description                                      |
|:------------------------------|:-------------------------------------------------|
| `success`                     | Boolean indicating if the request succeeded      |
| `fluctuation`                 | Returns `true` for fluctuation requests          |
| `start_date`                  | Start date of the time frame                     |
| `end_date`                    | End date of the time frame                       |
| `base`                        | Three-letter currency code of the base currency  |
| `rates`                       | Object mapping currency codes → fluctuation data |
| `rates.{currency}.start_rate` | Rate at the start date                           |
| `rates.{currency}.end_rate`   | Rate at the end date                             |
| `rates.{currency}.change`     | Absolute change in rate                          |
| `rates.{currency}.change_pct` | Percentage change in rate                        |

---

## 6. Error Handling

### HTTP Status Codes

| Code    | Description                                              | Action                        |
|:--------|:---------------------------------------------------------|:------------------------------|
| **200** | Success                                                  | Process response              |
| **400** | Bad Request (timeseries, fluctuation)                    | Validate input parameters     |
| **401** | No API key specified or invalid API key                  | Check API key                 |
| **403** | Current subscription plan does not support this endpoint | Upgrade plan                  |
| **404** | Requested resource or endpoint does not exist            | Check endpoint path or symbol |

### Error Response Body

```json
{
  "success": false,
  "error": {
    "code": 401,
    "info": "No API Key was specified or an invalid API Key was specified."
  }
}
```

### Specific Error Codes

| Error Code | Description                                    |
|:-----------|:-----------------------------------------------|
| 604        | No or invalid amount specified (convert)       |
| 605        | No or invalid timeframe specified (timeseries) |
| 606        | Weekend data not included in response          |

---

## 7. Rate Limits & Pricing

| Plan             | Price     | Requests/Month | Update Frequency | Historical Data | Convert Endpoint | Base Currencies |
|:-----------------|:----------|:---------------|:-----------------|:----------------|:-----------------|:----------------|
| **Free**         | $0        | 100            | Hourly           | ❌               | ❌                | EUR only        |
| **Basic**        | $14.99/mo | 10,000         | Hourly           | ✅               | ✅                | All             |
| **Professional** | $59.99/mo | 100,000        | 10 minutes       | ✅               | ✅                | All             |

> **Note:** Free plan is **HTTP-only** (not HTTPS).

### Overages

Once you reach 100% of your monthly allowance, overage fees apply to ensure uninterrupted service. Users are notified
via email at 75%, 90%, and 100% of their allowance.

### Uptime

The Fixer API has maintained **99.99% uptime** over the last 12 months.

---

## 8. Response Format

All responses are returned in **standard JSON format** and can be parsed easily using any programming language.

### Common Response Fields

| Field       | Type    | Description                                     |
|:------------|:--------|:------------------------------------------------|
| `success`   | boolean | Indicates if the request succeeded              |
| `timestamp` | integer | UNIX timestamp of when rates were collected     |
| `base`      | string  | Three-letter currency code of the base currency |
| `date`      | string  | Date the rates were collected (YYYY-MM-DD)      |
| `rates`     | object  | Exchange rate data                              |

---

## 9. Code Examples

### Python

```python
import requests

url = "https://data.fixer.io/api/latest"
params = {
    "access_key": "YOUR_API_KEY",
    "base": "USD",
    "symbols": "EUR,GBP,JPY"
}

response = requests.get(url, params=params)
data = response.json()

if data["success"]:
    for currency, rate in data["rates"].items():
        print(f"1 USD = {rate} {currency}")
else:
    print(f"Error: {data['error']['info']}")
```

### JavaScript (Node.js)

```javascript
const url = new URL("https://data.fixer.io/api/latest");
url.searchParams.append("access_key", "YOUR_API_KEY");
url.searchParams.append("base", "USD");
url.searchParams.append("symbols", "EUR,GBP,JPY");

fetch(url)
    .then(res => res.json())
    .then(data => {
        if (data.success) {
            Object.entries(data.rates).forEach(([currency, rate]) => {
                console.log(`1 USD = ${rate} ${currency}`);
            });
        } else {
            console.error(`Error: ${data.error.info}`);
        }
    })
    .catch(console.error);
```

### Historical Conversion Example

```javascript
// Convert 100 USD to INR using historical rates from 1999-01-01
const url = new URL("https://data.fixer.io/api/convert");
url.searchParams.append("access_key", "YOUR_API_KEY");
url.searchParams.append("from", "USD");
url.searchParams.append("to", "INR");
url.searchParams.append("amount", "100");
url.searchParams.append("date", "1999-01-01");

fetch(url)
    .then(res => res.json())
    .then(data => {
        if (data.success) {
            console.log(`100 USD on ${data.date} = ${data.result} INR`);
        }
    });
```

---

## 10. Trading Platform Integration Notes

| Consideration            | Recommendation                                                                     |
|:-------------------------|:-----------------------------------------------------------------------------------|
| **Data Type**            | Midpoint rates (average of Bid and Ask)                                            |
| **Update Frequency**     | Free: Hourly, Basic: Hourly, Professional: 10 minutes                              |
| **Historical Depth**     | From January 1, 1999                                                               |
| **Use Case**             | Suitable for **daily reference rates**, backtesting, and non-critical applications |
| **Not Suitable For**     | High-frequency trading, real-time execution, or tick-by-tick strategies            |
| **Production Readiness** | Paid plans recommended for commercial use                                          |

### Recommended Retry Logic

```
Retry 1: wait 1s
Retry 2: wait 2s
Retry 3: wait 4s
Max Retries: 3
If all fail: Log error and resume
```

### Cache Strategy

| Data Type        | Cache TTL                  |
|:-----------------|:---------------------------|
| Latest rates     | 5–10 minutes               |
| Historical rates | 24 hours (or indefinitely) |
| Timeseries       | 24 hours                   |
| Symbols          | 7 days                     |

---

## 11. Important Notes

| Note                      | Details                                      |
|:--------------------------|:---------------------------------------------|
| **Default Base**          | EUR                                          |
| **Free Plan Limitations** | 100 requests/month, EUR base only, HTTP-only |
| **Historical Data**       | Only available on paid plans                 |
| **Conversion Endpoint**   | Only available on paid plans                 |
| **Timeseries**            | Professional plan+; max 365 days per request |
| **Fluctuation**           | Professional Plus+ plan                      |
| **Weekend Data**          | Not included in timeseries responses         |
| **Data Source**           | 15+ financial data providers and banks       |