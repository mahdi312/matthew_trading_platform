import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { environment } from '../../../../environments/environment';
import { MARKET_API } from '../../../core/api/api-paths';
import type { OhlcvBar } from '../../../shared/chart-library';

/** Mirror of market-service WatchlistItemDto */
export interface WatchlistItem {
  id: number;
  symbol: string;
  assetClass: string;
  addedAt: string;
}

/** Request body for POST /api/market/watchlist */
export interface AddWatchlistRequest {
  symbol: string;
  assetClass?: string;
}

/**
 * Thin HTTP wrapper for watchlist + quote helpers on market-service.
 *
 * GET    /api/market/watchlist
 * POST   /api/market/watchlist
 * DELETE /api/market/watchlist/{symbol}
 * GET    /api/market/ohlcv/{symbol}  (used for previous-day close)
 */
@Injectable({ providedIn: 'root' })
export class WatchlistApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.gatewayBaseUrl;
  private readonly url  = `${this.base}${MARKET_API}/watchlist`;

  getWatchlist(assetClass?: string): Observable<WatchlistItem[]> {
    if (assetClass) {
      return this.http.get<WatchlistItem[]>(this.url, { params: { assetClass } });
    }
    return this.http.get<WatchlistItem[]>(this.url);
  }

  addSymbol(req: AddWatchlistRequest): Observable<WatchlistItem> {
    return this.http.post<WatchlistItem>(this.url, req);
  }

  removeSymbol(symbol: string): Observable<void> {
    return this.http.delete<void>(`${this.url}/${encodeURIComponent(symbol)}`);
  }

  /**
   * Previous daily close for day-change coloring.
   * Uses the second-to-last 1d bar when available; falls back to that bar's open.
   */
  getPreviousClose(symbol: string): Observable<number | null> {
    const url =
      `${this.base}${MARKET_API}/ohlcv/${encodeURIComponent(symbol)}` +
      `?timeframe=1d&limit=3`;
    return this.http.get<OhlcvBar[]>(url).pipe(
      map((bars) => {
        if (!bars?.length) return null;
        if (bars.length >= 2) {
          return Number(bars[bars.length - 2].close);
        }
        return Number(bars[0].open ?? bars[0].close);
      }),
      catchError(() => of(null)),
    );
  }

  /** Latest daily close — seed UI before WebSocket ticks arrive. */
  getLatestClose(symbol: string): Observable<number | null> {
    const url =
      `${this.base}${MARKET_API}/ohlcv/${encodeURIComponent(symbol)}` +
      `?timeframe=1d&limit=2`;
    return this.http.get<OhlcvBar[]>(url).pipe(
      map((bars) => {
        if (!bars?.length) return null;
        return Number(bars[bars.length - 1].close);
      }),
      catchError(() => of(null)),
    );
  }
}
