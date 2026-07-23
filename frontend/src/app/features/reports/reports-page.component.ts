import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule, DecimalPipe, PercentPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';

import { ReportsApiService } from './reports-api.service';
import { YearlyReport, MonthlyBreakdown } from './reports.models';
import { EmptyStateComponent } from '../../shared/empty-state/empty-state.component';
import { LoadingStateComponent } from '../../shared/loading-state/loading-state.component';
import { BarChartComponent, BarChartDatum } from '../../shared/chart-library';
import { ThemeService } from '../../core/theme/theme.service';

/**
 * ReportsPageComponent — yearly performance review + Excel export.
 *
 * Redesigned per Prompt 11 as a performance-review screen, not a data dump:
 *  1. Year selector (the endpoint is year-scoped; the selector assumes
 *     multiple browsable years even though only the current year likely has
 *     data today).
 *  2. Summary KPI strip — net P&L (bull/bear colored), win rate, avg R:R,
 *     max drawdown, Sharpe ratio (explicit "Not enough data" copy when the
 *     backend returns null rather than a blank/zero, since a null Sharpe
 *     ratio has a specific, explainable cause: insufficient trade history).
 *  3. Monthly breakdown — a bull/bear colored net-P&L bar chart via the
 *     shared Chart Library's `BarChartComponent`, plus a compact table below
 *     it with the exact per-month figures for anyone who wants them.
 *  4. Export — calls `GET /api/reports/export.xlsx` (`responseType: 'blob'`)
 *     and triggers a real browser file-save; the button itself shows a
 *     spinner-in-place-of-icon + disabled state while the file is generated.
 *
 * Route: /reports (behind authGuard)
 */
@Component({
  selector: 'app-reports-page',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    DecimalPipe,
    PercentPipe,
    MatIconModule,
    MatButtonModule,
    MatFormFieldModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    MatSnackBarModule,
    EmptyStateComponent,
    LoadingStateComponent,
    BarChartComponent,
  ],
  templateUrl: './reports-page.component.html',
  styleUrls: ['./reports-page.component.scss'],
})
export class ReportsPageComponent implements OnInit {
  private readonly api = inject(ReportsApiService);
  private readonly snack = inject(MatSnackBar);
  private readonly themeSvc = inject(ThemeService);

  // ── Year selection ────────────────────────────────────────────────────────

  readonly currentYear = new Date().getFullYear();
  /**
   * The endpoint is year-scoped — build the selector assuming multiple years
   * become browsable even though only the current year has real data today.
   */
  readonly availableYears: number[] = Array.from({ length: 6 }, (_, i) => this.currentYear - i);
  readonly selectedYear = signal<number>(this.currentYear);

  // ── Report state ──────────────────────────────────────────────────────────

  readonly loading = signal(true);
  readonly exporting = signal(false);
  readonly error = signal<string | null>(null);
  readonly report = signal<YearlyReport | null>(null);

  readonly chartTheme = computed(() => this.themeSvc.activeTheme());

  /** True once loaded successfully but the year has no trades at all. */
  readonly isEmptyYear = computed(() => {
    const r = this.report();
    return !!r && r.totalTrades === 0;
  });

  readonly monthNames = [
    'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
    'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec',
  ];

  /** Monthly net-P&L bars for the shared Chart Library's BarChartComponent. */
  readonly monthlyChartData = computed<BarChartDatum[]>(() => {
    const r = this.report();
    if (!r?.months?.length) return [];
    return r.months
      .slice()
      .sort((a, b) => a.month - b.month)
      .map((m) => ({ label: this.monthName(m), value: m.netPnl }));
  });

  readonly monthColumns = [
    'month', 'trades', 'wins', 'losses', 'winRate', 'grossPnl', 'netPnl', 'avgRrr',
  ];

  // ── Lifecycle ─────────────────────────────────────────────────────────────

  ngOnInit(): void {
    this.loadReport();
  }

  // ── Load report ───────────────────────────────────────────────────────────

  loadReport(): void {
    this.loading.set(true);
    this.error.set(null);

    this.api.getYearlyReport(this.selectedYear()).subscribe({
      next: (data) => {
        this.report.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set('Failed to load the yearly report. Please try again.');
        this.loading.set(false);
        console.error('[Reports] load error', err);
      },
    });
  }

  onYearChange(year: number): void {
    this.selectedYear.set(year);
    this.loadReport();
  }

  // ── Excel export ──────────────────────────────────────────────────────────

  /**
   * Calls the export endpoint and triggers a real browser file-save dialog
   * via a temporary anchor click — never opens the file in-app or navigates
   * away from the page.
   */
  exportExcel(): void {
    this.exporting.set(true);
    this.api.exportExcel(this.selectedYear()).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = `trading-report-${this.selectedYear()}.xlsx`;
        anchor.style.display = 'none';
        document.body.appendChild(anchor);
        anchor.click();
        document.body.removeChild(anchor);
        URL.revokeObjectURL(url);
        this.exporting.set(false);
        this.snack.open('Export downloaded.', 'OK', { duration: 3000 });
      },
      error: (err) => {
        this.exporting.set(false);
        this.snack.open('Export failed. Please try again.', 'Close', { duration: 4000 });
        console.error('[Reports] export error', err);
      },
    });
  }

  // ── UI helpers ────────────────────────────────────────────────────────────

  monthName(m: MonthlyBreakdown): string {
    return this.monthNames[m.month - 1] ?? String(m.month);
  }

  pnlClass(v: number): string {
    if (v > 0) return 'tp-bull';
    if (v < 0) return 'tp-bear';
    return '';
  }
}
