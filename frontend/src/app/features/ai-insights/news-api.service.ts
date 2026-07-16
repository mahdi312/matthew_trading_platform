import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { REFERENCE_API } from '../../core/api/api-paths';

/**
 * HTTP client for GET /api/reference/news.
 *
 * Calls reference-data-service (via the Gateway) directly —
 * NOT through ai-service. Raw news articles need no LLM processing;
 * routing them through AI synthesis would be incorrect.
 *
 * Endpoints consumed:
 *   GET /api/reference/news?symbol=...&assetClass=...&limit=20
 */
@Injectable({ providedIn: 'root' })
export class NewsApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.gatewayBaseUrl;

  /**
   * Fetch raw news articles for a symbol.
   *
   * @param symbol     Canonical ticker (e.g. "BTCUSDT", "AAPL").
   * @param assetClass "STOCK" | "CRYPTO" | "FOREX" — default "CRYPTO".
   * @param limit      Max articles to return (default 20).
   */
  getNews(symbol: string, assetClass: string = 'CRYPTO', limit: number = 20): Observable<NewsArticleDto[]> {
    let params = new HttpParams()
      .set('assetClass', assetClass)
      .set('limit', String(limit));
    if (symbol?.trim()) params = params.set('symbol', symbol.trim());

    return this.http.get<NewsArticleDto[]>(`${this.base}${REFERENCE_API}/news`, { params });
  }
}

// ── DTO shape — mirrors NewsArticleDto on the backend ────────────────────────

export interface NewsArticleDto {
  articleId:      string | null;
  providerName:   string | null;

  // Content
  title:          string;
  summary:        string | null;
  url:            string | null;
  imageUrl:       string | null;
  source:         string | null;
  author:         string | null;
  language:       string | null;

  // Timing
  publishedAt:    string | null;   // ISO-8601 Instant
  updatedAt:      string | null;

  // Classification
  assetClasses:   string[];
  relatedSymbols: string[];
  categories:     string[];

  // Sentiment (pre-computed by provider; may be null)
  sentimentScore:  number | null;
  sentimentLabel:  string | null;
}
