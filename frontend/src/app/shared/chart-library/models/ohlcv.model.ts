/**
 * A single OHLCV (Open/High/Low/Close/Volume) bar.
 *
 * Timestamp is an ISO-8601 string or epoch milliseconds — ECharts accepts
 * both when the x-axis type is set to `'time'`.
 */
export interface OhlcvBar {
  timestamp: string | number;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}
