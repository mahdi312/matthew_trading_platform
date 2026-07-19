import {
  Component,
  OnInit,
  OnDestroy,
  inject,
  signal,
  input,
  output,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { forkJoin, of, Subscription } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { WatchlistApiService, WatchlistItem } from './watchlist-api.service';
import { MarketDataSocketService } from '../../../core/api/market-data-socket.service';
import { SymbolSearchComponent, SymbolSearchResult } from '../../../shared/symbol-search';

interface QuoteState {
  price: number | null;
  prevClose: number | null;
}

/**
 * Right-drawer watchlist: symbol name, live price, and day change vs previous close.
 */
@Component({
  selector: 'app-watchlist-widget',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    MatSnackBarModule,
    SymbolSearchComponent,
  ],
  templateUrl: './watchlist-widget.component.html',
  styleUrls: ['./watchlist-widget.component.scss'],
})
export class WatchlistWidgetComponent implements OnInit, OnDestroy {
  /** When true, show a close control (drawer mode). */
  readonly drawerMode = input(false);
  readonly closed = output<void>();

  private readonly api = inject(WatchlistApiService);
  private readonly marketSocket = inject(MarketDataSocketService);
  private readonly router = inject(Router);
  private readonly snack = inject(MatSnackBar);

  readonly items = signal<WatchlistItem[]>([]);
  readonly loading = signal(false);
  readonly adding = signal(false);

  /** symbol → live / seeded price + previous-day close */
  readonly quotes = signal<Record<string, QuoteState>>({});

  private tickSubs: Subscription[] = [];

  ngOnInit(): void {
    this.loadWatchlist();
  }

  ngOnDestroy(): void {
    this.cleanupSubscriptions();
  }

  loadWatchlist(): void {
    this.loading.set(true);
    this.cleanupSubscriptions();

    this.api.getWatchlist().subscribe({
      next: (data) => {
        this.items.set(data);
        this.loading.set(false);
        this.bootstrapQuotes(data.map((i) => i.symbol));
      },
      error: () => {
        this.snack.open('Failed to load watchlist.', 'Close', { duration: 3000 });
        this.loading.set(false);
      },
    });
  }

  onSymbolSelected(result: SymbolSearchResult): void {
    this.adding.set(true);
    this.api.addSymbol({ symbol: result.ticker, assetClass: result.assetClass || undefined }).subscribe({
      next: (added) => {
        const current = this.items();
        if (!current.some((i) => i.symbol === added.symbol)) {
          this.items.set([added, ...current]);
          this.bootstrapQuotes([added.symbol]);
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

  removeSymbol(event: Event, symbol: string): void {
    event.stopPropagation();
    this.api.removeSymbol(symbol).subscribe({
      next: () => {
        this.items.update((list) => list.filter((i) => i.symbol !== symbol));
        this.marketSocket.unsubscribeSymbol(symbol);
        this.quotes.update((q) => {
          const copy = { ...q };
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

  navigateToChart(symbol: string): void {
    this.router.navigate(['/charting'], { queryParams: { symbol } });
  }

  requestClose(): void {
    this.closed.emit();
  }

  priceFor(symbol: string): number | null {
    return this.quotes()[symbol]?.price ?? null;
  }

  /** Signed % change vs previous daily close; null if unknown. */
  changePctFor(symbol: string): number | null {
    const q = this.quotes()[symbol];
    if (!q?.price || q.prevClose == null || q.prevClose === 0) return null;
    return ((q.price - q.prevClose) / q.prevClose) * 100;
  }

  changeClass(symbol: string): string {
    const pct = this.changePctFor(symbol);
    if (pct === null) return '';
    if (pct > 0) return 'up';
    if (pct < 0) return 'down';
    return 'flat';
  }

  private bootstrapQuotes(symbols: string[]): void {
    if (!symbols.length) return;

    const requests = symbols.map((sym) =>
      forkJoin({
        symbol: of(sym),
        prevClose: this.api.getPreviousClose(sym),
        latest: this.api.getLatestClose(sym),
      }).pipe(catchError(() => of({ symbol: sym, prevClose: null as number | null, latest: null as number | null }))),
    );

    forkJoin(requests).subscribe((results) => {
      this.quotes.update((current) => {
        const next = { ...current };
        for (const r of results) {
          next[r.symbol] = {
            price: r.latest ?? current[r.symbol]?.price ?? null,
            prevClose: r.prevClose,
          };
        }
        return next;
      });
      this.subscribeToLivePrices(symbols);
    });
  }

  private subscribeToLivePrices(symbols: string[]): void {
    for (const sym of symbols) {
      this.marketSocket.subscribeSymbol(sym);
      const sub = this.marketSocket.ticks$(sym).subscribe((tick) => {
        this.quotes.update((q) => ({
          ...q,
          [tick.symbol]: {
            price: tick.price,
            prevClose: q[tick.symbol]?.prevClose ?? null,
          },
        }));
      });
      this.tickSubs.push(sub);
    }
  }

  private cleanupSubscriptions(): void {
    for (const sub of this.tickSubs) {
      sub.unsubscribe();
    }
    this.tickSubs = [];
    for (const item of this.items()) {
      this.marketSocket.unsubscribeSymbol(item.symbol);
    }
  }
}
