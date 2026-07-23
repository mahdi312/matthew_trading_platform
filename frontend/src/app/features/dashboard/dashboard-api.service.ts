import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PORTFOLIO_API, TRADES_API } from '../../core/api/api-paths';
import { PortfolioStats, TradesResponse } from './dashboard.models';

/**
 * Thin HTTP wrapper for the Dashboard screen.
 *
 * Calls:
 *  - GET {gateway}/api/portfolio/stats   → PortfolioStats
 *  - GET {gateway}/api/trades?limit=N    → TradesResponse
 *
 * The auth interceptor attaches the JWT automatically.
 * Error handling (401 → /login redirect) is done globally by the
 * auth interceptor — this service does not need to repeat it.
 */
@Injectable({ providedIn: 'root' })
export class DashboardApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.gatewayBaseUrl;

  /** Fetches portfolio KPI stats including the equity curve. */
  getPortfolioStats(): Observable<PortfolioStats> {
    return this.http.get<PortfolioStats>(`${this.base}${PORTFOLIO_API}/stats`);
  }

  /**
   * Fetches the N most recent trades.
   * @param limit Maximum number of trades to return (default 20).
   */
  getRecentTrades(limit = 20): Observable<TradesResponse> {
    const params = new HttpParams().set('limit', limit);
    return this.http.get<TradesResponse>(`${this.base}${TRADES_API}`, { params });
  }
}
