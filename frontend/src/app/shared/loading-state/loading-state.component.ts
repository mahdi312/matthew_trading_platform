import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';

/**
 * LoadingStateComponent
 *
 * Skeleton-row loading placeholder for list/table screens.
 * Used by every data-driven screen so loading moments look intentional.
 *
 * Usage:
 *   <app-loading-state [rows]="5" [showHeader]="true" />
 *
 * For a single-item / card load:
 *   <app-loading-state variant="card" />
 */
@Component({
  selector: 'app-loading-state',
  standalone: true,
  imports: [CommonModule],
  template: `
    @if (variant === 'rows') {
      <div class="ls-wrap" [attr.aria-label]="'Loading data'">
        @if (showHeader) {
          <div class="ls-header">
            @for (_ of headerCols; track $index) {
              <div class="ls-skeleton ls-skeleton--header"></div>
            }
          </div>
        }
        @for (_ of rowArr; track $index) {
          <div class="ls-row">
            @for (_ of colArr; track $index) {
              <div class="ls-skeleton ls-skeleton--cell" [style.width]="randomWidth()"></div>
            }
          </div>
        }
      </div>
    }

    @if (variant === 'card') {
      <div class="ls-card-wrap" aria-label="Loading">
        <div class="ls-card">
          <div class="ls-skeleton ls-skeleton--title" style="width: 55%"></div>
          <div class="ls-skeleton ls-skeleton--text" style="width: 80%"></div>
          <div class="ls-skeleton ls-skeleton--text" style="width: 65%"></div>
          <div class="ls-skeleton ls-skeleton--text" style="width: 40%"></div>
        </div>
      </div>
    }

    @if (variant === 'kpi') {
      <div class="ls-kpi-wrap" aria-label="Loading metrics">
        @for (_ of rowArr; track $index) {
          <div class="ls-kpi-card">
            <div class="ls-skeleton ls-skeleton--kpi-label" style="width: 55%"></div>
            <div class="ls-skeleton ls-skeleton--kpi-value" style="width: 70%"></div>
          </div>
        }
      </div>
    }
  `,
  styles: [`
    :host {
      display: block;
      width: 100%;
    }

    /* Pulse animation */
    @keyframes ls-pulse {
      0%, 100% { opacity: 1; }
      50%       { opacity: 0.45; }
    }

    .ls-skeleton {
      background: var(--tp-bg-active);
      border-radius: 4px;
      animation: ls-pulse 1.6s ease-in-out infinite;
    }

    @media (prefers-reduced-motion: reduce) {
      .ls-skeleton { animation: none; opacity: 0.6; }
    }

    /* ── Rows variant ─────────────────────────── */
    .ls-wrap {
      display: flex;
      flex-direction: column;
      gap: 0;
    }

    .ls-header {
      display: flex;
      gap: 12px;
      align-items: center;
      padding: 10px 16px;
      border-bottom: 1px solid var(--hairline);
      margin-bottom: 2px;
    }

    .ls-skeleton--header {
      height: 10px;
      flex: 1;
      max-width: 120px;
    }

    .ls-row {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 14px 16px;
      border-bottom: 1px solid var(--hairline);
    }

    .ls-skeleton--cell {
      height: 13px;
      flex-shrink: 0;
    }

    /* ── Card variant ─────────────────────────── */
    .ls-card-wrap {
      padding: 16px;
    }

    .ls-card {
      display: flex;
      flex-direction: column;
      gap: 10px;
    }

    .ls-skeleton--title {
      height: 18px;
      margin-bottom: 4px;
    }

    .ls-skeleton--text {
      height: 12px;
    }

    /* ── KPI variant ──────────────────────────── */
    .ls-kpi-wrap {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
      gap: 12px;
    }

    .ls-kpi-card {
      background: var(--tp-bg-card);
      border: 1px solid var(--hairline);
      border-radius: var(--tp-radius-md);
      padding: 16px;
      display: flex;
      flex-direction: column;
      gap: 10px;
    }

    .ls-skeleton--kpi-label { height: 11px; }
    .ls-skeleton--kpi-value { height: 28px; margin-top: 4px; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoadingStateComponent {
  /** Layout variant */
  @Input() variant: 'rows' | 'card' | 'kpi' = 'rows';
  /** Number of skeleton rows / KPI cards */
  @Input() rows = 6;
  /** Number of columns per skeleton row */
  @Input() cols = 5;
  /** Show a header row above the skeleton rows */
  @Input() showHeader = true;

  get rowArr(): unknown[] { return Array(this.rows); }
  get colArr(): unknown[] { return Array(this.cols); }
  get headerCols(): unknown[] { return Array(this.cols); }

  /** Vary widths for a more organic look */
  randomWidth(): string {
    const widths = ['60px', '80px', '100px', '70px', '90px', '55px', '120px'];
    return widths[Math.floor(Math.random() * widths.length)];
  }
}
