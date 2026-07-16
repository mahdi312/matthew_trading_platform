import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AI_API } from '../../core/api/api-paths';
import {
  AiSummary,
  AiSignal,
  JournalCritiqueRequest,
  JournalCritique,
} from './ai-insights.models';

/**
 * HTTP client for AiInsightsModule — wraps all ai-service endpoints.
 *
 * Endpoints consumed (all Gateway-routed via environment.gatewayBaseUrl):
 *
 *   GET  /api/ai/summary/{symbol}     → AiSummary
 *   GET  /api/ai/signals/{symbol}     → AiSignal[]
 *   POST /api/ai/journal-critique     → JournalCritique
 *
 * Note: these are LLM-backed endpoints — responses may take several seconds.
 * Consumers must show a loading state until the observable resolves.
 *
 * The auth interceptor attaches the JWT automatically.
 */
@Injectable({ providedIn: 'root' })
export class AiInsightsApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.gatewayBaseUrl;

  /**
   * Fetches an AI-generated market summary for a symbol.
   * LLM call — may be slow (2–15 s).
   */
  getSummary(symbol: string): Observable<AiSummary> {
    return this.http.get<AiSummary>(`${this.base}${AI_API}/summary/${symbol}`);
  }

  /**
   * Fetches AI-generated trade signals for a symbol.
   * LLM call — may be slow.
   */
  getSignals(symbol: string): Observable<AiSignal[]> {
    return this.http.get<AiSignal[]>(`${this.base}${AI_API}/signals/${symbol}`);
  }

  /**
   * Requests an AI critique of a specific journal trade.
   * LLM call — may be slow.
   */
  critiqueJournalEntry(req: JournalCritiqueRequest): Observable<JournalCritique> {
    return this.http.post<JournalCritique>(`${this.base}${AI_API}/journal-critique`, req);
  }
}
