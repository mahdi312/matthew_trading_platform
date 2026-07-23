import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CHARTS_API, INDICATORS_API, MARKET_API } from '../../core/api/api-paths';
import type { OhlcvBar } from '../../shared/chart-library';
import {
  ApiChartDrawing,
  DrawingLayout,
  GlobalDrawingSettings,
  IndicatorConfig,
  IndicatorResult,
  SaveDrawingRequest,
  CreateLayoutRequest,
} from './charting.models';

/**
 * HTTP client for ChartingModule — wraps all market-service chart endpoints.
 *
 * Endpoints consumed (all Gateway-routed via environment.gatewayBaseUrl):
 *
 * Historical OHLCV:
 *   GET    /api/market/ohlcv/:symbol?timeframe=X&limit=Y
 *
 * Drawings:
 *   GET    /api/charts/drawings?symbol=X[&layoutId=Y]
 *   POST   /api/charts/drawings
 *   PUT    /api/charts/drawings/:id
 *   DELETE /api/charts/drawings/:id
 *
 * Layouts:
 *   GET    /api/charts/layouts?symbol=X
 *   POST   /api/charts/layouts
 *   GET    /api/charts/layouts/settings
 *   PUT    /api/charts/layouts/settings
 *
 * Indicators:
 *   GET    /api/indicators/configs?symbol=X
 *   POST   /api/indicators/configs
 *   PUT    /api/indicators/configs/:id
 *   DELETE /api/indicators/configs/:id
 *   GET    /api/indicators/:symbol/compute
 */
@Injectable({ providedIn: 'root' })
export class ChartingApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.gatewayBaseUrl;

  // ── Historical OHLCV ──────────────────────────────────────────────────────

  /**
   * GET /api/market/ohlcv/:symbol?timeframe=X&limit=Y
   * Returns historical candlestick bars for the symbol/timeframe combination.
   */
  getOhlcv(symbol: string, timeframe = '1h', limit = 200): Observable<OhlcvBar[]> {
    const params = new HttpParams()
      .set('timeframe', timeframe)
      .set('limit', limit.toString());
    return this.http.get<OhlcvBar[]>(
      `${this.base}${MARKET_API}/ohlcv/${encodeURIComponent(symbol)}`,
      { params }
    );
  }

  // ── Drawings ──────────────────────────────────────────────────────────────

  getDrawings(symbol: string, layoutId?: string): Observable<ApiChartDrawing[]> {
    let params = new HttpParams().set('symbol', symbol);
    if (layoutId) params = params.set('layoutId', layoutId);
    return this.http.get<ApiChartDrawing[]>(`${this.base}${CHARTS_API}/drawings`, { params });
  }

  createDrawing(req: SaveDrawingRequest): Observable<ApiChartDrawing> {
    return this.http.post<ApiChartDrawing>(`${this.base}${CHARTS_API}/drawings`, req);
  }

  updateDrawing(id: string, req: Partial<SaveDrawingRequest>): Observable<ApiChartDrawing> {
    return this.http.put<ApiChartDrawing>(`${this.base}${CHARTS_API}/drawings/${id}`, req);
  }

  deleteDrawing(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}${CHARTS_API}/drawings/${id}`);
  }

  // ── Layouts ───────────────────────────────────────────────────────────────

  getLayouts(symbol: string): Observable<DrawingLayout[]> {
    const params = new HttpParams().set('symbol', symbol);
    return this.http.get<DrawingLayout[]>(`${this.base}${CHARTS_API}/layouts`, { params });
  }

  createLayout(req: CreateLayoutRequest): Observable<DrawingLayout> {
    return this.http.post<DrawingLayout>(`${this.base}${CHARTS_API}/layouts`, req);
  }

  getGlobalSettings(): Observable<GlobalDrawingSettings> {
    return this.http.get<GlobalDrawingSettings>(`${this.base}${CHARTS_API}/layouts/settings`);
  }

  updateGlobalSettings(settings: Partial<GlobalDrawingSettings>): Observable<GlobalDrawingSettings> {
    return this.http.put<GlobalDrawingSettings>(`${this.base}${CHARTS_API}/layouts/settings`, settings);
  }

  // ── Indicator configs ─────────────────────────────────────────────────────

  getIndicatorConfigs(symbol: string): Observable<IndicatorConfig[]> {
    const params = new HttpParams().set('symbol', symbol);
    return this.http.get<IndicatorConfig[]>(`${this.base}${INDICATORS_API}/configs`, { params });
  }

  createIndicatorConfig(config: Omit<IndicatorConfig, 'id'>): Observable<IndicatorConfig> {
    return this.http.post<IndicatorConfig>(`${this.base}${INDICATORS_API}/configs`, config);
  }

  updateIndicatorConfig(id: string, config: Partial<IndicatorConfig>): Observable<IndicatorConfig> {
    return this.http.put<IndicatorConfig>(`${this.base}${INDICATORS_API}/configs/${id}`, config);
  }

  deleteIndicatorConfig(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}${INDICATORS_API}/configs/${id}`);
  }

  // ── Compute ───────────────────────────────────────────────────────────────

  /**
   * GET /api/indicators/:symbol/compute
   * Returns computed series data for all enabled indicator configs for the symbol.
   */
  computeIndicators(symbol: string): Observable<IndicatorResult[]> {
    return this.http.get<IndicatorResult[]>(`${this.base}${INDICATORS_API}/${symbol}/compute`);
  }
}
