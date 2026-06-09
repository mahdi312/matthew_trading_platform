# Chart Indicators Reference

This document describes all technical indicators added to the trading platform and how they were implemented.

---

## Overview

All indicators are powered by **ta4j 0.22.1** (Technical Analysis for Java).  
Each indicator is represented as an `IndicatorDefinition` instance with:

| Property      | Description                                             |
|---------------|---------------------------------------------------------|
| `type`        | One of the 30+ indicator types defined in `IndicatorDefinition.Type` |
| `period`      | Primary period (number of bars)                        |
| `period2`     | Secondary period (slow MA, %D smoothing, etc.)         |
| `period3`     | Tertiary period (signal line, RSI period for StochRSI) |
| `priceSource` | Which price field to use: CLOSE, OPEN, HIGH, LOW, HL2 (median), HLC3 (typical), OHLC4 (mean) |
| `color`       | Line color as hex string (e.g. `#388bfd`)              |
| `lineWeight`  | Stroke width in pixels (0.5–5.0)                       |
| `visible`     | Whether the indicator is shown on the chart            |

Multiple instances of the same indicator type are fully supported (e.g. EMA(20) + EMA(50) simultaneously).

---

## Indicator Categories

### 📈 Moving Averages (Price Pane Overlay)

| ID       | Name                          | Class (ta4j)                        | Periods       | Notes |
|----------|-------------------------------|-------------------------------------|---------------|-------|
| EMA      | Exponential Moving Average    | `EMAIndicator`                      | period        | Most-used trend follower |
| SMA      | Simple Moving Average         | `SMAIndicator`                      | period        | Equal-weight average |
| WMA      | Weighted Moving Average       | `WMAIndicator`                      | period        | Recent bars weighted higher |
| DEMA     | Double EMA                    | `DoubleEMAIndicator`                | period        | Reduces lag vs EMA |
| TEMA     | Triple EMA                    | `TripleEMAIndicator`                | period        | Further lag reduction |
| HullMA   | Hull Moving Average           | `HMAIndicator`                      | period        | Low lag, smooth trend |
| KAMA     | Kaufman's Adaptive MA         | `KAMAIndicator`                     | period (ER), 2, 30 | Adapts to volatility |
| ZLEMA    | Zero-Lag EMA                  | `ZLEMAIndicator`                    | period        | Eliminates EMA lag |
| VWAP     | Volume-Weighted Average Price | `VWAPIndicator`                     | period        | Fair value reference |

---

### 📊 Bands & Channels (Price Pane Overlay)

| ID            | Name                    | Class (ta4j)                                                        | Periods           | Notes |
|---------------|-------------------------|---------------------------------------------------------------------|-------------------|-------|
| Bollinger     | Bollinger Bands         | `BollingerBandsUpperIndicator`, `Middle`, `Lower`                   | period, stdDev×10 | Cloud fill + 3 lines |
| Keltner       | Keltner Channel         | `KeltnerChannelUpperIndicator`, `Middle`, `Lower`                   | EMA period, ATR period | ATR-based channel |
| Donchian      | Donchian Channel        | `DonchianChannelUpperIndicator`, `Lower`, midline                   | period            | Highest high / lowest low |
| SAR           | Parabolic SAR           | `ParabolicSarIndicator`                                             | —                 | Drawn as dots; step=0.02, max=0.2 |

---

### ⚡ Momentum Oscillators (Sub Pane)

| ID         | Name                        | Class (ta4j)                                           | Periods                 | Notes |
|------------|-----------------------------|--------------------------------------------------------|-------------------------|-------|
| RSI        | Relative Strength Index     | `RSIIndicator`                                         | period                  | 70/30 overbought/oversold zones |
| MACD       | MACD                        | `MACDIndicator` + `getSignalLine()` + `getHistogram()` | fast, slow, signal      | Histogram + 2 lines |
| Stochastic | Stochastic Oscillator       | `StochasticOscillatorKIndicator`, `D`                  | K period, %D smoothing  | 80/20 zones |
| StochRSI   | Stochastic RSI              | `StochasticRSIIndicator`                               | stoch period, %D, RSI   | Combines RSI + Stochastic |
| CCI        | Commodity Channel Index     | `CCIIndicator`                                         | period                  | ±100 zones |
| Williams%R | Williams %R                 | `WilliamsRIndicator`                                   | period                  | −80/−20 zones, range −100..0 |
| ROC        | Rate of Change              | `ROCIndicator`                                         | period                  | Percent price change |
| DPO        | Detrended Price Oscillator  | `DPOIndicator`                                         | period                  | Removes long-term trend |
| Aroon      | Aroon Oscillator            | `AroonOscillatorIndicator`, `AroonUpIndicator`, `AroonDownIndicator` | period | Oscillator + up/down lines |
| CMO        | Chande Momentum Oscillator  | `CMOIndicator`                                         | period                  | Range ±100 |
| Fisher     | Fisher Transform            | `FisherIndicator`                                      | period                  | Normal distribution transform |
| PPO        | Percentage Price Oscillator | `PPOIndicator`                                         | fast, slow, signal      | MACD as % of slow EMA; histogram |

