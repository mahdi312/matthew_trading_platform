# Migration Completion Status (V2.0.5)

Living checklist for closing the desktop → microservices → Angular migration.

## 1. Abstraction layers (backend)

| Contract | Registry | Real impl | NoOp fallback | Status |
|----------|----------|-----------|---------------|--------|
| `OhlcvDataProvider` | market-service + ai/alert Feign adapters | Multi-provider + BitUnix | Yes | Done |
| `FundamentalsProvider` | reference-data | Finnhub → AV | Yes | Done |
| `NewsProvider` | reference-data | AV → Finnhub | Yes | Done |
| `SentimentProvider` | reference-data | CMC → CoinGecko | Yes | Done |
| `EconomicCalendarProvider` | reference-data | AV → Finnhub | Yes | Done |
| `SymbolSearchProvider` | reference-data | AV → Finnhub → TD → CG | Yes | Done |
| `NftDataProvider` | reference-data | CoinGecko | Yes | Done |
| `DeFiDataProvider` | reference-data | CoinGecko | Yes | **Added** |
| `AiAnalysisProvider` | ai-service | LLM impl | Yes | Done |
| `MarketDataProvider` / `TradingProvider` | broker registry | BitUnix | — | Done (single broker) |

**Also fixed:** `alert-service` now uses Feign `MarketServiceOhlcvProvider` → market-service (was NoOp-only).

## 2. Angular UI

All feature routes exist (dashboard → admin). Recent polish:
- Shell typography / atmospheric background
- Detachable chart embed at `/embed/chart`
- Chart library `showToolbar` for read-only hosts

## 3. Detachable chart module

- Presentation library: `frontend/src/app/shared/chart-library/`
- Embed host: `/embed/chart?key=SECRET&symbol=BTCUSDT&timeframe=1h`
- Gateway auth: `X-Embed-Key` / `?embedKey=` validated against `embed.api-keys` (`EMBED_API_KEYS` env)
- Allowed embed paths: OHLCV, symbols, indicators, chart layouts (read)

## 4. Desktop thin client (Step 12)

| Screen | Gateway client | Status |
|--------|----------------|--------|
| Login / Register | IdentityApiClient | Done |
| Dashboard / Trade entry | TradeApiClient | Done |
| Alerts | AlertApiClient | Done |
| AI News | AiApiClient | Done |
| NFT / DeFi / On-chain pools | ReferenceDataApiClient | Done |
| Charting / Indicator mixer | MarketApiClient exists | **Still local services** |
| Profile / Admin / Export / Yearly | — | **Still local** |
| MainDashboard shell | mixed | **Partial** |

**Remaining for full thin client:**
1. Wire `ChartController` + `IndicatorMixerController` to `MarketApiClient`
2. Wire Profile / Admin / Export / Yearly to Identity + Trading report APIs
3. Strip `desktop/pom.xml` fat deps (JPA, SQLite, mail, Telegram, POI, ta4j)
4. Delete ported `desktop/.../service/**` packages

## 5. Embed key (local default)

```
EMBED_API_KEYS=dev-embed-key-change-me
```

Iframe example:

```html
<iframe
  src="http://localhost:4200/embed/chart?key=dev-embed-key-change-me&symbol=BTCUSDT&timeframe=1h"
  width="100%" height="480" style="border:0"></iframe>
```
