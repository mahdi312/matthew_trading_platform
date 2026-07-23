/**
 * A computed indicator series to overlay on the candlestick chart.
 *
 * The host module fetches the data from market-service's
 * `IndicatorController` (`/api/indicators/{symbol}/compute`) and passes
 * the result here as an `@Input()`.  The chart component itself does NOT
 * call any API — it is purely presentational.
 *
 * Supported indicator types match the compute endpoints documented in
 * `Migration_Guide_frontend.md` Step 3 / Step 6 (ChartingModule).
 */
export type IndicatorType =
  | 'SMA'
  | 'EMA'
  | 'RSI'
  | 'MACD'
  | 'BOLLINGER'
  | 'ICHIMOKU';

export interface IndicatorSeries {
  /** Human-readable label shown in the legend. */
  name: string;

  /** Which indicator this series represents. */
  type: IndicatorType;

  /**
   * Data points: each element is [timestamp, value] or a simple number
   * array aligned index-for-index with the OHLCV bars.
   */
  data: ([string | number, number] | number)[];

  /** Optional hex color; defaults to an ECharts palette color. */
  color?: string;

  /**
   * When true (e.g. RSI, MACD), the series renders in a separate sub-pane
   * below the main candlestick area instead of overlaid on it.
   */
  subPane?: boolean;
}
