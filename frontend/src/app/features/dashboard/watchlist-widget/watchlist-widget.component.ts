import {
  Component,
  OnInit,
  OnDestroy,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatListModule } from '@angular/material/list';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDividerModule } from '@angular/material/divider';
import { Subscription } from 'rxjs';

import { WatchlistApiService, WatchlistItem } from './watchlist-api.service';
import { MarketDataSocketService } from '../../../core/api/market-data-socket.service';
import { SymbolSearchComponent, SymbolSearchResult } from '../../../shared/symbol-search';

/**
 * WatchlistWidgetComponent
 *
 * Shows the current user's watchlist with live prices (via MarketDataSocketService —
 * the same STOMP connection used by live-trading-page, not a second WebSocket).
 *
 * Each row: symbol  |  live price  |  asset class badge  |  remove button
 * Clicking a row navigates to /charting?symbol=X.
 *
 * The top of the card contains the SymbolSearchComponent so users can add symbols
 * directly from the search box — connecting GAP 1 and GAP 2 naturally.
 */
@Component({
  selector: 'app-watchlist-widget',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatListModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    MatSnackBarModule,
    MatDividerModule,
    SymbolSearchComponent,
  ],
  templateUrl: './watchlist-widget.component.html',
  styleUrls: ['./watchlist-widget.component.scss'],
})
export class WatchlistWidgetComponent implements OnInit, OnDestroy {
  private readonly api          = inject(WatchlistApiService);
  private readonly marketSocket = inject(MarketDataSocketService);
  private readonly router       = inject(Router);
  private readonly snack        = inject(MatSnackBar);

  // ── State ─────────────────────────────────────────────────────────────────

  readonly items   = signal<WatchlistItem[]>([]);
  readonly loading = signal(false);
  readonly adding  = signal(false);

  /** symbol → live price map */
  readonly livePrices = signal<Record<string, number>>({});

  private tickSubs: Subscription[] = [];

  // ── Lifecycle ─────────────────────────────────────────────────────────────

  ngOnInit(): void {
    this.loadWatchlist();
  }

  ngOnDestroy(): void {
    this.cleanupSubscriptions();
    this.marketSocket.disconnect();
  }

  // ── Data loading ──────────────────────────────────────────────────────────

  loadWatchlist(): void {
    this.loading.set(true);
    this.cleanupSubscriptions();

    this.api.getWatchlist().subscribe({
      next: (data) => {
        this.items.set(data);
        this.loading.set(false);
        this.subscribeToLivePrices(data.map((i) => i.symbol));
      },
      error: () => {
        this.snack.open('Failed to load watchlist.', 'Close', { duration: 3000 });
        this.loading.set(false);
      },
    });
  }

  // ── Add from search ───────────────────────────────────────────────────────

  /** Called by (symbolSelected) from SymbolSearchComponent. */
  onSymbolSelected(result: SymbolSearchResult): void {
    this.adding.set(true);
    this.api.addSymbol({ symbol: result.ticker, assetClass: result.assetClass || undefined }).subscribe({
      next: (added) => {
        const current = this.items();
        if (!current.some((i) => i.symbol === added.symbol)) {
          this.items.set([added, ...current]);
          this.subscribeToLivePrices([added.symbol]);
        }
        this.snack.open(`${added.symbol} added to watchlist.`, 'OK', { duration: 2000 });
        this.adding.set(false);
      },
      error: () => {
        this.snack.open('Could not add symbol.', 'Close', { duration: 3000 });
        this.adding.set(false);
      },
    });
  }

  // ── Remove ────────────────────────────────────────────────────────────────

  removeSymbol(event: Event, symbol: string): void {
    event.stopPropagation(); // don't navigate when remove is clicked
    this.api.removeSymbol(symbol).subscribe({
      next: () => {
        this.items.update((list) => list.filter((i) => i.symbol !== symbol));
        this.marketSocket.unsubscribeSymbol(symbol);
        this.livePrices.update((prices) => {
          const copy = { ...prices };
          delete copy[symbol];
          return copy;
        });
        this.snack.open(`${symbol} removed from watchlist.`, 'OK', { duration: 2000 });
      },
      error: () => {
        this.snack.open('Could not remove symbol.', 'Close', { duration: 3000 });
      },
    });
  }

  // ── Navigation ────────────────────────────────────────────────────────────

  navigateToChart(symbol: string): void {
    this.router.navigate(['/charting'], { queryParams: { symbol } });
  }

  // ── Live price helpers ────────────────────────────────────────────────────

  priceFor(symbol: string): number | null {
    const p = this.livePrices()[symbol];
    return p !== undefined ? p : null;
  }

  // ── Private ───────────────────────────────────────────────────────────────

  private subscribeToLivePrices(symbols: string[]): void {
    for (const sym of symbols) {
      this.marketSocket.subscribeSymbol(sym);
      const sub = this.marketSocket.ticks$(sym).subscribe((tick) => {
        this.livePrices.update((prices) => ({ ...prices, [tick.symbol]: tick.price }));
      });
      this.tickSubs.push(sub);
    }
  }

  private cleanupSubscriptions(): void {
    for (const sub of this.tickSubs) {
      sub.unsubscribe();
    }
    this.tickSubs = [];
    // Unsubscribe all current symbols from STOMP
    for (const item of this.items()) {
      this.marketSocket.unsubscribeSymbol(item.symbol);
    }
  }
}
