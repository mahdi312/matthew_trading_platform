import { Injectable, signal } from '@angular/core';

/**
 * Minimal JWT session accessor.
 *
 * The real login UI (Google + password, per Step 3's `identity-service`)
 * is built in Step 13/14 of the migration guide — it is out of scope for
 * Step 4.75's notification bell. This service only knows how to read a
 * JWT the login flow is expected to persist under {@link TOKEN_STORAGE_KEY}
 * in `localStorage`, and to decode its `userId` claim (the same claim
 * `identity-service`'s `JwtUtil` embeds — see
 * services/identity-service/.../security/JwtUtil.java) so the
 * notification bell knows which `/topic/alerts/{userId}` channel to
 * subscribe to.
 *
 * This deliberately does NOT validate the token's signature or expiry —
 * that is the Gateway's job (`GatewayJwtAuthFilter`). If the token is
 * missing, malformed, or rejected by the Gateway on CONNECT, the
 * notification bell simply stays disconnected; it never blocks the rest
 * of the app.
 */
@Injectable({ providedIn: 'root' })
export class AuthSessionService {
  /** localStorage key the (future) login flow is expected to write the JWT under. */
  static readonly TOKEN_STORAGE_KEY = 'mtp_auth_token';

  /** Reactive current token, so consumers can react to login/logout without polling. */
  readonly token = signal<string | null>(this.readToken());

  /** Reads the raw JWT (without "Bearer " prefix), or null if absent. */
  getToken(): string | null {
    return this.token();
  }

  /** Decodes the `userId` claim from the current JWT, or null if unavailable/unparseable. */
  getCurrentUserId(): string | null {
    const token = this.token();
    if (!token) return null;
    const claims = this.decodeClaims(token);
    const userId = claims?.['userId'];
    return userId === undefined || userId === null ? null : String(userId);
  }

  /** Call after the (future) login flow stores a new token, to refresh reactive consumers. */
  refresh(): void {
    this.token.set(this.readToken());
  }

  /**
   * Decodes role claim(s) from the current JWT.
   *
   * identity-service {@code JwtUtil} embeds a single {@code role} claim
   * (e.g. {@code "ADMIN"}). Authorities at runtime are {@code ROLE_<role>}.
   * Older tokens may use a {@code roles} array — both shapes are supported.
   */
  getRoles(): string[] {
    const token = this.token();
    if (!token) return [];
    const claims = this.decodeClaims(token);
    if (!claims) return [];

    const normalized = new Set<string>();
    const add = (value: unknown) => {
      if (value === undefined || value === null) return;
      const s = String(value).trim();
      if (!s) return;
      normalized.add(s);
      if (!s.startsWith('ROLE_')) {
        normalized.add(`ROLE_${s}`);
      }
    };

    add(claims['role']);
    const raw = claims['roles'];
    if (Array.isArray(raw)) {
      raw.forEach(add);
    } else if (typeof raw === 'string' && raw.length > 0) {
      raw.split(',').forEach(add);
    }
    return [...normalized];
  }

  /** Returns true if the current JWT contains the given role string (case-sensitive). */
  hasRole(role: string): boolean {
    return this.getRoles().includes(role);
  }

  private readToken(): string | null {
    try {
      return localStorage.getItem(AuthSessionService.TOKEN_STORAGE_KEY);
    } catch {
      // localStorage can throw in some sandboxed/private-browsing contexts.
      return null;
    }
  }

  /** Decodes a JWT's payload segment without verifying its signature. */
  private decodeClaims(token: string): Record<string, unknown> | null {
    try {
      const payload = token.split('.')[1];
      if (!payload) return null;
      const normalized = payload.replace(/-/g, '+').replace(/_/g, '/');
      const json = decodeURIComponent(
        atob(normalized)
          .split('')
          .map((c) => '%' + c.charCodeAt(0).toString(16).padStart(2, '0'))
          .join('')
      );
      return JSON.parse(json);
    } catch {
      return null;
    }
  }
}
