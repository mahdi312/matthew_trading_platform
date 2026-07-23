import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthSessionService } from './auth-session.service';

/**
 * Functional route guard — protects every route except `/login` and `/register`.
 *
 * Reads `AuthSessionService.token()` (a `signal<string | null>`); if absent,
 * redirects to `/login`.  No async work needed — the token is already in
 * memory (read from localStorage at service construction time).
 *
 * Usage in route definitions:
 * ```ts
 * { path: 'dashboard', canActivate: [authGuard], ... }
 * ```
 */
export const authGuard: CanActivateFn = () => {
  const authSession = inject(AuthSessionService);
  const router = inject(Router);

  if (authSession.token()) {
    return true;
  }
  // Redirect unauthenticated visitors to the login page.
  return router.createUrlTree(['/login']);
};
