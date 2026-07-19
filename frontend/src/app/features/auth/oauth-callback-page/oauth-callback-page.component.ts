import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthSessionService } from '../../../core/auth/auth-session.service';
import { NotificationService } from '../../../core/notification/notification.service';

/**
 * Receives Google OAuth2 redirect: {@code /auth/callback?token=...}.
 * Persists the JWT then navigates to the dashboard.
 */
@Component({
  selector: 'app-oauth-callback-page',
  standalone: true,
  imports: [CommonModule, RouterLink, MatProgressSpinnerModule],
  template: `
    <div class="wrap">
      @if (error()) {
        <p class="error">{{ error() }}</p>
        <a routerLink="/login">Back to sign in</a>
      } @else {
        <mat-spinner diameter="40"></mat-spinner>
        <p>Completing sign-in…</p>
      }
    </div>
  `,
  styles: [`
    .wrap {
      min-height: 100vh;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 16px;
      color: var(--tp-text-primary, #e6edf3);
      background: var(--tp-bg-primary, #0d1117);
    }
    .error { color: var(--tp-accent-red, #f85149); }
    a { color: var(--tp-accent-blue, #388bfd); }
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
      this.error.set('No token received from Google sign-in.');
      return;
    }
    try {
      localStorage.setItem(AuthSessionService.TOKEN_STORAGE_KEY, token);
    } catch {
      this.error.set('Could not store session token.');
      return;
    }
    this.authSession.refresh();
    this.notification.connect();
    void this.router.navigate(['/dashboard'], { replaceUrl: true });
  }
}
