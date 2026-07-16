import {
  Component,
  OnInit,
  OnDestroy,
  inject,
  signal,
  computed,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDividerModule } from '@angular/material/divider';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Subscription } from 'rxjs';

import { TradeApiService, Trade, CreateTradeRequest } from '../../core/api/trade-api.service';
import { MarketDataSocketService } from '../../core/api/market-data-socket.service';
import { CandlestickChartComponent } from '../../shared/chart-library/candlestick-chart/candlestick-chart.component';
import { OhlcvBar } from '../../shared/chart-library/models/ohlcv.model';
import { AVAILABLE_BROKERS } from './live-trading.models';

/**
 * Live Trading screen.
 *
 * Features:
 *  - Broker picker (BitUnix hardcoded; TODO: load from trading-service)
 *  - Symbol selector with live OHLCV chart (CandlestickChartComponent)
 *    fed by MarketDataSocketService (STOMP /topic/market/{symbol})
 *  - Order-entry form: side (BUY/SELL), type (SPOT/FUTURES), price, qty
 *  - Open positions list with close-trade action
 *
 * Route: /live-trading  (behind authGuard)
 */
@Component({
  selector: 'app-live-trading-page',
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
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatDividerModule,
    MatChipsModule,
    MatTooltipModule,
    CandlestickChartComponent,
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
  readonly chartBars = signal<OhlcvBar[]>([]);

  readonly openPositions = signal<Trade[]>([]);
  readonly loadingPositions = signal(false);
  readonly submitting = signal(false);

  readonly popularSymbols = [
    'BTCUSDT', 'ETHUSDT', 'BNBUSDT', 'SOLUSDT', 'XRPUSDT',
    'ADAUSDT', 'DOGEUSDT', 'AVAXUSDT', 'DOTUSDT', 'MATICUSDT',
  ];

  // ── Order form ────────────────────────────────────────────────────────────

  readonly orderForm: FormGroup = this.fb.group({
    side:       ['BUY',    Validators.required],
    type:       ['SPOT',   Validators.required],
    price:      [null,     [Validators.required, Validators.min(0.000001)]],
    quantity:   [null,     [Validators.required, Validators.min(0.000001)]],
    notes:      [''],
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

  onSymbolChange(symbol: string): void {
    symbol = symbol.toUpperCase().trim();
    if (!symbol || symbol === this.currentSymbol) return;

    if (this.currentSymbol) {
      this.marketSocket.unsubscribeSymbol(this.currentSymbol);
    }
    this.tickSub?.unsubscribe();

    this.selectedSymbol.set(symbol);
    this.livePrice.set(null);
    this.chartBars.set([]);
    this.subscribeToSymbol(symbol);
  }

  private subscribeToSymbol(symbol: string): void {
    this.currentSymbol = symbol;
    this.marketSocket.subscribeSymbol(symbol);
    this.tickSub = this.marketSocket.ticks$(symbol).subscribe((tick) => {
      this.livePrice.set(tick.price);
      // Append tick as a minimal bar so the chart has a category axis entry.
      this.chartBars.update((bars) => {
        const last = bars[bars.length - 1];
        if (last && last.timestamp === tick.timestamp) {
          // Update last bar in place.
          const updated = bars.slice();
          updated[updated.length - 1] = {
            ...last,
            close: tick.price,
            high: Math.max(last.high, tick.price),
            low:  Math.min(last.low,  tick.price),
            volume: last.volume + tick.volume,
          };
          return updated;
        }
        // New candle period.
        const bar: OhlcvBar = {
          timestamp: tick.timestamp,
          open:  tick.price,
          high:  tick.price,
          low:   tick.price,
          close: tick.price,
          volume: tick.volume,
        };
        return [...bars.slice(-499), bar]; // keep last 500 bars
      });
    });
  }

  // ── Order submission ──────────────────────────────────────────────────────

  submitOrder(): void {
    if (this.orderForm.invalid || this.submitting()) return;

    this.submitting.set(true);
    const { side, type, price, quantity, notes } = this.orderForm.value;

    const req: CreateTradeRequest = {
      symbol:     this.selectedSymbol(),
      side,
      type,
      entryPrice: +price,
      quantity:   +quantity,
      broker:     this.selectedBroker(),
      notes:      notes || undefined,
    };

    this.tradeApi.openTrade(req).subscribe({
      next: (trade) => {
        this.snack.open(`Trade #${trade.id} opened: ${trade.side} ${trade.quantity} ${trade.symbol}`, 'OK', { duration: 4000 });
        this.orderForm.reset({ side: 'BUY', type: 'SPOT', price: null, quantity: null, notes: '' });
        this.loadOpenPositions();
        this.submitting.set(false);
      },
      error: (err) => {
        const msg = err?.error?.message ?? 'Failed to open trade.';
        this.snack.open(msg, 'Close', { duration: 5000, panelClass: 'snack-error' });
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
        const pnlStr = closed.pnl !== null ? ` P&L: ${closed.pnl.toFixed(2)}` : '';
        this.snack.open(`Trade #${closed.id} closed.${pnlStr}`, 'OK', { duration: 4000 });
        this.loadOpenPositions();
      },
      error: (err) => {
        this.snack.open(err?.error?.message ?? 'Failed to close trade.', 'Close', { duration: 5000 });
      },
    });
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  pnlClass(pnl: number | null): string {
    if (pnl === null) return '';
    return pnl >= 0 ? 'positive' : 'negative';
  }

  trackById(_: number, t: Trade): number { return t.id; }
}
