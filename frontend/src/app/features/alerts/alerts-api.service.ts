import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ALERTS_API } from '../../core/api/api-paths';
import { PriceAlert, SaveAlertRequest } from './alerts.models';

/**
 * HTTP client for AlertsModule — wraps all alert-service endpoints.
 *
 * Endpoints consumed (all Gateway-routed via environment.gatewayBaseUrl):
 *
 *   GET    /api/alerts           → PriceAlert[]   (list all for current user)
 *   POST   /api/alerts           → PriceAlert     (create)
 *   PUT    /api/alerts/:id       → PriceAlert     (update)
 *   DELETE /api/alerts/:id       → void           (delete)
 *
 * The auth interceptor attaches the JWT automatically.
 * 401 handling (redirect to /login) is done globally — this service
 * does not repeat it.
 */
@Injectable({ providedIn: 'root' })
export class AlertsApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.gatewayBaseUrl;

  /** Returns all price alerts belonging to the authenticated user. */
  getAlerts(): Observable<PriceAlert[]> {
    return this.http.get<PriceAlert[]>(`${this.base}${ALERTS_API}`);
  }

  /** Creates a new price alert. */
  createAlert(req: SaveAlertRequest): Observable<PriceAlert> {
    return this.http.post<PriceAlert>(`${this.base}${ALERTS_API}`, req);
  }

  /** Updates an existing alert (e.g., change target value, re-enable). */
  updateAlert(id: string, req: Partial<SaveAlertRequest>): Observable<PriceAlert> {
    return this.http.put<PriceAlert>(`${this.base}${ALERTS_API}/${id}`, req);
  }

  /** Permanently deletes an alert. */
  deleteAlert(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}${ALERTS_API}/${id}`);
  }
}
