import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { AUTH_API } from '../../core/api/api-paths';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  username?: string;
}

export interface AuthResponse {
  token: string;
}

/**
 * Thin HTTP wrapper around identity-service's AuthController endpoints.
 *
 * On success, writes the returned JWT to `localStorage['mtp_auth_token']`
 * (the exact key `AuthSessionService.TOKEN_STORAGE_KEY` expects) and calls
 * `authSession.refresh()` so every signal consumer (notification bell, guards,
 * etc.) reacts immediately without a page reload.
 *
 * The notification-bell connect call is intentionally left to the call-site
 * (LoginPageComponent / RegisterPageComponent) so that the injected
 * `NotificationService` dependency stays out of this low-level service.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly authSession = inject(AuthSessionService);

  private readonly baseUrl = environment.gatewayBaseUrl;

  login(credentials: LoginRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.baseUrl}${AUTH_API}/login`, credentials)
      .pipe(tap((res) => this.persistToken(res.token)));
  }

  register(data: RegisterRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.baseUrl}${AUTH_API}/register`, data)
      .pipe(tap((res) => this.persistToken(res.token)));
  }

  /**
   * Initiates the Google OAuth2 flow.
   * Redirects the browser to the OAuth2 authorization endpoint exposed by
   * identity-service's Spring Security config (the starting point, NOT the
   * callback URL).  The backend redirects back with a code; identity-service
   * exchanges it and eventually issues a JWT the same way as /api/auth/login.
   */
  initiateGoogleOAuth(): void {
    // Spring Security registers the initiation URL at
    // /oauth2/authorization/google which the Gateway proxies as-is.
    window.location.href = `${this.baseUrl}/oauth2/authorization/google`;
  }

  logout(): void {
    try {
      localStorage.removeItem(AuthSessionService.TOKEN_STORAGE_KEY);
    } catch {
      // ignore
    }
    this.authSession.refresh();
  }

  private persistToken(token: string): void {
    try {
      localStorage.setItem(AuthSessionService.TOKEN_STORAGE_KEY, token);
    } catch {
      // localStorage unavailable — token stays in memory via signal only.
    }
    this.authSession.refresh();
  }
}
