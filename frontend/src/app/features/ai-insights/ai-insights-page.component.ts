import {
  Component,
  OnInit,
  inject,
  signal,
  computed,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDividerModule } from '@angular/material/divider';
import { MatTabsModule } from '@angular/material/tabs';
import { MatBadgeModule } from '@angular/material/badge';

import { AiInsightsApiService } from './ai-insights-api.service';
import { NewsApiService, NewsArticleDto } from './news-api.service';
import { TradeApiService } from '../../core/api/trade-api.service';
import { EmptyStateComponent } from '../../shared/empty-state/empty-state.component';
import {
  AiSummary,
  AiSignal,
  JournalCritiqueRequest,
  JournalCritique,
} from './ai-insights.models';

/**
 * AiInsightsModule — AI-powered market analysis and journal critique.
 *
 * Tabs:
 *  1. Market Summary — per-symbol LLM-generated market summary + sentiment.
 *  2. Trade Signals  — per-symbol AI buy/sell/hold signals with reasoning.
 *  3. Journal Critique — submit a trade for AI critique; shows rating + suggestions.
 *
 * All three tabs share a common symbol selector in the header.
 * LLM endpoints are slow (2–15 s) — each tab shows a spinner while loading.
 *
 * Route: /ai-insights  (behind authGuard)
 */
@Component({
  selector: 'app-ai-insights-page',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    RouterModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    MatChipsModule,
    MatTooltipModule,
    MatSnackBarModule,
    MatDividerModule,
    MatTabsModule,
    MatBadgeModule,
    EmptyStateComponent,
  ],
  templateUrl: './ai-insights-page.component.html',
  styleUrls: ['./ai-insights-page.component.scss'],
})
export class AiInsightsPageComponent implements OnInit {
  private readonly api     = inject(AiInsightsApiService);
  private readonly newsApi = inject(NewsApiService);
  private readonly tradeApi = inject(TradeApiService);
  private readonly snack   = inject(MatSnackBar);
  private readonly fb      = inject(FormBuilder);
  private readonly route   = inject(ActivatedRoute);

  // ── Symbol selector ───────────────────────────────────────────────────────

  readonly symbol       = signal<string>('BTCUSDT');
  readonly symbolInput  = signal<string>('BTCUSDT');

  readonly popularSymbols = ['BTCUSDT', 'ETHUSDT', 'BNBUSDT', 'SOLUSDT', 'XRPUSDT'];

  /** Selected tab index — auto-switched to the Critique tab (2) on a Journal "Ask AI" hand-off. */
  readonly selectedTabIndex = signal(0);

  // ── Summary tab ───────────────────────────────────────────────────────────

  readonly loadingSummary = signal(false);
  readonly summary        = signal<AiSummary | null>(null);
  readonly summaryError   = signal<string | null>(null);

  /** Rotating "the AI is thinking" status line, cycled while a summary request is in flight. */
  readonly summaryStatusLine = signal(0);

  // ── Signals tab ───────────────────────────────────────────────────────────

  readonly loadingSignals = signal(false);
  readonly signals        = signal<AiSignal[]>([]);
  readonly signalsError   = signal<string | null>(null);

  /** ids of signal cards whose reasoning has been expanded via "why?" */
  readonly expandedReasoning = signal<Set<string>>(new Set());

  // ── Journal critique tab ──────────────────────────────────────────────────

  readonly loadingCritique  = signal(false);
  readonly critique         = signal<JournalCritique | null>(null);
  readonly critiqueError    = signal<string | null>(null);

  /** True when this critique request arrived pre-filled from Journal's "Ask AI" hand-off. */
  readonly critiqueFromJournal = signal(false);

  // ── Latest News tab ───────────────────────────────────────────────────────

  readonly loadingNews  = signal(false);
  readonly newsArticles = signal<NewsArticleDto[]>([]);
  readonly newsError    = signal<string | null>(null);

  /** Asset class selector for the news tab. */
  readonly newsAssetClass = signal<string>('CRYPTO');

  readonly critiqueForm: FormGroup = this.fb.group({
    tradeId:    ['', Validators.required],
    symbol:     ['', Validators.required],
    side:       ['BUY', Validators.required],
    entryPrice: [null, [Validators.required, Validators.min(0)]],
    exitPrice:  [null],
    notes:      [''],
  });

  /** Rotating status lines shown while an LLM request is in flight — a first-class loading design, not a spinner. */
  private readonly statusLines = [
    'Reading the market…',
    'Weighing recent price action…',
    'Cross-checking sentiment…',
    'Drafting the summary…',
  ];
  private statusLineTimer: ReturnType<typeof setInterval> | null = null;

