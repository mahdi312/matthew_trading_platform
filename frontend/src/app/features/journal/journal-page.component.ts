import {
  Component,
  OnInit,
  inject,
  signal,
  computed,
} from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { debounceTime, distinctUntilChanged, Subject } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { TradeApiService, Trade, TradeFilter } from '../../core/api/trade-api.service';
import { SymbolSearchComponent, SymbolSearchResult } from '../../shared/symbol-search';
import { EmptyStateComponent } from '../../shared/empty-state/empty-state.component';
import { LoadingStateComponent } from '../../shared/loading-state/loading-state.component';
import { PriceFlashDirective } from '../../shared/directives/price-flash.directive';

type StatusFilter = 'ALL' | 'OPEN' | 'CLOSED';

/**
 * Trade Journal screen.
 * Persistent filter bar with immediate debounced filtering.
 * Shared TradeApiService (no duplicate HTTP code vs Live Trading).
 */
@Component({
  selector: 'app-journal-page',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    RouterModule,
    DecimalPipe,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    MatPaginatorModule,
    MatSnackBarModule,
    MatDialogModule,
    MatTooltipModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatFormFieldModule,
    MatInputModule,
    SymbolSearchComponent,
    EmptyStateComponent,
    LoadingStateComponent,
    PriceFlashDirective,
  ],
  templateUrl: './journal-page.component.html',
  styleUrls: ['./journal-page.component.scss'],
})
export class JournalPageComponent implements OnInit {
  private readonly tradeApi = inject(TradeApiService);
  private readonly snack    = inject(MatSnackBar);
  private readonly dialog   = inject(MatDialog);
  private readonly router   = inject(Router);
  private readonly fb       = inject(FormBuilder);

  // ── State ─────────────────────────────────────────────────────────────────

  readonly trades      = signal<Trade[]>([]);
  readonly totalCount  = signal(0);
  readonly loading     = signal(false);
  readonly error       = signal<string | null>(null);
  readonly closingId   = signal<number | null>(null);
  readonly deletingId  = signal<number | null>(null);

  // ── Whether any filter is active (for empty state copy) ───────────────────
  readonly hasActiveFilter = computed(() => {
    const { status, symbol, from, to } = this.filterForm.value as {
      status: StatusFilter; symbol: string; from: Date | null; to: Date | null;
    };
    return status !== 'ALL' || !!symbol?.trim() || !!from || !!to;
  });

  // ── Pagination ────────────────────────────────────────────────────────────

  readonly pageSize  = signal(20);
  readonly pageIndex = signal(0);

  // ── Filter form ───────────────────────────────────────────────────────────

  readonly statusFilter = signal<StatusFilter>('ALL');
  readonly symbolFilter = signal<string>('');

  readonly filterForm: FormGroup = this.fb.group({
    status: ['ALL'],
    symbol: [''],
    from:   [null],
    to:     [null],
  });

  // ── Table columns ──────────────────────────────────────────────────────────

  readonly displayedColumns = [
    'symbol', 'side', 'type', 'entryPrice', 'exitPrice',
    'quantity', 'pnl', 'status', 'openedAt', 'closedAt', 'broker', 'actions',
  ];

  private readonly filterChange$ = new Subject<void>();

  // ── Lifecycle ─────────────────────────────────────────────────────────────

  constructor() {
    // Debounce form changes — filters apply immediately without an Apply button
    this.filterForm.valueChanges.pipe(
      debounceTime(350),
      distinctUntilChanged(),
      takeUntilDestroyed(),
    ).subscribe(() => {
      this.pageIndex.set(0);
      this.loadTrades();
    });
  }

  ngOnInit(): void {
    this.loadTrades();
  }

  // ── Status chip filter ─────────────────────────────────────────────────────

  setStatus(status: StatusFilter): void {
    this.filterForm.patchValue({ status });
  }

  onSymbolSelected(result: SymbolSearchResult): void {
    this.filterForm.patchValue({ symbol: result.ticker });
  }

  clearSymbol(): void {
    this.filterForm.patchValue({ symbol: '' });
  }

  onPage(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.loadTrades();
  }

  loadTrades(): void {
    this.loading.set(true);
    this.error.set(null);

    const { status, symbol, from, to } = this.filterForm.value as {
      status: StatusFilter; symbol: string; from: Date | null; to: Date | null;
    };

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
        this.error.set('Failed to load trades. Please try again.');
        this.loading.set(false);
        console.error('[Journal]', err);
      },
    });
  }

  // ── Actions ───────────────────────────────────────────────────────────────

  closeTrade(trade: Trade): void {
    const exitPrice = Number(prompt(
      `Close trade #${trade.id} — ${trade.symbol} ${trade.side}\nEnter exit price:`,
      String(trade.entryPrice),
    ));
    if (!exitPrice || isNaN(exitPrice)) return;

    this.closingId.set(trade.id);
    this.tradeApi.closeTrade(trade.id, { exitPrice }).subscribe({
      next: (closed) => {
        const pnlStr = closed.pnl !== null ? ` | P&L: ${closed.pnl >= 0 ? '+' : ''}${closed.pnl.toFixed(2)}` : '';
        this.snack.open(`Closed ${closed.symbol}${pnlStr}`, 'OK', { duration: 4000 });
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
    if (!confirm(
      `Delete trade #${trade.id} (${trade.symbol} ${trade.side})?\n\nThis is permanent and cannot be undone.`
    )) return;

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

  askAI(trade: Trade): void {
    // Navigate to AI Insights with trade context passed as query params
    void this.router.navigate(['/ai-insights'], {
      queryParams: { tradeId: trade.id, symbol: trade.symbol },
    });
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  pnlClass(pnl: number | null): string {
    if (pnl === null) return 'tp-mono';
    return pnl >= 0 ? 'tp-mono tp-bull' : 'tp-mono tp-bear';
  }

  formatDate(iso: string | null): string {
    if (!iso) return '—';
    return new Date(iso).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
  }

  formatPnl(pnl: number | null): string {
    if (pnl === null) return '—';
    return (pnl >= 0 ? '+' : '') + pnl.toFixed(2);
  }

  trackById(_: number, t: Trade): number { return t.id; }

  currentStatus(): StatusFilter {
    return this.filterForm.get('status')?.value as StatusFilter ?? 'ALL';
  }
}
