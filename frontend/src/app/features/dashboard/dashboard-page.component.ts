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
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';

import { DashboardApiService } from './dashboard-api.service';
import { PortfolioStats, TradeSummary } from './dashboard.models';
import { CandlestickChartComponent } from '../../shared/chart-library/candlestick-chart/candlestick-chart.component';
import { EconomicCalendarComponent } from './economic-calendar/economic-calendar.component';
import { OhlcvBar } from '../../shared/chart-library/models/ohlcv.model';
import { IndicatorSeries } from '../../shared/chart-library/models/indicator-series.model';
import { EmptyStateComponent } from '../../shared/empty-state/empty-state.component';
import { LoadingStateComponent } from '../../shared/loading-state/loading-state.component';
import { PriceFlashDirective } from '../../shared/directives/price-flash.directive';

type RangeFilter = '1W' | '1M' | '3M' | 'ALL';

/**
 * Landing screen after login — KPIs, equity curve, recent trades, economic calendar.
 * Watchlist lives in the global app-shell tools panel.
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
    MatIconModule,
    MatButtonModule,
    MatChipsModule,
    MatTooltipModule,
    CandlestickChartComponent,
    EconomicCalendarComponent,
    EmptyStateComponent,
    LoadingStateComponent,
    PriceFlashDirective,
  ],
  templateUrl: './dashboard-page.component.html',
  styleUrls: ['./dashboard-page.component.scss'],
})
export class DashboardPageComponent implements OnInit {
  private readonly api = inject(DashboardApiService);

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  readonly stats = signal<PortfolioStats | null>(null);
  readonly recentTrades = signal<TradeSummary[]>([]);

  /** Active range filter for the equity curve */
  readonly activeRange = signal<RangeFilter>('ALL');
  readonly rangeOptions: RangeFilter[] = ['1W', '1M', '3M', 'ALL'];

  /** Filtered equity bars based on selected range */
  readonly filteredEquityBars = computed<OhlcvBar[]>(() => {
    const s = this.stats();
    if (!s?.equityCurve?.length) return [];
    const all = s.equityCurve;
    const now = Date.now();
    const cutoffMs: Record<RangeFilter, number> = {
      '1W':  7 * 24 * 3600 * 1000,
      '1M':  30 * 24 * 3600 * 1000,
      '3M':  90 * 24 * 3600 * 1000,
      'ALL': Infinity,
    };
    const cutoff = now - cutoffMs[this.activeRange()];
    const filtered = all.filter(([ts]) => new Date(ts).getTime() >= cutoff);
    const points = (filtered.length > 0 ? filtered : all);
    return points.map(([ts, value]) => ({
      timestamp: ts,
      open: value,
      high: value,
      low: value,
      close: value,
      volume: 0,
    }));
  });

  readonly equityIndicator = computed<IndicatorSeries[]>(() => {
    const bars = this.filteredEquityBars();
    if (!bars.length) return [];
    // Determine net direction for gradient color
    const first = bars[0].close;
    const last = bars[bars.length - 1].close;
    const isPositive = last >= first;
    return [
      {
        name: 'Equity',
        type: 'SMA',
        data: bars.map(b => b.close),
        color: isPositive ? '#2FBE7A' : '#F0555A',
        subPane: false,
      },
    ];
  });

  readonly displayedColumns = [
    'symbol', 'side', 'entryPrice', 'exitPrice', 'quantity', 'pnl', 'status', 'openedAt',
  ];

  ngOnInit(): void {
    this.loadDashboard();
  }

  refresh(): void {
    this.loadDashboard();
  }

  setRange(range: RangeFilter): void {
    this.activeRange.set(range);
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

  pnlClass(pnl: number | null): string {
    if (pnl === null) return 'tp-mono';
    return pnl >= 0 ? 'tp-mono tp-bull' : 'tp-mono tp-bear';
  }

  statusClass(status: string): string {
    return status === 'OPEN' ? 'chip-open' : 'chip-closed';
  }

  formatDate(iso: string | null): string {
    if (!iso) return '—';
    return new Date(iso).toLocaleDateString('en-US', {
      month: 'short', day: 'numeric', year: 'numeric',
    });
  }

  formatPnl(pnl: number | null): string {
    if (pnl === null) return '—';
    const sign = pnl >= 0 ? '+' : '';
    return `${sign}${pnl.toFixed(2)}`;
  }

  get totalPnl(): number { return this.stats()?.totalPnl ?? 0; }
  get totalPnlClass(): string { return this.totalPnl >= 0 ? 'tp-bull' : 'tp-bear'; }
}
