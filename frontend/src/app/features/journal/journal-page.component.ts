import {
  Component,
  OnInit,
  inject,
  signal,
  computed,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';

import { TradeApiService, Trade, TradeFilter } from '../../core/api/trade-api.service';

/**
 * Trade Journal screen.
 *
 * Features:
 *  - Paginated, filterable trade history table (status, symbol, date range)
 *  - Per-row close-trade action (OPEN trades only)
 *  - Per-row delete action
 *
 * Shares TradeApiService with LiveTradingModule (no duplicate HTTP code).
 * Route: /journal  (behind authGuard)
 */
@Component({
  selector: 'app-journal-page',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    RouterModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatDialogModule,
    MatChipsModule,
    MatTooltipModule,
    MatDatepickerModule,
    MatNativeDateModule,
  ],
  templateUrl: './journal-page.component.html',
  styleUrls: ['./journal-page.component.scss'],
})
export class JournalPageComponent implements OnInit {
  private readonly tradeApi = inject(TradeApiService);
  private readonly snack    = inject(MatSnackBar);
  private readonly fb       = inject(FormBuilder);

  // ── State ─────────────────────────────────────────────────────────────────

  readonly trades      = signal<Trade[]>([]);
  readonly totalCount  = signal(0);
  readonly loading     = signal(false);
  readonly error       = signal<string | null>(null);
  readonly closingId   = signal<number | null>(null);
  readonly deletingId  = signal<number | null>(null);

  // ── Pagination ────────────────────────────────────────────────────────────

  readonly pageSize  = signal(20);
  readonly pageIndex = signal(0);

  // ── Filter form ───────────────────────────────────────────────────────────

  readonly filterForm: FormGroup = this.fb.group({
    status:  ['ALL'],
    symbol:  [''],
    from:    [null],
    to:      [null],
  });

  // ── Table columns ──────────────────────────────────────────────────────────

  readonly displayedColumns = [
    'id', 'symbol', 'side', 'type',
    'entryPrice', 'exitPrice', 'quantity', 'pnl',
    'status', 'openedAt', 'closedAt', 'actions',
  ];

  // ── Lifecycle ─────────────────────────────────────────────────────────────

  ngOnInit(): void {
    this.loadTrades();
  }

  // ── Filter / pagination ────────────────────────────────────────────────────

  applyFilter(): void {
    this.pageIndex.set(0);
    this.loadTrades();
  }

  clearFilter(): void {
    this.filterForm.reset({ status: 'ALL', symbol: '', from: null, to: null });
    this.pageIndex.set(0);
    this.loadTrades();
  }

  onPage(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.loadTrades();
  }

  private loadTrades(): void {
    this.loading.set(true);
    this.error.set(null);

    const { status, symbol, from, to } = this.filterForm.value;
    const filter: TradeFilter = {
      status: status === 'ALL' ? undefined : status,
      symbol: symbol?.trim() || undefined,
      from:   from ? (from as Date).toISOString().slice(0, 10) : undefined,
      to:     to   ? (to   as Date).toISOString().slice(0, 10) : undefined,
      page:   this.pageIndex(),
      size:   this.pageSize(),
    };

    this.tradeApi.getTrades(filter).subscribe({
      next: (resp) => {
        this.trades.set(resp.content);
        this.totalCount.set(resp.totalElements);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set('Failed to load trades.');
        this.loading.set(false);
        console.error('[Journal]', err);
      },
    });
  }

  // ── Actions ───────────────────────────────────────────────────────────────

  closeTrade(trade: Trade): void {
    const exitPrice = Number(prompt(`Close trade #${trade.id}. Enter exit price:`, String(trade.entryPrice)));
    if (!exitPrice || isNaN(exitPrice)) return;

    this.closingId.set(trade.id);
    this.tradeApi.closeTrade(trade.id, { exitPrice }).subscribe({
      next: (closed) => {
        this.snack.open(`Trade #${closed.id} closed. P&L: ${closed.pnl?.toFixed(2) ?? '—'}`, 'OK', { duration: 4000 });
        this.closingId.set(null);
        this.loadTrades();
      },
      error: (err) => {
        this.snack.open(err?.error?.message ?? 'Failed to close trade.', 'Close', { duration: 5000 });
        this.closingId.set(null);
      },
    });
  }

  deleteTrade(trade: Trade): void {
    if (!confirm(`Delete trade #${trade.id} (${trade.symbol} ${trade.side})? This cannot be undone.`)) return;

    this.deletingId.set(trade.id);
    this.tradeApi.deleteTrade(trade.id).subscribe({
      next: () => {
        this.snack.open(`Trade #${trade.id} deleted.`, 'OK', { duration: 3000 });
        this.deletingId.set(null);
        this.loadTrades();
      },
      error: (err) => {
        this.snack.open(err?.error?.message ?? 'Failed to delete trade.', 'Close', { duration: 5000 });
        this.deletingId.set(null);
      },
    });
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  pnlClass(pnl: number | null): string {
    if (pnl === null) return '';
    return pnl >= 0 ? 'positive' : 'negative';
  }

  formatDate(iso: string | null): string {
    if (!iso) return '—';
    return new Date(iso).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
  }

  trackById(_: number, t: Trade): number { return t.id; }
}