  private startStatusRotation(): void {
    this.summaryStatusLine.set(0);
    this.stopStatusRotation();
    this.statusLineTimer = setInterval(() => {
      this.summaryStatusLine.update(i => (i + 1) % this.statusLines.length);
    }, 1800);
  }

  private stopStatusRotation(): void {
    if (this.statusLineTimer) {
      clearInterval(this.statusLineTimer);
      this.statusLineTimer = null;
    }
  }

  statusLineText(): string {
    return this.statusLines[this.summaryStatusLine()];
  }

  // ── Lifecycle ─────────────────────────────────────────────────────────────

  ngOnInit(): void {
    this.fetchSummary();
    this.fetchSignals();
    this.fetchNews();
    this.applyJournalHandoff();
  }

  /**
   * Journal's "Ask AI" action navigates here with `?tradeId=&symbol=` query params.
   * When present: pre-fill the critique form from the trade (fetched fresh so we have
   * entry/exit/side/notes, not just the id+symbol carried in the URL), switch to the
   * Journal Critique tab, and submit automatically so the user lands on a result.
   */
  private applyJournalHandoff(): void {
    const params = this.route.snapshot.queryParamMap;
    const tradeId = params.get('tradeId');
    const symbolParam = params.get('symbol');
    if (!tradeId) return;

    this.critiqueFromJournal.set(true);
    this.selectedTabIndex.set(2); // Journal Critique tab

    if (symbolParam) {
      this.critiqueForm.patchValue({ tradeId, symbol: symbolParam.toUpperCase() });
    } else {
      this.critiqueForm.patchValue({ tradeId });
    }

    this.tradeApi.getTrade(Number(tradeId)).subscribe({
      next: (trade) => {
        this.critiqueForm.patchValue({
          tradeId:    String(trade.id),
          symbol:     trade.symbol,
          side:       trade.side === 'SELL' ? 'SELL' : 'BUY',
          entryPrice: trade.entryPrice,
          exitPrice:  trade.exitPrice,
          notes:      trade.notes ?? '',
        });
        this.onCritiqueSubmit();
      },
      error: (err) => {
        // Non-fatal — the form is still usable with the id/symbol we already have from the URL.
        console.warn('[AiInsights] could not fetch trade context for hand-off', err);
      },
    });
  }

  // ── Symbol change ─────────────────────────────────────────────────────────

  applySymbol(): void {
    const s = this.symbolInput().toUpperCase().trim();
    if (!s) return;
    this.symbol.set(s);
    // Refresh summary, signals, and news for new symbol
    this.fetchSummary();
    this.fetchSignals();
    this.fetchNews();
  }

  selectPopular(s: string): void {
    this.symbolInput.set(s);
    this.applySymbol();
  }

  // ── Summary ───────────────────────────────────────────────────────────────

  fetchSummary(): void {
    this.loadingSummary.set(true);
    this.summaryError.set(null);
    this.summary.set(null);
    this.startStatusRotation();

    this.api.getSummary(this.symbol()).subscribe({
      next:  (data) => {
        this.summary.set(data);
        this.loadingSummary.set(false);
        this.stopStatusRotation();
      },
      error: (err)  => {
        this.summaryError.set('The AI service didn\u2019t respond. It may be unavailable right now — try again in a moment.');
        this.loadingSummary.set(false);
        this.stopStatusRotation();
        console.error('[AiInsights] summary error', err);
      },
    });
  }

  // ── Signals ───────────────────────────────────────────────────────────────

  fetchSignals(): void {
    this.loadingSignals.set(true);
    this.signalsError.set(null);
    this.signals.set([]);

    this.api.getSignals(this.symbol()).subscribe({
      next:  (data) => { this.signals.set(data); this.loadingSignals.set(false); },
      error: (err)  => {
        this.signalsError.set('The AI service didn\u2019t respond. It may be unavailable right now — try again in a moment.');
        this.loadingSignals.set(false);
        console.error('[AiInsights] signals error', err);
      },
    });
  }

  /** Toggle the "why?" expand for a given signal's reasoning text. */
  toggleReasoning(signalId: string): void {
    this.expandedReasoning.update(set => {
      const next = new Set(set);
      if (next.has(signalId)) next.delete(signalId); else next.add(signalId);
      return next;
    });
  }

  isReasoningExpanded(signalId: string): boolean {
    return this.expandedReasoning().has(signalId);
  }

  // ── Journal critique ──────────────────────────────────────────────────────

