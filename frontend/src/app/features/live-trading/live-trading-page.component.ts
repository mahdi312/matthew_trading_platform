import {
  Component,
  OnInit,
  OnDestroy,
  inject,
  signal,
  computed,
} from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { Subscription } from 'rxjs';

import { TradeApiService, Trade, CreateTradeRequest } from '../../core/api/trade-api.service';
import { MarketDataSocketService } from '../../core/api/market-data-socket.service';
import { CandlestickChartComponent } from '../../shared/chart-library/candlestick-chart/candlestick-chart.component';
import { SymbolSearchComponent, SymbolSearchResult } from '../../shared/symbol-search';
import { OhlcvBar } from '../../shared/chart-library/models/ohlcv.model';
import { AVAILABLE_BROKERS } from './live-trading.models';
import { EmptyStateComponent } from '../../shared/empty-state/empty-state.component';
import { LoadingStateComponent } from '../../shared/loading-state/loading-state.component';
import { PriceFlashDirective } from '../../shared/directives/price-flash.directive';

/**
 * Live Trading workspace.
 * Two-pane layout: dominant chart (left) + order ticket (right).
 * Open positions strip below both panes.
 */
@Component({
  selector: 'app-live-trading-page',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    RouterModule,
    DecimalPipe,
    MatIconModule,
    MatButtonModule,
    MatTooltipModule,
    MatSnackBarModule,
    CandlestickChartComponent,
    SymbolSearchComponent,
    EmptyStateComponent,
    LoadingStateComponent,
    PriceFlashDirective,
  ],
  templateUrl: './live-trading-page.component.html',
  styleUrls: ['./live-trading-page.component.scss'],
})
export class LiveTradingPageComponent implements OnInit, OnDestroy {
  private readonly tradeApi = inject(TradeApiService);
  private readonly marketSocket = inject(MarketDataSocketService);
  private readonly snack = inject(MatSnackBar);
  private readonly fb = inject(FormBuilder);

  // ── State ─────────────────────────────────────────────────────────────────

  readonly brokers = AVAILABLE_BROKERS;
  readonly selectedBroker = signal<string>('BITUNIX');
  readonly selectedSymbol = signal<string>('BTCUSDT');

  readonly livePrice = signal<number | null>(null);
  private prevPrice: number | null = null;
  readonly priceDirection = signal<'up' | 'down' | 'flat'>('flat');
  readonly chartBars = signal<OhlcvBar[]>([]);

  readonly openPositions = signal<Trade[]>([]);
  readonly loadingPositions = signal(false);
  readonly submitting = signal(false);

  // ── Order form ────────────────────────────────────────────────────────────

  readonly orderForm: FormGroup = this.fb.group({
    side:     ['BUY',  Validators.required],
    type:     ['SPOT', Validators.required],
    price:    [null,   [Validators.required, Validators.min(0.000001)]],
    quantity: [null,   [Validators.required, Validators.min(0.000001)]],
    notes:    [''],
  });

  /** Live preview: invested = price × quantity */
  readonly investedAmount = computed<number | null>(() => {
    const price = this.orderForm.get('price')?.value;
    const qty   = this.orderForm.get('quantity')?.value;
    if (!price || !qty) return null;
    return Number(price) * Number(qty);
  });

  private tickSub: Subscription | null = null;
  private currentSymbol = '';

  // ── Lifecycle ─────────────────────────────────────────────────────────────

  ngOnInit(): void {
    this.loadOpenPositions();
    this.subscribeToSymbol(this.selectedSymbol());
  }

  ngOnDestroy(): void {
    this.tickSub?.unsubscribe();
    this.marketSocket.unsubscribeSymbol(this.currentSymbol);
  }

  // ── Symbol change ─────────────────────────────────────────────────────────

  onSymbolSelected(result: SymbolSearchResult): void {
    const symbol = result.ticker.toUpperCase().trim();
    if (!symbol || symbol === this.currentSymbol) return;

    if (this.currentSymbol) {
      this.marketSocket.unsubscribeSymbol(this.currentSymbol);
    }
    this.tickSub?.unsubscribe();

    this.selectedSymbol.set(symbol);
    this.livePrice.set(null);
    this.prevPrice = null;
    this.priceDirection.set('flat');
    this.chartBars.set([]);
    this.subscribeToSymbol(symbol);
  }

