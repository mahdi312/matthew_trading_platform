import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { TRADES_API, PORTFOLIO_API } from './api-paths';

/** ── DTOs (shared by LiveTradingModule and JournalModule) ─────────────── */

export interface Trade {
  id: number;
  symbol: string;
  /** 'BUY' | 'SELL' */
  side: string;
  /** 'SPOT' | 'FUTURES' */
  type: string;
  entryPrice: number;
  quantity: number;
  /** null while open */
  exitPrice: number | null;
  /** null while open */
  pnl: number | null;
  /** 'OPEN' | 'CLOSED' */
  status: string;
  openedAt: string;
  closedAt: string | null;
  notes: string | null;
  broker: string | null;
}

export interface PagedTrades {
  content: Trade[];
  totalElements: number;
  totalPages: number;
  number: number;   // zero-based page index
  size: number;
}

/** Payload for opening a new trade. */
export interface CreateTradeRequest {
  symbol: string;
  side: 'BUY' | 'SELL';
  type: 'SPOT' | 'FUTURES';
  entryPrice: number;
  quantity: number;
  broker?: string;
  notes?: string;
}

/** Payload for closing an open trade. */
export interface CloseTradeRequest {
  exitPrice: number;
}

/** Filter options for the journal trade list. */
export interface TradeFilter {
  status?: 'OPEN' | 'CLOSED' | 'ALL';
  symbol?: string;
  from?: string;   // ISO-8601 date
  to?: string;     // ISO-8601 date
  page?: number;
  size?: number;
}

/**
 * Shared HTTP client for `/api/trades/**` and `/api/portfolio/**`.
 *
 * Used by both `LiveTradingModule` (order entry + open positions) and
 * `JournalModule` (trade history, filtering, close action). Building it
 * once here avoids duplicating the HttpClient calls in both feature modules.
 *
 * The auth interceptor attaches the JWT automatically; 401s redirect to
 * /login via the global error interceptor — no extra handling needed here.
 */
@Injectable({ providedIn: 'root' })
export class TradeApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.gatewayBaseUrl;

  // ── Trade CRUD ──────────────────────────────────────────────────────────

  /** POST /api/trades — opens a new trade. */
  openTrade(req: CreateTradeRequest): Observable<Trade> {
    return this.http.post<Trade>(`${this.base}${TRADES_API}`, req);
  }

  /**
   * GET /api/trades — paginated list with optional filters.
   * Supports status, symbol, date range, and pagination params.
   */
  getTrades(filter: TradeFilter = {}): Observable<PagedTrades> {
    let params = new HttpParams();
    if (filter.status && filter.status !== 'ALL') params = params.set('status', filter.status);
    if (filter.symbol) params = params.set('symbol', filter.symbol);
    if (filter.from)   params = params.set('from', filter.from);
    if (filter.to)     params = params.set('to', filter.to);
    if (filter.page !== undefined) params = params.set('page', filter.page);
    if (filter.size !== undefined) params = params.set('size', filter.size);
    return this.http.get<PagedTrades>(`${this.base}${TRADES_API}`, { params });
  }

  /** GET /api/trades/:id — single trade. */
  getTrade(id: number): Observable<Trade> {
    return this.http.get<Trade>(`${this.base}${TRADES_API}/${id}`);
  }

  /**
   * PATCH /api/trades/:id/close — closes an open trade.
   * Sends the exit price; trading-service computes P&L.
   */
  closeTrade(id: number, req: CloseTradeRequest): Observable<Trade> {
    return this.http.patch<Trade>(`${this.base}${TRADES_API}/${id}/close`, req);
  }

  /** DELETE /api/trades/:id — removes a draft/cancelled trade. */
  deleteTrade(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}${TRADES_API}/${id}`);
  }

  // ── Portfolio ────────────────────────────────────────────────────────────

  /** GET /api/portfolio/stats — KPI summary (re-exported so LiveTrading can show a mini-summary). */
  getPortfolioStats(): Observable<unknown> {
    return this.http.get(`${this.base}${PORTFOLIO_API}/stats`);
  }
}