  onCritiqueSubmit(): void {
    if (this.critiqueForm.invalid) return;
    this.loadingCritique.set(true);
    this.critiqueError.set(null);
    this.critique.set(null);

    const req: JournalCritiqueRequest = {
      tradeId:    this.critiqueForm.value.tradeId.trim(),
      symbol:     this.critiqueForm.value.symbol.toUpperCase().trim(),
      side:       this.critiqueForm.value.side,
      entryPrice: Number(this.critiqueForm.value.entryPrice),
      exitPrice:  this.critiqueForm.value.exitPrice ? Number(this.critiqueForm.value.exitPrice) : null,
      notes:      this.critiqueForm.value.notes?.trim() || null,
    };

    this.api.critiqueJournalEntry(req).subscribe({
      next: (data) => {
        this.critique.set(data);
        this.loadingCritique.set(false);
      },
      error: (err) => {
        this.critiqueError.set('The AI service didn\u2019t respond. It may be unavailable right now — try again in a moment.');
        this.loadingCritique.set(false);
        console.error('[AiInsights] critique error', err);
      },
    });
  }

  /** Clears the Journal hand-off context so the tab reverts to a manual critique form. */
  clearJournalHandoff(): void {
    this.critiqueFromJournal.set(false);
    this.critique.set(null);
    this.critiqueForm.reset({ tradeId: '', symbol: '', side: 'BUY', entryPrice: null, exitPrice: null, notes: '' });
  }

  // ── News ──────────────────────────────────────────────────────────────────

  /**
   * Fetch raw news articles from reference-data-service.
   * NOT routed through ai-service — this is raw data, no LLM processing.
   */
  fetchNews(): void {
    this.loadingNews.set(true);
    this.newsError.set(null);
    this.newsArticles.set([]);

    this.newsApi.getNews(this.symbol(), this.newsAssetClass(), 20).subscribe({
      next:  (articles) => { this.newsArticles.set(articles); this.loadingNews.set(false); },
      error: (err) => {
        this.newsError.set('Failed to load news. The reference data service may be unavailable.');
        this.loadingNews.set(false);
        console.error('[AiInsights] news error', err);
      },
    });
  }

  onNewsAssetClassChange(cls: string): void {
    this.newsAssetClass.set(cls);
    this.fetchNews();
  }

  /** Format a published date for display. */
  formatPublishedAt(iso: string | null): string {
    if (!iso) return '';
    const d = new Date(iso);
    const now = Date.now();
    const diffMs = now - d.getTime();
    const diffH  = diffMs / 3_600_000;
    if (diffH < 1)    return `${Math.round(diffMs / 60_000)}m ago`;
    if (diffH < 24)   return `${Math.round(diffH)}h ago`;
    return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
  }

  /**
   * Relative "Generated X minutes ago" phrasing for LLM output — since the summary/critique
   * text can go stale, an absolute timestamp alone under-communicates that.
   */
  relativeGeneratedAt(iso: string | null): string {
    if (!iso) return '';
    const d = new Date(iso);
    const diffMs = Date.now() - d.getTime();
    if (diffMs < 0) return 'just now';
    const diffMin = Math.floor(diffMs / 60_000);
    if (diffMin < 1)  return 'just now';
    if (diffMin < 60) return `${diffMin} minute${diffMin === 1 ? '' : 's'} ago`;
    const diffH = Math.floor(diffMin / 60);
    if (diffH < 24)   return `${diffH} hour${diffH === 1 ? '' : 's'} ago`;
    const diffD = Math.floor(diffH / 24);
    return `${diffD} day${diffD === 1 ? '' : 's'} ago`;
  }

  sentimentLabelColor(label: string | null): string {
    switch (label?.toUpperCase()) {
      case 'POSITIVE': return 'positive';
      case 'NEGATIVE': return 'negative';
      default:         return '';
    }
  }

  trackByArticleId(_: number, item: NewsArticleDto): string {
    return item.articleId ?? item.title ?? String(_);
  }

  // ── UI helpers ────────────────────────────────────────────────────────────

  sentimentColor(sentiment: string): string {
    switch (sentiment) {
      case 'BULLISH': return 'primary';
      case 'BEARISH': return 'warn';
      default:        return '';
    }
  }

  sentimentIcon(sentiment: string): string {
    switch (sentiment) {
      case 'BULLISH': return 'trending_up';
      case 'BEARISH': return 'trending_down';
      default:        return 'trending_flat';
    }
  }

  signalColor(type: string): string {
    switch (type) {
      case 'BUY':  return 'primary';
      case 'SELL': return 'warn';
      default:     return '';
    }
  }

  signalIcon(type: string): string {
    switch (type) {
      case 'BUY':  return 'arrow_upward';
      case 'SELL': return 'arrow_downward';
      default:     return 'pause';
    }
  }

  ratingColor(rating: string): string {
    switch (rating) {
      case 'GOOD':    return 'good';
      case 'POOR':    return 'poor';
      default:        return 'average';
    }
  }

  confidencePct(v: number): string {
    return (v * 100).toFixed(0) + '%';
  }

  trackById(_: number, item: { id: string }): string { return item.id; }
}
