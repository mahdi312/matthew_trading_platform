import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';

/**
 * EmptyStateComponent
 *
 * Presentational component for empty data states. Used by every list/table
 * screen in the app for consistent empty and "no results" moments.
 *
 * Usage:
 *   <app-empty-state
 *     icon="book"
 *     headline="No trades logged yet"
 *     body="Start by logging your first trade from the Live Trading screen."
 *     actionLabel="Log a Trade"
 *     (action)="navigateToLiveTrading()"
 *   />
 */
@Component({
  selector: 'app-empty-state',
  standalone: true,
  imports: [CommonModule, MatIconModule, MatButtonModule],
  template: `
    <div class="empty-state-wrap">
      <div class="empty-icon-ring">
        <mat-icon class="empty-icon" aria-hidden="true">{{ icon }}</mat-icon>
      </div>
      <h3 class="empty-headline">{{ headline }}</h3>
      @if (body) {
        <p class="empty-body">{{ body }}</p>
      }
      @if (actionLabel) {
        <button
          mat-flat-button
          class="empty-action"
          color="primary"
          type="button"
          (click)="action.emit()"
        >
          {{ actionLabel }}
        </button>
      }
    </div>
  `,
  styles: [`
    :host {
      display: flex;
      justify-content: center;
      align-items: center;
      width: 100%;
      min-height: 220px;
      padding: 48px 24px;
    }

    .empty-state-wrap {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 12px;
      max-width: 380px;
      text-align: center;
    }

    .empty-icon-ring {
      width: 64px;
      height: 64px;
      border-radius: 50%;
      background: color-mix(in srgb, var(--brass-500) 10%, var(--tp-bg-hover));
      border: 1px solid color-mix(in srgb, var(--brass-500) 20%, var(--hairline));
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }

    .empty-icon {
      font-size: 28px;
      width: 28px;
      height: 28px;
      color: var(--paper-600) !important;
    }

    .empty-headline {
      font-family: 'Space Grotesk', 'IBM Plex Sans', sans-serif;
      font-size: 1rem;
      font-weight: 600;
      color: var(--tp-text-primary);
      margin: 0;
      line-height: 1.4;
    }

    .empty-body {
      font-size: 12.5px;
      color: var(--tp-text-secondary);
      margin: 0;
      line-height: 1.55;
    }

    .empty-action {
      margin-top: 4px;
      background: var(--brass-500) !important;
      color: #0A0D12 !important;
      font-weight: 600;
      border-radius: var(--tp-radius-md);
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmptyStateComponent {
  @Input() icon = 'inbox';
  @Input() headline = 'Nothing here yet';
  @Input() body?: string;
  @Input() actionLabel?: string;
  @Output() action = new EventEmitter<void>();
}
