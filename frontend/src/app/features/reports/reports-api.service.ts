import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { REPORTS_API } from '../../core/api/api-paths';
import { YearlyReport } from './reports.models';

/**
 * HTTP client for ReportsModule — wraps trading-service report endpoints.
 *
 * Endpoints consumed (all Gateway-routed via environment.gatewayBaseUrl):
 *
 *   GET /api/reports/yearly          → YearlyReport
 *   GET /api/reports/export.xlsx     → Blob  (triggers browser file download)
 *
 * The export endpoint uses responseType: 'blob' so the browser can save the
 * file to disk rather than opening it in-app.
 */
@Injectable({ providedIn: 'root' })
export class ReportsApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.gatewayBaseUrl;

  /**
   * Fetches the yearly performance report.
   * @param year 4-digit year (defaults to current year if omitted).
   */
  getYearlyReport(year?: number): Observable<YearlyReport> {
    let params = new HttpParams();
    if (year) params = params.set('year', year);
    return this.http.get<YearlyReport>(`${this.base}${REPORTS_API}/yearly`, { params });
  }

  /**
   * Downloads the Excel export as a Blob.
   *
   * The caller (ReportsPageComponent) is responsible for triggering the
   * browser download via a temporary `<a>` element — this method only
   * returns the raw Blob Observable.
   *
   * @param year 4-digit year (optional).
   */
  exportExcel(year?: number): Observable<Blob> {
    let params = new HttpParams();
    if (year) params = params.set('year', year);
    return this.http.get(`${this.base}${REPORTS_API}/export.xlsx`, {
      params,
      responseType: 'blob',
    });
  }
}
