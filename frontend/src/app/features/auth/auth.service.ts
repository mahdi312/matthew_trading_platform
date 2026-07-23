import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { AUTH_API } from '../../core/api/api-paths';

/** Matches identity-service LoginRequest (username + password). */
export interface LoginRequest {
  username: string;
  password: string;
}

/** Matches identity-service RegisterRequest. */
export interface RegisterRequest {
  username: string;
  password: string;
  displayName: string;
  email?: string;
}

export interface AuthResponse {
  token: string;
  expiresIn?: number;
  userId?: number;
  username?: string;
  role?: string;
}

/**
 * Thin HTTP wrapper around identity-service AuthController via the Gateway.
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
   * Starts Google OAuth2 via Spring Security (Gateway → identity-service).
   */
  initiateGoogleOAuth(): void {
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
      // localStorage unavailable
    }
    this.authSession.refresh();
  }
}
