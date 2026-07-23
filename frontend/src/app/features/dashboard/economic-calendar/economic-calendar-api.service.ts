import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { REFERENCE_API } from '../../../core/api/api-paths';

/**
 * HTTP client for GET /api/reference/calendar.
 *
 * Calls reference-data-service (via the Gateway) to fetch upcoming
 * economic calendar events — no AI / LLM processing involved.
 */
@Injectable({ providedIn: 'root' })
export class EconomicCalendarApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.gatewayBaseUrl;

  /**
   * Fetch economic events.
   *
   * @param from        Start date (YYYY-MM-DD). Defaults to today on the server.
   * @param to          End date (YYYY-MM-DD). Defaults to today + 30 days.
   * @param assetClass  Filter by asset class relevance ("STOCK" | "FOREX" | "CRYPTO").
   * @param impactLevel Filter by impact ("HIGH" | "MEDIUM" | "LOW"). Leave blank for all.
   */
  getCalendar(
    from?: string,
    to?: string,
    assetClass: string = 'STOCK',
    impactLevel?: string
  ): Observable<EconomicEventDto[]> {
    let params = new HttpParams().set('assetClass', assetClass);
    if (from)        params = params.set('from', from);
    if (to)          params = params.set('to', to);
    if (impactLevel) params = params.set('impactLevel', impactLevel);

    return this.http.get<EconomicEventDto[]>(`${this.base}${REFERENCE_API}/calendar`, { params });
  }
}

// ── DTO shape — mirrors EconomicEventDto on the backend ──────────────────────

export interface EconomicEventDto {
  eventId:              string;
  providerName:         string | null;

  title:                string;
  description:          string | null;
  country:              string | null;
  category:             string | null;

  scheduledAt:          string | null;   // ISO-8601 Instant
  actualReleasedAt:     string | null;
  isTimeTentative:      boolean;

  impactLevel:          string | null;   // "HIGH" | "MEDIUM" | "LOW"
  affectedAssetClasses: string[];
  affectedCurrencies:   string[];

  forecast:             string | null;
  previous:             string | null;
  actual:               string | null;
  surprise:             string | null;

  isRecurring:          boolean;
  isReleased:           boolean;
  isRevised:            boolean;
}
