import {
  Component,
  OnInit,
  inject,
  signal,
  computed,
} from '@angular/core';
import { CommonModule, DecimalPipe, PercentPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDividerModule } from '@angular/material/divider';

import { ReportsApiService } from './reports-api.service';
import { YearlyReport, MonthlyBreakdown } from './reports.models';

/**
 * ReportsModule — yearly P&L report + Excel export.
 *
 * Features:
 *  1. Year selector (defaults to current year).
 *  2. Summary KPI cards — total trades, wins/losses, net P&L, win rate, max drawdown,
 *     Sharpe ratio.
 *  3. Monthly breakdown table showing all 12 months with per-month stats.
 *  4. Export button — calls GET /api/reports/export.xlsx (responseType: 'blob')
 *     and triggers a browser file-save dialog via a temporary <a> element.
 *
 * Route: /reports  (behind authGuard)
 */
@Component({
  selector: 'app-reports-page',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterModule,
    DecimalPipe,
    PercentPipe,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    MatChipsModule,
    MatTooltipModule,
    MatSnackBarModule,
    MatDividerModule,
  ],
  templateUrl: './reports-page.component.html',
  styleUrls: ['./reports-page.component.scss'],
})
export class ReportsPageComponent implements OnInit {
  private readonly api   = inject(ReportsApiService);
  private readonly snack = inject(MatSnackBar);

  // ── Year selection ────────────────────────────────────────────────────────

  readonly currentYear = new Date().getFullYear();
  readonly availableYears: number[] = Array.from(
    { length: 5 },
    (_, i) => this.currentYear - i
  );
  readonly selectedYear = signal<number>(this.currentYear);

  // ── Report state ──────────────────────────────────────────────────────────

  readonly loading    = signal(true);
  readonly exporting  = signal(false);
  readonly error      = signal<string | null>(null);
  readonly report     = signal<YearlyReport | null>(null);

  /** Month name lookup for the table */
  readonly monthNames = [
    'Jan','Feb','Mar','Apr','May','Jun',
    'Jul','Aug','Sep','Oct','Nov','Dec',
  ];

  // ── Monthly table config ─────────────────────────────────────────────────

  readonly monthColumns = [
    'month', 'trades', 'wins', 'losses',
    'winRate', 'grossPnl', 'netPnl', 'avgRrr',
  ];

  // ── Lifecycle ─────────────────────────────────────────────────────────────

  ngOnInit(): void {
    this.loadReport();
  }

  // ── Load report ───────────────────────────────────────────────────────────

  loadReport(): void {
    this.loading.set(true);
    this.error.set(null);
    this.report.set(null);

    this.api.getYearlyReport(this.selectedYear()).subscribe({
      next: (data) => { this.report.set(data); this.loading.set(false); },
      error: (err) => {
        this.error.set('Failed to load report. Please try again.');
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
   * Calls the export endpoint and triggers a browser file-save dialog.
   *
   * Uses the standard "blob → URL → temporary anchor click" technique so
   * the file is saved to disk rather than opened in-app.
   */
  exportExcel(): void {
    this.exporting.set(true);
    this.api.exportExcel(this.selectedYear()).subscribe({
      next: (blob) => {
        const url      = URL.createObjectURL(blob);
        const anchor   = document.createElement('a');
        anchor.href    = url;
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
    return this.monthNames[(m.month - 1)] ?? String(m.month);
  }

  pnlClass(v: number): string {
    if (v > 0) return 'positive';
    if (v < 0) return 'negative';
    return '';
  }
}
