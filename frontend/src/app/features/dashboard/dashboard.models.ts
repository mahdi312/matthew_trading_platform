/**
 * Domain models for the Dashboard feature module.
 *
 * These mirror the DTOs returned by trading-service's
 * PortfolioController and TradeController.
 */

/** Response from GET /api/portfolio/stats */
export interface PortfolioStats {
  /** Total realised + unrealised profit/loss in account currency. */
  totalPnl: number;
  /** Win rate as a decimal fraction, e.g. 0.65 = 65 %. */
  winRate: number;
  /** Total number of trades (open + closed). */
  tradeCount: number;
  /** Total number of currently open positions. */
  openPositions: number;
  /**
   * Historical equity curve data points.
   * Each entry is [ISO-8601 timestamp, equity value].
   */
  equityCurve: [string, number][];
}

/** A single trade summary row (subset of Trade entity). */
export interface TradeSummary {
  id: number;
  symbol: string;
  /** 'BUY' | 'SELL' */
  side: string;
  entryPrice: number;
  quantity: number;
  /** null if still open */
  exitPrice: number | null;
  /** null if still open */
  pnl: number | null;
  /** 'OPEN' | 'CLOSED' */
  status: string;
  openedAt: string;
  closedAt: string | null;
}

/** Response envelope from GET /api/trades?limit=N */
export interface TradesResponse {
  content: TradeSummary[];
  totalElements: number;
  totalPages: number;
}
