/**
 * Domain models for ReportsModule.
 *
 * These mirror the DTOs from trading-service's ReportsController:
 *
 *   GET /api/reports/yearly           → YearlyReport
 *   GET /api/reports/export.xlsx      → Blob  (Excel download)
 */

/** A single month's performance summary within a yearly report. */
export interface MonthlyBreakdown {
  month:      number;   // 1–12
  trades:     number;
  wins:       number;
  losses:     number;
  grossPnl:   number;
  netPnl:     number;
  winRate:    number;   // 0–1
  avgRrr:     number;   // avg risk-reward ratio
}

/** Full yearly performance report returned by /api/reports/yearly. */
export interface YearlyReport {
  year:          number;
  totalTrades:   number;
  totalWins:     number;
  totalLosses:   number;
  grossPnl:      number;
  netPnl:        number;
  winRate:       number;   // 0–1
  avgRrr:        number;
  maxDrawdown:   number;   // absolute value in quote currency
  sharpeRatio:   number | null;
  months:        MonthlyBreakdown[];
}
