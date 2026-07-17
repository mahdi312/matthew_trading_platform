import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { MARKET_API } from '../../../core/api/api-paths';

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
 * Thin HTTP wrapper for the three watchlist endpoints on market-service.
 *
 * GET    /api/market/watchlist
 * POST   /api/market/watchlist
 * DELETE /api/market/watchlist/{symbol}
 *
 * The auth interceptor attaches the JWT; the gateway forwards X-User-Id.
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
}
