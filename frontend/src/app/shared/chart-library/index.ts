/**
 * Public API barrel for the detachable Chart Library.
 *
 * ## In-app usage (Angular standalone)
 * ```ts
 * import { CandlestickChartComponent } from '../../shared/chart-library';
 * import type { OhlcvBar, IndicatorSeries, ChartDrawing } from '../../shared/chart-library';
 * ```
 *
 * ## Third-party / iframe embed (secret key)
 * Host the chart without a user JWT:
 * ```html
 * <iframe
 *   src="https://YOUR_APP/embed/chart?key=YOUR_EMBED_SECRET&symbol=BTCUSDT&timeframe=1h&theme=dark"
 *   width="100%" height="480" style="border:0" allow="clipboard-write"></iframe>
 * ```
 * Configure secrets in Gateway config: `embed.api-keys` / env `EMBED_API_KEYS`
 * (comma-separated). The iframe page sends `X-Embed-Key` to the Gateway for
 * read-only OHLCV + indicator endpoints.
 *
 * The component is standalone — no NgModule wrapper needed.
 */

export { CandlestickChartComponent } from './candlestick-chart/candlestick-chart.component';
export { BarChartComponent } from './bar-chart/bar-chart.component';

// Models
export type { OhlcvBar } from './models/ohlcv.model';
export type {
  IndicatorSeries,
  IndicatorType,
} from './models/indicator-series.model';
export type {
  ChartDrawing,
  DrawingChangedEvent,
  DrawingToolType,
  DrawingPoint,
  DrawingAction,
} from './models/drawing.model';
export type { BarChartDatum } from './models/bar-chart-datum.model';
