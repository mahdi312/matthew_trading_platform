import {
  Component,
  OnInit,
  inject,
  signal,
  computed,
} from '@angular/core';
import { CommonModule, DecimalPipe, PercentPipe } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';

import { DashboardApiService } from './dashboard-api.service';
import { PortfolioStats, TradeSummary } from './dashboard.models';
import { CandlestickChartComponent } from '../../shared/chart-library/candlestick-chart/candlestick-chart.component';
import { WatchlistWidgetComponent } from './watchlist-widget/watchlist-widget.component';
import { OhlcvBar } from '../../shared/chart-library/models/ohlcv.model';
import { IndicatorSeries } from '../../shared/chart-library/models/indicator-series.model';

/**
 * Landing screen after login.
 *
 * Shows:
 *  1. KPI cards — Total P&L, Win Rate, Trade Count, Open Positions.
 *  2. Equity curve — rendered via CandlestickChartComponent in line-chart
 *     mode (ohlcvData left empty; indicators carry the equity line).
 *  3. Recent trades table — last 20 trades with symbol, side, P&L, status.
 *
 * Route: /dashboard  (protected by authGuard, default redirect after login)
 */
@Component({
  selector: 'app-dashboard-page',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    DecimalPipe,
    PercentPipe,
    MatCardModule,
    MatTableModule,
    MatProgressSpinnerModule,
    MatIconModule,
    MatButtonModule,
    MatChipsModule,
    MatTooltipModule,
    CandlestickChartComponent,
    WatchlistWidgetComponent,
  ],
  templateUrl: './dashboard-page.component.html',
  styleUrls: ['./dashboard-page.component.scss'],
})
export class DashboardPageComponent implements OnInit {
  private readonly api = inject(DashboardApiService);

  // ── Loading / error state ─────────────────────────────────────────────────

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  // ── Data signals ─────────────────────────────────────────────────────────

  readonly stats = signal<PortfolioStats | null>(null);
  readonly recentTrades = signal<TradeSummary[]>([]);

  /**
   * Equity curve converted to an IndicatorSeries so CandlestickChartComponent
   * can render it as a line overlay on an empty OHLCV canvas.
   *
   * We use the equity curve timestamps as the category axis by constructing
   * a matching fake OhlcvBar array (all prices set to the equity value so
   * the "candle" degenerates into a point — the line series is what we see).
   */
  readonly equityIndicator = computed<IndicatorSeries[]>(() => {
    const s = this.stats();
    if (!s?.equityCurve?.length) return [];
    return [
      {
        name: 'Equity',
        data: s.equityCurve.map(([, value]) => value),
        color: '#42a5f5',
        subPane: false,
      },
    ];
  });

  /** Fake OHLCV bars to serve as the category axis for the equity line chart. */
  readonly equityBars = computed<OhlcvBar[]>(() => {
    const s = this.stats();
    if (!s?.equityCurve?.length) return [];
    return s.equityCurve.map(([ts, value]) => ({
      timestamp: ts,
      open: value,
      high: value,
      low: value,
      close: value,
      volume: 0,
    }));
  });

  // ── Table column definitions ─────────────────────────────────────────────

  readonly displayedColumns = [
    'symbol',
    'side',
    'entryPrice',
    'exitPrice',
    'quantity',
    'pnl',
    'status',
    'openedAt',
  ];

  // ── Lifecycle ─────────────────────────────────────────────────────────────

  ngOnInit(): void {
    this.loadDashboard();
  }

  refresh(): void {
    this.loadDashboard();
  }

  private loadDashboard(): void {
    this.loading.set(true);
    this.error.set(null);

    let statsLoaded = false;
    let tradesLoaded = false;

    const checkDone = () => {
      if (statsLoaded && tradesLoaded) {
        this.loading.set(false);
      }
    };

    this.api.getPortfolioStats().subscribe({
      next: (data) => {
        this.stats.set(data);
        statsLoaded = true;
        checkDone();
      },
      error: (err) => {
        this.error.set('Failed to load portfolio stats. Please try again.');
        statsLoaded = true;
        checkDone();
        console.error('[Dashboard] portfolio stats error', err);
      },
    });

    this.api.getRecentTrades(20).subscribe({
      next: (data) => {
        this.recentTrades.set(data.content ?? []);
        tradesLoaded = true;
        checkDone();
      },
      error: (err) => {
        this.error.set('Failed to load recent trades. Please try again.');
        tradesLoaded = true;
        checkDone();
        console.error('[Dashboard] recent trades error', err);
      },
    });
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  pnlClass(pnl: number | null): string {
    if (pnl === null) return '';
    return pnl >= 0 ? 'positive' : 'negative';
  }

  statusClass(status: string): string {
    return status === 'OPEN' ? 'chip-open' : 'chip-closed';
  }

  formatDate(iso: string | null): string {
    if (!iso) return '—';
    return new Date(iso).toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
    });
  }
}
