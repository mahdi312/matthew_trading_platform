import {
  Component,
  OnInit,
  inject,
  signal,
  computed,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { RouterModule } from '@angular/router';
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
import { MatListModule } from '@angular/material/list';
import { MatBadgeModule } from '@angular/material/badge';

import { AiInsightsApiService } from './ai-insights-api.service';
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
    MatListModule,
    MatBadgeModule,
  ],
  templateUrl: './ai-insights-page.component.html',
  styleUrls: ['./ai-insights-page.component.scss'],
})
export class AiInsightsPageComponent implements OnInit {
  private readonly api   = inject(AiInsightsApiService);
  private readonly snack = inject(MatSnackBar);
  private readonly fb    = inject(FormBuilder);

  // ── Symbol selector ───────────────────────────────────────────────────────

  readonly symbol       = signal<string>('BTCUSDT');
  readonly symbolInput  = signal<string>('BTCUSDT');

  readonly popularSymbols = ['BTCUSDT', 'ETHUSDT', 'BNBUSDT', 'SOLUSDT', 'XRPUSDT'];

  // ── Summary tab ───────────────────────────────────────────────────────────

  readonly loadingSummary = signal(false);
  readonly summary        = signal<AiSummary | null>(null);
  readonly summaryError   = signal<string | null>(null);

  // ── Signals tab ───────────────────────────────────────────────────────────

  readonly loadingSignals = signal(false);
  readonly signals        = signal<AiSignal[]>([]);
  readonly signalsError   = signal<string | null>(null);

  // ── Journal critique tab ──────────────────────────────────────────────────

  readonly loadingCritique  = signal(false);
  readonly critique         = signal<JournalCritique | null>(null);
  readonly critiqueError    = signal<string | null>(null);

  readonly critiqueForm: FormGroup = this.fb.group({
    tradeId:    ['', Validators.required],
    symbol:     ['', Validators.required],
    side:       ['BUY', Validators.required],
    entryPrice: [null, [Validators.required, Validators.min(0)]],
    exitPrice:  [null],
    notes:      [''],
  });

  // ── Lifecycle ─────────────────────────────────────────────────────────────

  ngOnInit(): void {
    this.fetchSummary();
    this.fetchSignals();
  }

  // ── Symbol change ─────────────────────────────────────────────────────────

  applySymbol(): void {
    const s = this.symbolInput().toUpperCase().trim();
    if (!s) return;
    this.symbol.set(s);
    // Refresh summary & signals for new symbol
    this.fetchSummary();
    this.fetchSignals();
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

    this.api.getSummary(this.symbol()).subscribe({
      next:  (data) => { this.summary.set(data);  this.loadingSummary.set(false); },
      error: (err)  => {
        this.summaryError.set('Failed to load AI summary. The service may be unavailable.');
        this.loadingSummary.set(false);
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
        this.signalsError.set('Failed to load AI signals.');
        this.loadingSignals.set(false);
        console.error('[AiInsights] signals error', err);
      },
    });
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
        this.critiqueError.set('Failed to get AI critique. Please try again.');
        this.loadingCritique.set(false);
        console.error('[AiInsights] critique error', err);
      },
    });
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