---

### 📉 Volatility (Sub Pane)

| ID          | Name              | Class (ta4j)          | Periods | Notes |
|-------------|-------------------|-----------------------|---------|-------|
| ATR         | Average True Range | `ATRIndicator`        | period  | Volatility measure |
| Ulcer       | Ulcer Index        | `UlcerIndexIndicator` | period  | Drawdown-based risk |

---

### 📦 Volume (Sub Pane)

| ID          | Name                    | Class (ta4j)                        | Periods              | Notes |
|-------------|-------------------------|-------------------------------------|----------------------|-------|
| OBV         | On-Balance Volume       | `OnBalanceVolumeIndicator`          | —                    | Cumulative buy/sell volume |
| MFI         | Money Flow Index        | `MoneyFlowIndexIndicator`           | period               | RSI of money flow |
| CMF         | Chaikin Money Flow      | `ChaikinMoneyFlowIndicator`         | period               | Accumulation/distribution |
| ChaikinOsc  | Chaikin Oscillator      | `ChaikinOscillatorIndicator`        | fast, slow           | EMA(ADL fast) − EMA(ADL slow) |

---

### 🎯 Trend Strength (Sub Pane)

| ID  | Name                        | Class (ta4j)                                         | Periods | Notes |
|-----|-----------------------------|------------------------------------------------------|---------|-------|
| ADX | Average Directional Index   | `ADXIndicator`, `PlusDIIndicator`, `MinusDIIndicator` | period  | 3 lines: ADX + ±DI |

---

### 🌩 Complex Overlays (Price Pane)

| ID              | Name                      | Class (ta4j)                                           | Periods                 | Notes |
|-----------------|---------------------------|--------------------------------------------------------|-------------------------|-------|
| Ichimoku        | Ichimoku Cloud            | `IchimokuTenkanSenIndicator`, `KijunSen`, `SenkouSpanA/B`, `Chikou` | Tenkan, Kijun, Senkou | Cloud fill (bull/bear) + 4 lines |
| S/R             | Support & Resistance      | `SupportResistanceService` (custom)                    | —                       | Fibonacci + pivot point levels |

---

## How Indicators Are Computed

1. **`IndicatorComputeService.compute(def, series)`** — called per `IndicatorDefinition`.
2. Values are extracted for every bar in the full history into `def.series` (and `def.extraSeries` for multi-line indicators).
3. The chart canvas reads `def.series` sliced to `[startBarIndex, startBarIndex+visibleBars)` using **absolute indexing** — this ensures zoom/pan never corrupts indicator positions.

---

## Adding a New Indicator

1. Add an entry to `IndicatorDefinition.Type` with a short name, display name, pane, and `isSingleLine` flag.
2. Add default period/color values in the `defaultPeriod*`, `defaultColor` methods of `IndicatorDefinition`.
3. Add a `case` in `IndicatorComputeService.compute()` calling a private method.
4. Add drawing logic in `CandlestickChartCanvas.drawSubPane()` or `drawPricePaneIndicatorLines()`.
5. Add the type to the appropriate category group in `IndicatorPickerDialog.CATEGORIES`.
6. Add parameter labels (`periodLabel`, `period2Label`, `period3Label`) in `IndicatorPickerDialog`.

---

## Removed Indicator Storage

Previous versions stored computed indicator series in separate DB tables  
(`BTCUSDT_EMA_1H`, etc.) via `IndicatorSeriesStorageService` and  
`DynamicIndicatorTableService`. This has been **removed**.

Indicators are now computed **on-the-fly from OHLCV bars** when the chart loads, making the system:
- Simpler (no schema migrations needed for new indicators)
- More accurate (no stale cached values)
- Instantly configurable (change periods without cache invalidation)

---

## Timeframes Supported

| Code  | Duration    | Status |
|-------|-------------|--------|
| `1m`  | 1 minute    | ✅     |
| `3m`  | 3 minutes   | ✅ (new) |
| `5m`  | 5 minutes   | ✅     |
| `15m` | 15 minutes  | ✅     |
| `30m` | 30 minutes  | ✅ (new) |
| `1h`  | 1 hour      | ✅     |
| `2h`  | 2 hours     | ✅ (new) |
| `4h`  | 4 hours     | ✅     |
| `6h`  | 6 hours     | ✅ (new) |
| `8h`  | 8 hours     | ✅ (new) |
| `12h` | 12 hours    | ✅ (new) |
| `1d`  | 1 day       | ✅     |
| `3d`  | 3 days      | ✅ (new) |
| `1w`  | 1 week      | ✅     |
| `1mo` | 1 month     | ✅ (new) |

All timeframes appear in the chart toolbar toggle buttons.
