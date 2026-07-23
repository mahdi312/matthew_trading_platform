/**
 * Drawing tool types matching the `DrawingLayout` / `GlobalDrawingSettings`
 * backend model in `market-service` (persisted via `/api/charts/drawings`).
 *
 * The `CandlestickChartComponent` is presentation-only — it emits
 * `(drawingChanged)` with a `ChartDrawing` payload whenever the user
 * creates, moves, or deletes a drawing.  The host module (e.g.
 * `ChartingModule`) is responsible for persisting it via the API.
 */
export type DrawingToolType =
  | 'TREND_LINE'
  | 'FIBONACCI'
  | 'RECTANGLE'
  | 'HORIZONTAL_LINE'
  | 'VERTICAL_LINE';

export interface DrawingPoint {
  timestamp: string | number;
  price: number;
}

export interface ChartDrawing {
  /** Stable ID generated client-side (uuid) or assigned by the backend after save. */
  id: string;

  type: DrawingToolType;

  /** Ordered list of anchor points (2 for lines, 4 for rectangles). */
  points: DrawingPoint[];

  /** Fibonacci levels — only used when type === 'FIBONACCI'. */
  fibLevels?: number[];

  /** Optional style overrides. */
  color?: string;
  lineWidth?: number;
  label?: string;
}

export type DrawingAction = 'created' | 'updated' | 'deleted';

export interface DrawingChangedEvent {
  action: DrawingAction;
  drawing: ChartDrawing;
}
