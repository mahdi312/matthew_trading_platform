import {
  Component,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDividerModule } from '@angular/material/divider';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatExpansionModule } from '@angular/material/expansion';
import { HttpErrorResponse } from '@angular/common/http';

import { SettingsApiService } from './settings-api.service';
import { BrokerLinkApiService } from './broker-link-api.service';
import { UserProfile, UpdateProfileRequest, NotificationPreferences, UpdateNotificationPreferencesRequest } from './settings.models';

/**
 * Supported brokers shown in the "Connected Accounts" section.
 * BitUnix is the Phase-1 live target; the rest are listed as "coming soon"
 * to give users visibility without pretending they're available.
 */
interface BrokerCard {
  /** Matches BrokerType enum value on the backend (case-insensitive). */
  type:        string;
  /** Display label. */
  label:       string;
  /** Short description shown beneath the label. */
  description: string;
  /** Whether this broker is currently available for linking (others show "Coming soon"). */
  available:   boolean;
  /** Material icon name. */
  icon:        string;
}

/**
 * SettingsModule — user profile + app settings editor + Connected Accounts.
 *
 * Loads the current user's profile from `GET /api/profile` on init,
 * pre-fills the form, and submits updates via `PUT /api/profile`.
 *
 * Editable fields: Display Name, Avatar URL, Timezone, Currency.
 * Read-only fields: Username, Email, Account created date.
 *
 * Notifications section (separate form + API):
 *   GET/PUT /api/profile/preferences — in-app, email, Telegram channel toggles
 *   and optional email / Telegram chat-id overrides.
 *
 * Connected Accounts section:
 *  - One card per supported broker.
 *  - If not connected: shows a form to enter API key + secret (masked).
 *  - If connected: shows status + disconnect action.
 *  - The backend (BrokerLinkController) is currently stubbed (501);
 *    the UI handles this gracefully and warns the user.
 *
 * Route: /settings  (behind authGuard)
 */
@Component({
  selector: 'app-settings-page',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    RouterModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatSlideToggleModule,
    MatProgressSpinnerModule,
    MatDividerModule,
    MatSnackBarModule,
    MatChipsModule,
    MatTooltipModule,
    MatExpansionModule,
  ],
  templateUrl: './settings-page.component.html',
  styleUrls: ['./settings-page.component.scss'],
})
export class SettingsPageComponent implements OnInit {
  private readonly api        = inject(SettingsApiService);
  private readonly brokerApi  = inject(BrokerLinkApiService);
  private readonly snack      = inject(MatSnackBar);
  private readonly fb         = inject(FormBuilder);

  // ── State ─────────────────────────────────────────────────────────────────

  readonly loading = signal(true);
  readonly saving  = signal(false);
  readonly error   = signal<string | null>(null);
  readonly profile = signal<UserProfile | null>(null);

  readonly notificationsLoading = signal(true);
  readonly notificationsSaving  = signal(false);
  readonly notificationsError   = signal<string | null>(null);
  readonly notificationPrefs      = signal<NotificationPreferences | null>(null);

  // ── Form ──────────────────────────────────────────────────────────────────

  readonly form: FormGroup = this.fb.group({
    displayName: [''],
    avatarUrl:   [''],
    timezone:    [''],
    currency:    ['USD'],
  });

  readonly notificationsForm: FormGroup = this.fb.group({
    inAppEnabled:    [true],
    emailEnabled:    [true],
    email:           [''],
    telegramEnabled: [false],
    telegramChatId:  [''],
  });

  // ── Currency options ──────────────────────────────────────────────────────

  readonly currencies = ['USD', 'EUR', 'GBP', 'JPY', 'BTC', 'ETH'];

  // ── Timezone options (common subset) ──────────────────────────────────────

  readonly timezones = [
    'UTC',
    'America/New_York',
    'America/Chicago',
    'America/Denver',
    'America/Los_Angeles',
    'Europe/London',
    'Europe/Paris',
    'Europe/Berlin',
    'Asia/Tokyo',
    'Asia/Shanghai',
    'Asia/Singapore',
    'Australia/Sydney',
  ];

  // ── Broker cards ──────────────────────────────────────────────────────────

