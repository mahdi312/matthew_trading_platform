/**
 * Domain models for the ChartingModule.
 *
 * These mirror the server-side DTOs from market-service:
 *   - ChartDrawing         → /api/charts/drawings
 *   - DrawingLayout        → /api/charts/layouts
 *   - GlobalDrawingSettings→ /api/charts/layouts/settings
 *   - IndicatorConfig      → /api/indicators/configs
 *   - IndicatorResult      → /api/indicators/{symbol}/compute
 *
 * The ChartDrawing here is the API payload — distinct from the
 * shared/chart-library/models/drawing.model.ts (which is the
 * presentation-layer type used by CandlestickChartComponent).
 * The charting API service maps between them.
 */

/** A single persisted drawing saved to market-service. */
export interface ApiChartDrawing {
  id: string;
  symbol: string;
  layoutId: string | null;
  /** DrawingToolType string — matches CandlestickChartComponent's enum. */
  type: string;
  points: { timestamp: string | number; price: number }[];
  color?: string;
  lineWidth?: number;
  label?: string;
  fibLevels?: number[];
  createdAt: string;
  updatedAt: string;
}

/** A named drawing layout (collection of drawings). */
export interface DrawingLayout {
  id: string;
  name: string;
  symbol: string;
  isGlobal: boolean;
  createdAt: string;
  updatedAt: string;
}

/** Global drawing settings (default layout reference). */
export interface GlobalDrawingSettings {
  defaultLayoutId: string | null;
  theme: 'dark' | 'light';
}

/** A user-configured indicator. */
export interface IndicatorConfig {
  id: string;
  symbol: string;
  /** e.g. 'SMA', 'EMA', 'RSI', 'MACD', 'BOLLINGER', 'ICHIMOKU' */
  type: string;
  parameters: Record<string, number | string>;
  color: string;
  /** Whether to render in a sub-pane below the main chart. */
  subPane: boolean;
  enabled: boolean;
}

/** Computed indicator values returned by market-service. */
export interface IndicatorResult {
  type: string;
  name: string;
  data: (number | null)[];
  color: string;
  subPane: boolean;
}

/** Request payload for saving/updating a drawing. */
export interface SaveDrawingRequest {
  symbol: string;
  layoutId?: string | null;
  type: string;
  points: { timestamp: string | number; price: number }[];
  color?: string;
  lineWidth?: number;
  label?: string;
  fibLevels?: number[];
}

/** Request payload for creating a new layout. */
export interface CreateLayoutRequest {
  name: string;
  symbol: string;
  isGlobal?: boolean;
}
