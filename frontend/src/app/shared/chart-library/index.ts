/**
 * Public API barrel for `ChartLibraryModule`.
 *
 * Consumers import from here:
 * ```ts
 * import { CandlestickChartComponent } from '../../shared/chart-library';
 * import type { OhlcvBar, IndicatorSeries, ChartDrawing } from '../../shared/chart-library';
 * ```
 *
 * The component is standalone — no NgModule wrapper needed.
 * Import it directly in any standalone component's `imports: []` array.
 */

export { CandlestickChartComponent } from './candlestick-chart/candlestick-chart.component';

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
