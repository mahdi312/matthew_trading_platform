import {
  Component,
  OnInit,
  inject,
  signal,
  computed,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
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
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatBadgeModule } from '@angular/material/badge';

import { AlertsApiService } from './alerts-api.service';
import { PriceAlert, SaveAlertRequest, AlertCondition, AlertStatus } from './alerts.models';

/**
 * AlertsModule — full CRUD UI for price alert management.
 *
 * This is alert *management* (create / edit / delete / list), NOT live delivery.
 * Live delivery is handled by the existing NotificationBellComponent which
 * subscribes to the STOMP topic — that component is NOT changed here.
 *
 * Features:
 *  1. Alert list showing all user alerts with symbol, condition, target, status.
 *  2. Inline create form to add a new PriceAlert.
 *  3. Edit mode per row — update target value, condition, message.
 *  4. Delete with confirmation.
 *  5. Status badge (ACTIVE / FIRED / DISABLED) with visual distinction.
 *  6. History section that separates fired alerts from active ones.
 *
 * Route: /alerts  (behind authGuard)
 */
@Component({
  selector: 'app-alerts-page',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    RouterModule,
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
    MatDialogModule,
    MatDividerModule,
    MatBadgeModule,
  ],
  templateUrl: './alerts-page.component.html',
  styleUrls: ['./alerts-page.component.scss'],
})
export class AlertsPageComponent implements OnInit {
  private readonly api    = inject(AlertsApiService);
  private readonly snack  = inject(MatSnackBar);
  private readonly fb     = inject(FormBuilder);

  // ── State ─────────────────────────────────────────────────────────────────

  readonly loading  = signal(true);
  readonly saving   = signal(false);
  readonly error    = signal<string | null>(null);

  readonly alerts   = signal<PriceAlert[]>([]);

  /** Derived: active (non-fired) alerts */
  readonly activeAlerts = computed(() =>
    this.alerts().filter(a => a.status === 'ACTIVE' || a.status === 'DISABLED')
  );

  /** Derived: fired alerts (history) */
  readonly firedAlerts = computed(() =>
    this.alerts().filter(a => a.status === 'FIRED')
  );

  /** Currently edited alert id, or null when creating */
  readonly editingId = signal<string | null>(null);

  // ── Form ──────────────────────────────────────────────────────────────────

  readonly createForm: FormGroup = this.fb.group({
    symbol:      ['', [Validators.required, Validators.minLength(2)]],
    condition:   ['ABOVE' as AlertCondition, Validators.required],
    targetValue: [null, [Validators.required, Validators.min(0)]],
    message:     [''],
  });

  readonly editForm: FormGroup = this.fb.group({
    symbol:      ['', [Validators.required, Validators.minLength(2)]],
    condition:   ['ABOVE' as AlertCondition, Validators.required],
    targetValue: [null, [Validators.required, Validators.min(0)]],
    message:     [''],
  });

  // ── Table columns ─────────────────────────────────────────────────────────

  readonly activeColumns = ['symbol', 'condition', 'targetValue', 'status', 'message', 'createdAt', 'actions'];
  readonly historyColumns = ['symbol', 'condition', 'targetValue', 'firedAt', 'message'];

  // ── Condition options ─────────────────────────────────────────────────────

  readonly conditions: { value: AlertCondition; label: string }[] = [
    { value: 'ABOVE',         label: 'Price >' },
    { value: 'BELOW',         label: 'Price <' },
    { value: 'CROSSES_ABOVE', label: 'Crosses Above' },
    { value: 'CROSSES_BELOW', label: 'Crosses Below' },
  ];

  // ── Lifecycle ─────────────────────────────────────────────────────────────

  ngOnInit(): void {
    this.loadAlerts();
  }

  // ── Data loading ──────────────────────────────────────────────────────────

  loadAlerts(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.getAlerts().subscribe({
      next: (data) => {
        this.alerts.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set('Failed to load alerts. Please try again.');
        this.loading.set(false);
        console.error('[AlertsPage] load error', err);
      },
    });
  }

  // ── Create ────────────────────────────────────────────────────────────────

  onCreateSubmit(): void {
    if (this.createForm.invalid) return;
    this.saving.set(true);

    const req: SaveAlertRequest = {
      symbol:      this.createForm.value.symbol.toUpperCase().trim(),
      condition:   this.createForm.value.condition,
      targetValue: Number(this.createForm.value.targetValue),
      message:     this.createForm.value.message?.trim() || null,
    };

    this.api.createAlert(req).subscribe({
      next: (created) => {
        this.alerts.update(list => [created, ...list]);
        this.createForm.reset({ condition: 'ABOVE' });
        this.saving.set(false);
        this.snack.open(`Alert for ${created.symbol} created.`, 'OK', { duration: 3000 });
      },
      error: (err) => {
        this.saving.set(false);
        this.snack.open('Failed to create alert.', 'Close', { duration: 4000 });
        console.error('[AlertsPage] create error', err);
      },
    });
  }

  // ── Edit ──────────────────────────────────────────────────────────────────

  startEdit(alert: PriceAlert): void {
    this.editingId.set(alert.id);
    this.editForm.setValue({
      symbol:      alert.symbol,
      condition:   alert.condition,
      targetValue: alert.targetValue,
      message:     alert.message ?? '',
    });
  }

  cancelEdit(): void {
    this.editingId.set(null);
  }

  onEditSubmit(alert: PriceAlert): void {
    if (this.editForm.invalid) return;
    this.saving.set(true);

    const req: Partial<SaveAlertRequest> = {
      symbol:      this.editForm.value.symbol.toUpperCase().trim(),
      condition:   this.editForm.value.condition,
      targetValue: Number(this.editForm.value.targetValue),
      message:     this.editForm.value.message?.trim() || null,
    };

    this.api.updateAlert(alert.id, req).subscribe({
      next: (updated) => {
        this.alerts.update(list => list.map(a => a.id === updated.id ? updated : a));
        this.editingId.set(null);
        this.saving.set(false);
        this.snack.open('Alert updated.', 'OK', { duration: 3000 });
      },
      error: (err) => {
        this.saving.set(false);
        this.snack.open('Failed to update alert.', 'Close', { duration: 4000 });
        console.error('[AlertsPage] update error', err);
      },
    });
  }

  // ── Delete ────────────────────────────────────────────────────────────────

  onDelete(alert: PriceAlert): void {
    if (!confirm(`Delete alert for ${alert.symbol} @ ${alert.targetValue}?`)) return;

    this.api.deleteAlert(alert.id).subscribe({
      next: () => {
        this.alerts.update(list => list.filter(a => a.id !== alert.id));
        this.snack.open('Alert deleted.', 'OK', { duration: 2500 });
      },
      error: (err) => {
        this.snack.open('Failed to delete alert.', 'Close', { duration: 4000 });
        console.error('[AlertsPage] delete error', err);
      },
    });
  }

  // ── UI helpers ────────────────────────────────────────────────────────────

  statusColor(status: AlertStatus): string {
    switch (status) {
      case 'ACTIVE':   return 'primary';
      case 'FIRED':    return 'accent';
      case 'DISABLED': return 'warn';
      default:         return '';
    }
  }

  statusIcon(status: AlertStatus): string {
    switch (status) {
      case 'ACTIVE':   return 'notifications_active';
      case 'FIRED':    return 'notifications';
      case 'DISABLED': return 'notifications_off';
      default:         return 'notifications';
    }
  }

  conditionLabel(c: AlertCondition): string {
    return this.conditions.find(x => x.value === c)?.label ?? c;
  }

  trackById(_: number, item: { id: string }): string { return item.id; }
}
