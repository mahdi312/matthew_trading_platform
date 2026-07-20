import {
  Component,
  OnInit,
  OnDestroy,
  inject,
  signal,
  computed,
  ChangeDetectionStrategy,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subscription } from 'rxjs';
import { catchError, of } from 'rxjs';
import { WatchlistApiService, WatchlistItem } from '../../features/dashboard/watchlist-widget/watchlist-api.service';
import { MarketDataSocketService, MarketTick } from '../../core/api/market-data-socket.service';

interface TickerItem {
  symbol: string;
  price: number | null;
  prevClose: number | null;
}

@Component({
  selector: 'app-ticker-marquee',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './ticker-marquee.component.html',
  styleUrl: './ticker-marquee.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TickerMarqueeComponent implements OnInit, OnDestroy {
  private readonly watchlistApi = inject(WatchlistApiService);
  private readonly marketSocket = inject(MarketDataSocketService);

  readonly items = signal<TickerItem[]>([]);
  private readonly subs: Subscription[] = [];

  /** Duplicate items for seamless scroll loop */
  readonly displayItems = computed<TickerItem[]>(() => {
    const arr = this.items();
    if (!arr.length) return [];
    // Duplicate to fill at least 2× the viewport for seamless loop
    const doubled = [...arr, ...arr];
    return doubled;
  });

  ngOnInit(): void {
    this.watchlistApi.getWatchlist().pipe(
      catchError(() => of([] as WatchlistItem[])),
    ).subscribe((watchlist) => {
      const initialItems: TickerItem[] = watchlist.map(w => ({
        symbol: w.symbol,
        price: null,
        prevClose: null,
      }));
      this.items.set(initialItems);

      // Seed with last known price & subscribe for live ticks
      watchlist.forEach((w) => {
        // Seed latest close
        this.watchlistApi.getLatestClose(w.symbol).pipe(
          catchError(() => of(null)),
        ).subscribe((close) => {
          this.updatePrice(w.symbol, close);
        });

        // Seed previous close for change %
        this.watchlistApi.getPreviousClose(w.symbol).pipe(
          catchError(() => of(null)),
        ).subscribe((prev) => {
          this.updatePrevClose(w.symbol, prev);
        });

        // Live WebSocket subscription
        this.marketSocket.subscribeSymbol(w.symbol);
        const sub = this.marketSocket.ticks$(w.symbol).subscribe((tick: MarketTick) => {
          this.updatePrice(tick.symbol, tick.price);
        });
        this.subs.push(sub);
      });
    });
  }

  ngOnDestroy(): void {
    this.subs.forEach(s => s.unsubscribe());
    // We don't call disconnect() here — the socket is shared with other components
  }

  changePercent(item: TickerItem): number | null {
    if (item.price === null || item.prevClose === null || item.prevClose === 0) return null;
    return ((item.price - item.prevClose) / item.prevClose) * 100;
  }

  direction(item: TickerItem): 'bull' | 'bear' | 'neutral' {
    const pct = this.changePercent(item);
    if (pct === null) return 'neutral';
    return pct >= 0 ? 'bull' : 'bear';
  }

  formatPrice(price: number | null): string {
    if (price === null) return '—';
    if (price >= 1000) return price.toLocaleString('en-US', { maximumFractionDigits: 2 });
    if (price >= 1) return price.toFixed(4);
    return price.toFixed(6);
  }

  formatChange(pct: number | null): string {
    if (pct === null) return '';
    const sign = pct >= 0 ? '+' : '';
    return `${sign}${pct.toFixed(2)}%`;
  }

  private updatePrice(symbol: string, price: number | null): void {
    this.items.update(arr =>
      arr.map(item => item.symbol === symbol ? { ...item, price } : item),
    );
  }

  private updatePrevClose(symbol: string, prevClose: number | null): void {
    this.items.update(arr =>
      arr.map(item => item.symbol === symbol ? { ...item, prevClose } : item),
    );
  }
}
