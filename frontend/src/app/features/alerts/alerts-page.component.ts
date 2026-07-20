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
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';

import { AlertsApiService } from './alerts-api.service';
import {
  PriceAlert,
  SaveAlertRequest,
  AlertCondition,
  AlertStatus,
  CONDITION_OPTIONS,
  BULLISH_CONDITIONS,
} from './alerts.models';
import { SymbolSearchComponent, SymbolSearchResult } from '../../shared/symbol-search';
import { EmptyStateComponent } from '../../shared/empty-state/empty-state.component';
import { LoadingStateComponent } from '../../shared/loading-state/loading-state.component';

/**
 * AlertsModule — full alert *management* CRUD UI (create / edit / delete / history).
 *
 * This is deliberately separate from live delivery, which is owned entirely by
 * the existing NotificationBellComponent (STOMP push when an alert fires) —
 * nothing here duplicates that subscription or delivery logic.
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
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatTooltipModule,
    MatSnackBarModule,
    SymbolSearchComponent,
    EmptyStateComponent,
    LoadingStateComponent,
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

  /** Show/hide the create form panel. */
  readonly createOpen = signal(false);

  /** Derived: active + disabled alerts — what the user is currently watching for. */
  readonly activeAlerts = computed(() =>
    this.alerts()
      .filter(a => a.status === 'ACTIVE' || a.status === 'DISABLED')
      .sort((a, b) => (a.status === b.status ? 0 : a.status === 'ACTIVE' ? -1 : 1))
  );

  /** Derived: fired alerts — history, most recent first. */
  readonly firedAlerts = computed(() =>
    this.alerts()
      .filter(a => a.status === 'FIRED')
      .sort((a, b) => new Date(b.firedAt ?? 0).getTime() - new Date(a.firedAt ?? 0).getTime())
  );

  /** Currently edited alert id, or null when not editing. */
  readonly editingId = signal<string | null>(null);

  // ── Form ──────────────────────────────────────────────────────────────────

  readonly conditions = CONDITION_OPTIONS;

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

  /** Signal mirror of the create form's raw values — updated on every keystroke so the preview re-renders. */
  private readonly createFormValue = signal<{ symbol: string; condition: AlertCondition; targetValue: number | null }>({
    symbol: '', condition: 'ABOVE', targetValue: null,
  });

  /** Live-readable sentence preview of the create form, e.g. "Alert me when BTCUSDT crosses above $72,000". */
  readonly createPreview = computed(() => this.buildPreview(this.createFormValue()));

  // ── Lifecycle ─────────────────────────────────────────────────────────────

  ngOnInit(): void {
    this.loadAlerts();
    // Keep the sentence preview reactive as the user types.
    this.createForm.valueChanges.subscribe((v) => {
      this.createFormValue.set({
        symbol: (v.symbol ?? '').toUpperCase().trim(),
        condition: v.condition,
        targetValue: v.targetValue,
      });
    });
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

  openCreateForm(): void {
    this.createOpen.set(true);
  }

  closeCreateForm(): void {
    this.createOpen.set(false);
    this.createForm.reset({ symbol: '', condition: 'ABOVE', targetValue: null, message: '' });
  }

  onCreateSymbolSelected(result: SymbolSearchResult): void {
    this.createForm.patchValue({ symbol: result.ticker });
  }

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
        this.saving.set(false);
        this.closeCreateForm();
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

  // ── Toggle active/disabled (optimistic) ──────────────────────────────────

  toggleStatus(alert: PriceAlert): void {
    const nextStatus: AlertStatus = alert.status === 'DISABLED' ? 'ACTIVE' : 'DISABLED';

    // Optimistic UI — flip immediately, reconcile on response.
    this.alerts.update(list => list.map(a => a.id === alert.id ? { ...a, status: nextStatus } : a));

    this.api.updateAlert(alert.id, {
      symbol:      alert.symbol,
      condition:   alert.condition,
      targetValue: alert.targetValue,
      message:     alert.message,
      status:      nextStatus,
    } as SaveAlertRequest & { status: AlertStatus }).subscribe({
      next: (updated) => {
        this.alerts.update(list => list.map(a => a.id === updated.id ? updated : a));
      },
      error: () => {
        // Reconcile — revert the optimistic flip.
        this.alerts.update(list => list.map(a => a.id === alert.id ? alert : a));
        this.snack.open('Failed to update alert status.', 'Close', { duration: 4000 });
      },
    });
  }

  // ── Delete ────────────────────────────────────────────────────────────────

  onDelete(alert: PriceAlert): void {
    if (!confirm(`Delete alert for ${alert.symbol} @ ${alert.targetValue}?\n\nThis cannot be undone.`)) return;

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

  /** Builds the readable sentence for a given symbol/condition/target combo. */
  buildPreview(v: { symbol: string; condition: AlertCondition; targetValue: number | null }): string {
    if (!v.symbol || !v.targetValue) return 'Fill in the fields above to preview your alert.';
    const phrase = this.conditions.find(c => c.value === v.condition)?.phrase ?? v.condition;
    return `Alert me when ${v.symbol} ${phrase} $${this.formatTarget(v.targetValue)}`;
  }

  private formatTarget(n: number): string {
    return n.toLocaleString('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 8 });
  }

  conditionLabel(c: AlertCondition): string {
    return this.conditions.find(x => x.value === c)?.label ?? c;
  }

  /** Full sentence for a persisted alert row, e.g. "BTCUSDT crosses above $72,000". */
  alertSentence(alert: PriceAlert): string {
    const phrase = this.conditions.find(c => c.value === alert.condition)?.phrase ?? alert.condition;
    return `${alert.symbol} ${phrase} $${this.formatTarget(alert.targetValue)}`;
  }

  /** Whether a fired alert's trigger was bullish (rose/crossed above) or bearish. */
  isBullishCondition(c: AlertCondition): boolean {
    return BULLISH_CONDITIONS.includes(c);
  }

  statusChipClass(alert: PriceAlert): string {
    if (alert.status === 'ACTIVE')   return 'chip-active';
    if (alert.status === 'DISABLED') return 'chip-disabled';
    // FIRED — color by trigger direction
    return this.isBullishCondition(alert.condition) ? 'chip-fired-bull' : 'chip-fired-bear';
  }

  statusIcon(alert: PriceAlert): string {
    if (alert.status === 'ACTIVE')   return 'notifications_active';
    if (alert.status === 'DISABLED') return 'notifications_off';
    return this.isBullishCondition(alert.condition) ? 'trending_up' : 'trending_down';
  }

  trackById(_: number, item: { id: string }): string { return item.id; }
}