  /**
   * Supported brokers.
   * BitUnix is Phase-1 live; others are stubbed/coming-soon on the backend too.
   * Keep this list in sync with the BrokerType enum in shared/contracts.
   */
  readonly brokers: BrokerCard[] = [
    {
      type:        'BITUNIX',
      label:       'BitUnix',
      description: 'Crypto spot & futures exchange — Phase 1 live integration.',
      available:   true,
      icon:        'currency_bitcoin',
    },
    {
      type:        'BINANCE',
      label:       'Binance',
      description: 'Global crypto spot & futures exchange.',
      available:   false,
      icon:        'account_balance',
    },
    {
      type:        'COINBASE',
      label:       'Coinbase',
      description: 'Coinbase Advanced Trade API.',
      available:   false,
      icon:        'paid',
    },
    {
      type:        'ALPACA',
      label:       'Alpaca',
      description: 'US equities & crypto broker.',
      available:   false,
      icon:        'show_chart',
    },
    {
      type:        'PAPER',
      label:       'Paper Trading',
      description: 'Internal paper-trading simulator — no real API key required.',
      available:   false,
      icon:        'science',
    },
  ];

  /**
   * Per-broker link forms — keyed by broker type.
   * Each form has: apiKey (required), apiSecret (required, masked), label (optional).
   */
  readonly brokerForms: Record<string, FormGroup> = {};

  /**
   * Per-broker submitting state.
   */
  readonly brokerSubmitting: Record<string, boolean> = {};

  /**
   * Per-broker connected state.
   * Populated by listConnections() on init; updated optimistically on connect/disconnect.
   *
   * NOTE: The backend is currently stubbed (501), so this starts empty and
   * connect/disconnect calls will return a 501 response — the UI shows an
   * informational notice about this.
   */
  readonly brokerConnected: Record<string, boolean> = {};

  /** Whether the broker connection list has been loaded. */
  readonly brokerStatusLoaded = signal(false);

  /** Whether the broker backend is stubbed (501 responses). */
  readonly brokerBackendStubbed = signal(false);

  // ── Lifecycle ─────────────────────────────────────────────────────────────

  ngOnInit(): void {
    // Build a reactive form for each available broker.
    for (const broker of this.brokers) {
      this.brokerForms[broker.type] = this.fb.group({
        apiKey:    ['', Validators.required],
        apiSecret: ['', Validators.required],
        label:     [''],
      });
      this.brokerSubmitting[broker.type] = false;
      this.brokerConnected[broker.type]  = false;
    }

    this.api.getProfile().subscribe({
      next: (p) => {
        this.profile.set(p);
        this.form.patchValue({
          displayName: p.displayName ?? '',
          avatarUrl:   p.avatarUrl ?? '',
          timezone:    p.timezone ?? 'UTC',
          currency:    p.currency ?? 'USD',
        });
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set('Failed to load profile.');
        this.loading.set(false);
        console.error('[Settings] load error', err);
      },
    });

    this.loadNotificationPreferences();
    this.loadBrokerConnections();
  }

  private loadNotificationPreferences(): void {
    this.api.getNotificationPreferences().subscribe({
      next: (prefs) => {
        this.notificationPrefs.set(prefs);
        this.notificationsForm.patchValue({
          inAppEnabled:    prefs.inAppEnabled,
          emailEnabled:    prefs.emailEnabled,
          email:           prefs.email ?? '',
          telegramEnabled: prefs.telegramEnabled,
          telegramChatId:  prefs.telegramChatId ?? '',
        });
        this.notificationsLoading.set(false);
      },
      error: (err) => {
        this.notificationsError.set('Failed to load notification preferences.');
        this.notificationsLoading.set(false);
        console.error('[Settings] notification preferences load error', err);
      },
    });
  }

  // ── Save profile ──────────────────────────────────────────────────────────

  onSave(): void {
    if (this.form.invalid) return;
    this.saving.set(true);

    const req: UpdateProfileRequest = {
      displayName: this.form.value.displayName?.trim() || null,
      avatarUrl:   this.form.value.avatarUrl?.trim() || null,
      timezone:    this.form.value.timezone || null,
      currency:    this.form.value.currency || null,
    };

    this.api.updateProfile(req).subscribe({
      next: (updated) => {
        this.profile.set(updated);
        this.saving.set(false);
        this.snack.open('Profile saved.', 'OK', { duration: 3000 });
      },
      error: (err) => {
        this.saving.set(false);
        this.snack.open('Failed to save profile.', 'Close', { duration: 4000 });
        console.error('[Settings] save error', err);
      },
    });
  }

