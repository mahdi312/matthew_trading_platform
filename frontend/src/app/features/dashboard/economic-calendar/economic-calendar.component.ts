import {
  Component,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatChipsModule } from '@angular/material/chips';
import { MatDividerModule } from '@angular/material/divider';

import {
  EconomicCalendarApiService,
  EconomicEventDto,
} from './economic-calendar-api.service';

/**
 * EconomicCalendarComponent — glanceable dashboard widget.
 *
 * Shows the next 7 days of economic events from
 * GET /api/reference/calendar?from=...&to=...&impactLevel=...
 *
 * Compact list layout: date column, event name, impact badge.
 * No chart/graph — this is reference data, not analysis.
 *
 * Placed on the dashboard (not a full route).
 */
@Component({
  selector: 'app-economic-calendar',
  standalone: true,
  imports: [
    CommonModule,
    DatePipe,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    MatChipsModule,
    MatDividerModule,
  ],
  templateUrl: './economic-calendar.component.html',
  styleUrls: ['./economic-calendar.component.scss'],
})
export class EconomicCalendarComponent implements OnInit {
  private readonly api = inject(EconomicCalendarApiService);

  // ── State ─────────────────────────────────────────────────────────────────

  readonly loading = signal(true);
  readonly error   = signal<string | null>(null);
  readonly events  = signal<EconomicEventDto[]>([]);

  /** Currently active impact filter. Empty string = all. */
  readonly impactFilter = signal<string>('');

  // ── Lifecycle ─────────────────────────────────────────────────────────────

  ngOnInit(): void {
    this.loadCalendar();
  }

  // ── Data loading ──────────────────────────────────────────────────────────

  loadCalendar(): void {
    this.loading.set(true);
    this.error.set(null);

    const today = new Date();
    const in7   = new Date(today);
    in7.setDate(today.getDate() + 7);

    const fmt = (d: Date): string => d.toISOString().slice(0, 10); // YYYY-MM-DD

    this.api.getCalendar(
      fmt(today),
      fmt(in7),
      'STOCK',
      this.impactFilter() || undefined
    ).subscribe({
      next: (data) => {
        this.events.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set('Failed to load economic calendar.');
        this.loading.set(false);
        console.error('[EconomicCalendar] error', err);
      },
    });
  }

  // ── Filter ────────────────────────────────────────────────────────────────

  setImpactFilter(level: string): void {
    this.impactFilter.set(level);
    this.loadCalendar();
  }

  // ── UI helpers ────────────────────────────────────────────────────────────

  /** Material color for impact badge. */
  impactColor(level: string | null): string {
    switch (level?.toUpperCase()) {
      case 'HIGH':   return 'warn';
      case 'MEDIUM': return 'accent';
      default:       return '';
    }
  }

  /** CSS class for the impact badge dot. */
  impactClass(level: string | null): string {
    switch (level?.toUpperCase()) {
      case 'HIGH':   return 'impact-high';
      case 'MEDIUM': return 'impact-medium';
      default:       return 'impact-low';
    }
  }

  /** Format an Instant string to a short local date+time. */
  formatScheduledAt(iso: string | null): string {
    if (!iso) return '—';
    const d = new Date(iso);
    return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
      + ' '
      + d.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' });
  }

  /** Format date only (for grouping label). */
  formatDateOnly(iso: string | null): string {
    if (!iso) return '—';
    return new Date(iso).toLocaleDateString('en-US', {
      weekday: 'short', month: 'short', day: 'numeric',
    });
  }

  trackByEventId(_: number, event: EconomicEventDto): string {
    return event.eventId ?? _;
  }
}
