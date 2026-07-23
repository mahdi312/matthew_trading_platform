/**
 * A single bar in `BarChartComponent`.
 *
 * Used by screens that need a categorical bar chart with per-bar bull/bear
 * coloring (e.g. Reports' monthly net P&L breakdown) — kept in the shared
 * Chart Library so no feature screen re-implements bar-chart rendering.
 */
export interface BarChartDatum {
  /** X-axis category label (e.g. a month abbreviation like "Jan"). */
  label: string;

  /**
   * Bar value. When `colorBySign` is true (default) on the host component,
   * the sign of this value determines bull (>= 0) / bear (< 0) coloring.
   */
  value: number;
}
