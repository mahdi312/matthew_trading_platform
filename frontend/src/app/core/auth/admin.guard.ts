import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthSessionService } from './auth-session.service';

/**
 * Role-checking route guard — allows access only to users whose JWT contains
 * the `ROLE_ADMIN` claim.
 *
 * This guard runs AFTER the regular `authGuard` (or can be listed alongside
 * it in `canActivate`). It gates the route itself, not just the nav link —
 * so even a user who manually types `/admin` in the URL will be redirected
 * back to `/dashboard` if they lack the role.
 *
 * Uses `AuthSessionService.hasRole()` which reuses the same private JWT
 * decoder as `getCurrentUserId()` — no second decoder is introduced.
 *
 * Usage:
 * ```ts
 * { path: 'admin', canActivate: [authGuard, adminGuard], ... }
 * ```
 */
export const adminGuard: CanActivateFn = () => {
  const authSession = inject(AuthSessionService);
  const router      = inject(Router);

  // First ensure the user is authenticated at all.
  if (!authSession.token()) {
    return router.createUrlTree(['/login']);
  }

  // Then check for the ROLE_ADMIN claim.
  if (authSession.hasRole('ROLE_ADMIN')) {
    return true;
  }

  // Authenticated but not admin — redirect to dashboard, not login.
  return router.createUrlTree(['/dashboard']);
};
