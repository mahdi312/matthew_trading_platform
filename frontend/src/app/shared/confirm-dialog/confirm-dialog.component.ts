import {ChangeDetectionStrategy, Component, Inject} from '@angular/core';
import {CommonModule} from '@angular/common';
import {MAT_DIALOG_DATA, MatDialogModule, MatDialogRef,} from '@angular/material/dialog';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {A11yModule} from '@angular/cdk/a11y';

/**
 * Data contract for {@link ConfirmDialogComponent}.
 *
 * `message` must be specific to the action being confirmed — never a
 * generic "Are you sure?" — per the Admin (Prompt 13) requirement that
 * every consequential, hard-to-undo action states exactly what will
 * change before it happens.
 */
export interface ConfirmDialogData {
  /** Short dialog title, e.g. "Suspend user". */
  title: string;
  /** The specific consequence of confirming, e.g.
   * "Suspend this user? They'll be signed out and unable to log in
   * until reactivated." */
  message: string;
  /** Label for the confirm button. Defaults to "Confirm". */
  confirmLabel?: string;
  /** Label for the cancel button. Defaults to "Cancel". */
  cancelLabel?: string;
  /** Styles the confirm button as destructive (bear-tinted) when true. */
  danger?: boolean;
  /** Icon shown next to the title. Defaults to 'warning' (danger) / 'help_outline'. */
  icon?: string;
}

/**
 * ConfirmDialogComponent
 *
 * A single, reusable MatDialog-based confirmation used anywhere a mutating
 * action needs an explicit, action-specific confirmation — first consumer
 * is AdminPageComponent (edit roles, change status, delete user), each with
 * distinct, plain-language copy describing exactly what will change.
 *
 * Usage:
 *   const ref = this.dialog.open(ConfirmDialogComponent, {
 *     data: { title: 'Delete user', message: '…', danger: true, confirmLabel: 'Delete' },
 *   });
 *   ref.afterClosed().subscribe(confirmed => { if (confirmed) { ... } });
 */
@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule, MatIconModule, A11yModule],
  template: `
    <div class="confirm-dialog" [class.confirm-dialog--danger]="data.danger">
      <div class="confirm-dialog__header">
        <mat-icon aria-hidden="true">{{ data.icon ?? (data.danger ? 'warning' : 'help_outline') }}</mat-icon>
        <h2 class="confirm-dialog__title" mat-dialog-title>{{ data.title }}</h2>
      </div>

      <div mat-dialog-content class="confirm-dialog__body">
        <p>{{ data.message }}</p>
      </div>

      <div mat-dialog-actions align="end" class="confirm-dialog__actions">
        <button mat-button type="button" (click)="dialogRef.close(false)">
          {{ data.cancelLabel ?? 'Cancel' }}
        </button>
        <button
          mat-flat-button
          type="button"
          class="confirm-dialog__confirm-btn"
          [class.confirm-dialog__confirm-btn--danger]="data.danger"
          (click)="dialogRef.close(true)"
          cdkFocusInitial>
          {{ data.confirmLabel ?? 'Confirm' }}
        </button>
      </div>
    </div>
  `,
  styles: [`
    :host {
      display: block;
    }

    .confirm-dialog {
      min-width: 320px;
      max-width: 420px;
    }

    .confirm-dialog__header {
      display: flex;
      align-items: center;
      gap: 10px;
      margin-bottom: 4px;

      mat-icon {
        color: var(--brass-500);
        flex-shrink: 0;
      }
    }

    .confirm-dialog--danger .confirm-dialog__header mat-icon {
      color: var(--bear-500);
    }

    .confirm-dialog__title {
      margin: 0 !important;
      font-family: 'Space Grotesk', 'IBM Plex Sans', sans-serif;
      font-size: 1.05rem !important;
      font-weight: 600 !important;
      color: var(--tp-text-primary);
    }

    .confirm-dialog__body p {
      margin: 0;
      font-size: 13.5px;
      line-height: 1.6;
      color: var(--tp-text-secondary);
    }

    .confirm-dialog__actions {
      margin-top: 8px;
      padding-top: 0;
    }

    .confirm-dialog__confirm-btn {
      background: var(--brass-500) !important;
      color: #0A0D12 !important;
      font-weight: 600 !important;
      border-radius: var(--tp-radius-md) !important;
    }

    .confirm-dialog__confirm-btn--danger {
      background: var(--bear-500) !important;
      color: #ffffff !important;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConfirmDialogComponent {
  constructor(
    public dialogRef: MatDialogRef<ConfirmDialogComponent, boolean>,
    @Inject(MAT_DIALOG_DATA) public data: ConfirmDialogData
  ) {
  }
}
