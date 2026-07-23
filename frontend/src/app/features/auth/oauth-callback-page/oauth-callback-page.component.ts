import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { AuthSessionService } from '../../../core/auth/auth-session.service';
import { NotificationService } from '../../../core/notification/notification.service';

/**
 * Receives Google OAuth2 redirect: {@code /auth/callback?token=...}.
 * Persists the JWT then navigates to the dashboard.
 */
@Component({
  selector: 'app-oauth-callback-page',
  standalone: true,
  imports: [CommonModule, RouterLink, MatIconModule],
  template: `
    <div class="callback-page">
      <div class="callback-backdrop" aria-hidden="true"></div>

      <div class="callback-panel">
        <span class="callback-brand" aria-label="Matthew Trading Platform">MTP</span>

        @if (error()) {
          <div class="callback-error" role="alert">
            <mat-icon aria-hidden="true">error_outline</mat-icon>
            <div class="callback-error__body">
              <strong>Sign-in failed</strong>
              <p>{{ error() }}</p>
              <a routerLink="/login" class="callback-back-link">Back to sign in</a>
            </div>
          </div>
        } @else {
          <div class="callback-loading" aria-live="polite" aria-busy="true">
            <div class="callback-spinner" aria-hidden="true"></div>
            <p class="callback-status">Signing you in…</p>
            <p class="callback-hint">Completing authentication with Google</p>
          </div>
        }
      </div>
    </div>
  `,
  styles: [`
    .callback-page {
      position: relative;
      min-height: 100vh;
      display: grid;
      place-items: center;
      background: var(--ink-950, #0A0D12);
      overflow: hidden;
    }

    .callback-backdrop {
      position: absolute;
      inset: 0;
      pointer-events: none;
      background:
        radial-gradient(ellipse 80% 60% at 50% 0%, color-mix(in srgb, #D4A24C 12%, transparent), transparent 55%),
        radial-gradient(ellipse 60% 50% at 50% 100%, color-mix(in srgb, #34D6C4 6%, transparent), transparent 50%);
    }

    .callback-panel {
      position: relative;
      z-index: 1;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 32px;
      padding: 48px 32px;
      background: var(--ink-800, #1B2029);
      border: 1px solid var(--hairline, #2A303C);
      border-radius: 16px;
      min-width: 320px;
      box-shadow: 0 24px 56px rgba(0, 0, 0, 0.55);
    }

    .callback-brand {
      font-family: 'Space Grotesk', sans-serif;
      font-size: 2.5rem;
      font-weight: 700;
      letter-spacing: 0.1em;
      color: var(--brass-500, #D4A24C);
    }

    .callback-loading {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 12px;
      text-align: center;
    }

    .callback-spinner {
      width: 40px;
      height: 40px;
      border: 3px solid rgba(212, 162, 76, 0.2);
      border-top-color: var(--brass-500, #D4A24C);
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
    }

    @keyframes spin {
      to { transform: rotate(360deg); }
    }

    .callback-status {
      font-family: 'Space Grotesk', sans-serif;
      font-size: 1.1rem;
      font-weight: 600;
      color: var(--paper-100, #EDEFF3);
      margin: 0;
    }

    .callback-hint {
      font-size: 12.5px;
      color: var(--paper-400, #8A93A6);
      margin: 0;
    }

    .callback-error {
      display: flex;
      align-items: flex-start;
      gap: 12px;
      padding: 16px;
      background: color-mix(in srgb, #F0555A 10%, var(--ink-800, #1B2029));
      border: 1px solid color-mix(in srgb, #F0555A 30%, transparent);
      border-radius: 10px;
      max-width: 360px;
    }

    .callback-error mat-icon {
      color: #F0555A;
      font-size: 22px;
      width: 22px;
      height: 22px;
      flex-shrink: 0;
      margin-top: 2px;
    }

    .callback-error__body {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    .callback-error__body strong {
      color: var(--paper-100, #EDEFF3);
      font-size: 14px;
    }

    .callback-error__body p {
      margin: 0;
      font-size: 13px;
      color: #F47378;
    }

    .callback-back-link {
      margin-top: 8px;
      display: inline-block;
      color: var(--brass-500, #D4A24C);
      font-size: 13px;
      font-weight: 600;
      text-decoration: none;

      &:hover { color: var(--brass-400, #E0B563); text-decoration: underline; }
      &:focus-visible { outline: 2px solid var(--brass-500, #D4A24C); outline-offset: 2px; border-radius: 2px; }
    }
  `],
})
export class OauthCallbackPageComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly authSession = inject(AuthSessionService);
  private readonly notification = inject(NotificationService);

  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token');
    if (!token) {
      this.error.set('No token received from Google sign-in. Please try again.');
      return;
    }
    try {
      localStorage.setItem(AuthSessionService.TOKEN_STORAGE_KEY, token);
    } catch {
      this.error.set('Could not store your session. Your browser may be blocking localStorage.');
      return;
    }
    this.authSession.refresh();
    this.notification.connect();
    void this.router.navigate(['/dashboard'], { replaceUrl: true });
  }
}