  private subscribeToSymbol(symbol: string): void {
    this.currentSymbol = symbol;
    this.marketSocket.subscribeSymbol(symbol);
    this.tickSub = this.marketSocket.ticks$(symbol).subscribe((tick) => {
      const prev = this.livePrice();
      this.priceDirection.set(prev === null ? 'flat' : tick.price > prev ? 'up' : tick.price < prev ? 'down' : 'flat');
      this.prevPrice = prev;
      this.livePrice.set(tick.price);

      this.chartBars.update((bars) => {
        const last = bars[bars.length - 1];
        if (last && last.timestamp === tick.timestamp) {
          const updated = bars.slice();
          updated[updated.length - 1] = {
            ...last,
            close: tick.price,
            high: Math.max(last.high, tick.price),
            low: Math.min(last.low, tick.price),
            volume: last.volume + tick.volume,
          };
          return updated;
        }
        const bar: OhlcvBar = {
          timestamp: tick.timestamp,
          open: tick.price, high: tick.price, low: tick.price, close: tick.price,
          volume: tick.volume,
        };
        return [...bars.slice(-499), bar];
      });
    });
  }

  // ── Order form helpers ────────────────────────────────────────────────────

  selectBroker(id: string): void { this.selectedBroker.set(id); }
  setSide(side: string): void { this.orderForm.patchValue({ side }); }
  setType(type: string): void { this.orderForm.patchValue({ type }); }

  useMarketPrice(): void {
    if (this.livePrice() !== null) {
      this.orderForm.patchValue({ price: this.livePrice() });
    }
  }

  // ── Order submission ──────────────────────────────────────────────────────

  submitOrder(): void {
    if (this.orderForm.invalid || this.submitting()) return;

    this.submitting.set(true);
    const { side, type, price, quantity, notes } = this.orderForm.value as {
      side: 'BUY' | 'SELL'; type: 'SPOT' | 'FUTURES';
      price: number; quantity: number; notes: string;
    };

    const req: CreateTradeRequest = {
      symbol:     this.selectedSymbol(),
      side, type,
      entryPrice: +price,
      quantity:   +quantity,
      broker:     this.selectedBroker(),
      notes:      notes || undefined,
    };

    this.tradeApi.openTrade(req).subscribe({
      next: (trade) => {
        this.snack.open(`${trade.side} ${trade.quantity} ${trade.symbol} @ ${trade.entryPrice}`, 'OK', { duration: 4000 });
        this.orderForm.reset({ side: 'BUY', type: 'SPOT', price: null, quantity: null, notes: '' });
        this.openPositions.update(pos => [...pos, trade]);
        this.submitting.set(false);
      },
      error: (err) => {
        this.snack.open(err?.error?.message ?? 'Failed to open trade.', 'Close', { duration: 5000 });
        this.submitting.set(false);
      },
    });
  }

  // ── Position management ───────────────────────────────────────────────────

  loadOpenPositions(): void {
    this.loadingPositions.set(true);
    this.tradeApi.getTrades({ status: 'OPEN', size: 50 }).subscribe({
      next: (resp) => {
        this.openPositions.set(resp.content);
        this.loadingPositions.set(false);
      },
      error: () => this.loadingPositions.set(false),
    });
  }

  closePosition(trade: Trade): void {
    const exitPrice = this.livePrice() ?? trade.entryPrice;
    this.tradeApi.closeTrade(trade.id, { exitPrice }).subscribe({
      next: (closed) => {
        const pnlStr = closed.pnl !== null ? ` | P&L: ${closed.pnl >= 0 ? '+' : ''}${closed.pnl.toFixed(2)}` : '';
        this.snack.open(`Closed ${closed.symbol}${pnlStr}`, 'OK', { duration: 4000 });
        this.openPositions.update(pos => pos.filter(p => p.id !== closed.id));
      },
      error: (err) => {
        this.snack.open(err?.error?.message ?? 'Failed to close trade.', 'Close', { duration: 5000 });
      },
    });
  }

  unrealizedPnl(trade: Trade): number | null {
    if (this.livePrice() === null) return null;
    const diff = this.livePrice()! - trade.entryPrice;
    return trade.side === 'BUY' ? diff * trade.quantity : -diff * trade.quantity;
  }

  formatUnrealizedPnl(trade: Trade): string {
    const pnl = this.unrealizedPnl(trade);
    if (pnl === null) return '—';
    const sign = pnl >= 0 ? '+' : '';
    return `${sign}${pnl.toFixed(2)}`;
  }

  trackById(_: number, t: Trade): number { return t.id; }
}
