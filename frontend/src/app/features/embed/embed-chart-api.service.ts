import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { MARKET_API, INDICATORS_API } from '../../core/api/api-paths';
import type { OhlcvBar } from '../../shared/chart-library';

/**
 * Read-only market data client for the detachable embed chart.
 * Authenticates with an embed API key instead of a user JWT.
 */
@Injectable()
export class EmbedChartApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.gatewayBaseUrl;

  private headers(embedKey: string): HttpHeaders {
    return new HttpHeaders({ 'X-Embed-Key': embedKey });
  }

  getOhlcv(
    embedKey: string,
    symbol: string,
    timeframe = '1h',
    limit = 200
  ): Observable<OhlcvBar[]> {
    const url =
      `${this.base}${MARKET_API}/ohlcv/${encodeURIComponent(symbol)}` +
      `?timeframe=${encodeURIComponent(timeframe)}&limit=${limit}`;
    return this.http.get<OhlcvBar[]>(url, { headers: this.headers(embedKey) });
  }

  computeIndicators(
    embedKey: string,
    symbol: string,
    timeframe = '1h',
    limit = 200
  ): Observable<unknown> {
    const url =
      `${this.base}${INDICATORS_API}/${encodeURIComponent(symbol)}/compute` +
      `?timeframe=${encodeURIComponent(timeframe)}&limit=${limit}`;
    return this.http.get(url, { headers: this.headers(embedKey) });
  }
}
