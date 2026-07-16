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

import { SettingsApiService } from './settings-api.service';
import { UserProfile, UpdateProfileRequest } from './settings.models';

/**
 * SettingsModule — user profile + app settings editor.
 *
 * Loads the current user's profile from `GET /api/profile` on init,
 * pre-fills the form, and submits updates via `PUT /api/profile`.
 *
 * Editable fields: Display Name, Avatar URL, Timezone, Currency,
 *                  Notifications toggle.
 * Read-only fields: Username, Email, Account created date.
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
  ],
  templateUrl: './settings-page.component.html',
  styleUrls: ['./settings-page.component.scss'],
})
export class SettingsPageComponent implements OnInit {
  private readonly api   = inject(SettingsApiService);
  private readonly snack = inject(MatSnackBar);
  private readonly fb    = inject(FormBuilder);

  // ── State ─────────────────────────────────────────────────────────────────

  readonly loading = signal(true);
  readonly saving  = signal(false);
  readonly error   = signal<string | null>(null);
  readonly profile = signal<UserProfile | null>(null);

  // ── Form ──────────────────────────────────────────────────────────────────

  readonly form: FormGroup = this.fb.group({
    displayName:          [''],
    avatarUrl:            [''],
    timezone:             [''],
    currency:             ['USD'],
    notificationsEnabled: [true],
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

  // ── Lifecycle ─────────────────────────────────────────────────────────────

  ngOnInit(): void {
    this.api.getProfile().subscribe({
      next: (p) => {
        this.profile.set(p);
        this.form.patchValue({
          displayName:          p.displayName ?? '',
          avatarUrl:            p.avatarUrl ?? '',
          timezone:             p.timezone ?? 'UTC',
          currency:             p.currency ?? 'USD',
          notificationsEnabled: p.notificationsEnabled,
        });
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set('Failed to load profile.');
        this.loading.set(false);
        console.error('[Settings] load error', err);
      },
    });
  }

  // ── Save ──────────────────────────────────────────────────────────────────

  onSave(): void {
    if (this.form.invalid) return;
    this.saving.set(true);

    const req: UpdateProfileRequest = {
      displayName:          this.form.value.displayName?.trim() || null,
      avatarUrl:            this.form.value.avatarUrl?.trim() || null,
      timezone:             this.form.value.timezone || null,
      currency:             this.form.value.currency || null,
      notificationsEnabled: this.form.value.notificationsEnabled,
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
}
