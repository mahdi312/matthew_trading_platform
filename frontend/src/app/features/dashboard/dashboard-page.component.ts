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
import { EconomicCalendarComponent } from './economic-calendar/economic-calendar.component';
import { OhlcvBar } from '../../shared/chart-library/models/ohlcv.model';
import { IndicatorSeries } from '../../shared/chart-library/models/indicator-series.model';

/**
 * Landing screen after login — KPIs, equity, calendar, recent trades.
 * Watchlist lives in the global app-shell tools panel (all routes).
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
    EconomicCalendarComponent,
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

  readonly equityIndicator = computed<IndicatorSeries[]>(() => {
    const s = this.stats();
    if (!s?.equityCurve?.length) return [];
    return [
      {
        name: 'Equity',
        type: 'SMA',
        data: s.equityCurve.map(([, value]) => value),
        color: '#388bfd',
        subPane: false,
      },
    ];
  });

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