  onSaveNotifications(): void {
    if (this.notificationsForm.invalid) return;
    this.notificationsSaving.set(true);

    const req: UpdateNotificationPreferencesRequest = {
      inAppEnabled:    this.notificationsForm.value.inAppEnabled,
      emailEnabled:    this.notificationsForm.value.emailEnabled,
      email:           this.notificationsForm.value.email?.trim() || null,
      telegramEnabled: this.notificationsForm.value.telegramEnabled,
      telegramChatId:  this.notificationsForm.value.telegramChatId?.trim() || null,
    };

    this.api.updateNotificationPreferences(req).subscribe({
      next: (updated) => {
        this.notificationPrefs.set(updated);
        this.notificationsSaving.set(false);
        this.snack.open('Notification preferences saved.', 'OK', { duration: 3000 });
      },
      error: (err) => {
        this.notificationsSaving.set(false);
        this.snack.open('Failed to save notification preferences.', 'Close', { duration: 4000 });
        console.error('[Settings] notification preferences save error', err);
      },
    });
  }

  // ── Broker connections ────────────────────────────────────────────────────

  /** Load the list of connected brokers for the current user. */
  private loadBrokerConnections(): void {
    this.brokerApi.listConnections().subscribe({
      next: (connections) => {
        for (const conn of connections) {
          if (conn.brokerType in this.brokerConnected) {
            this.brokerConnected[conn.brokerType] = conn.connected;
          }
        }
        this.brokerStatusLoaded.set(true);
        this.brokerBackendStubbed.set(false);
      },
      error: (err: HttpErrorResponse) => {
        // 501 = backend is stubbed — treat as "no connections known yet"
        // and show an informational banner rather than an error.
        if (err.status === 501) {
          this.brokerBackendStubbed.set(true);
        }
        this.brokerStatusLoaded.set(true);
        console.warn('[Settings] broker list unavailable (backend stubbed or error)', err.status);
      },
    });
  }

  /**
   * Submit API credentials for a broker.
   * The apiSecret value is read from the form and immediately discarded after
   * the HTTP request is constructed — it is NEVER logged or stored.
   */
  onBrokerConnect(broker: BrokerCard): void {
    const form = this.brokerForms[broker.type];
    if (!form || form.invalid) return;

    this.brokerSubmitting[broker.type] = true;

    const apiKey    = form.value.apiKey as string;
    const apiSecret = form.value.apiSecret as string;   // treated as secret — not logged
    const label     = form.value.label as string | undefined;

    this.brokerApi.connectBroker(broker.type, apiKey, apiSecret, label).subscribe({
      next: (res) => {
        this.brokerSubmitting[broker.type] = false;
        if (res.status === 'NOT_IMPLEMENTED') {
          this.brokerBackendStubbed.set(true);
          this.snack.open(
            'Broker linking is not yet live — credentials were submitted but not stored. Check back soon.',
            'OK',
            { duration: 6000 }
          );
        } else {
          this.brokerConnected[broker.type] = true;
          form.reset();
          this.snack.open(`${broker.label} connected successfully.`, 'OK', { duration: 3500 });
        }
      },
      error: (err: HttpErrorResponse) => {
        this.brokerSubmitting[broker.type] = false;
        if (err.status === 501) {
          this.brokerBackendStubbed.set(true);
          this.snack.open(
            'Broker linking is coming soon — the backend is not yet implemented.',
            'Close',
            { duration: 6000 }
          );
        } else {
          this.snack.open(
            `Failed to connect ${broker.label}: ${err.error?.error ?? err.message}`,
            'Close',
            { duration: 5000 }
          );
        }
      },
    });
  }

  /** Revoke a linked broker connection. */
  onBrokerDisconnect(broker: BrokerCard): void {
    this.brokerSubmitting[broker.type] = true;
    this.brokerApi.revokeConnection(broker.type).subscribe({
      next: (res) => {
        this.brokerSubmitting[broker.type] = false;
        if (res.status === 'NOT_IMPLEMENTED') {
          this.brokerBackendStubbed.set(true);
          this.snack.open('Disconnect is not yet implemented on the backend.', 'OK', { duration: 5000 });
        } else {
          this.brokerConnected[broker.type] = false;
          this.snack.open(`${broker.label} disconnected.`, 'OK', { duration: 3000 });
        }
      },
      error: (err: HttpErrorResponse) => {
        this.brokerSubmitting[broker.type] = false;
        if (err.status === 501) {
          this.brokerBackendStubbed.set(true);
          this.snack.open('Disconnect backend not yet implemented.', 'Close', { duration: 5000 });
        } else {
          this.snack.open(`Failed to disconnect ${broker.label}.`, 'Close', { duration: 4000 });
        }
      },
    });
  }
}
