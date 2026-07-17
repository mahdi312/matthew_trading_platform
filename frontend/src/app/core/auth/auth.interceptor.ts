import { HttpInterceptorFn, HttpRequest, HttpHandlerFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthSessionService } from './auth-session.service';

/**
 * Functional HTTP interceptor — two responsibilities in one pass:
 *
 * 1. **Auth header attachment**: If the request URL starts with
 *    `environment.gatewayBaseUrl` AND a JWT is present in
 *    `AuthSessionService`, attaches `Authorization: Bearer {token}`.
 *    Requests to other origins (CDNs, third-party APIs) are never touched.
 *
 * 2. **401 handling**: If a Gateway-routed call comes back with HTTP 401,
 *    clears `localStorage['mtp_auth_token']` and redirects to `/login`.
 *    This keeps every protected route consistent — no feature module has to
 *    handle auth expiry on its own.
 *
 * Registered globally via `provideHttpClient(withInterceptors([authInterceptor]))`
 * in `app.config.ts` — follows the Angular 17 functional-interceptor pattern.
 */
export const authInterceptor: HttpInterceptorFn = (
  req: HttpRequest<unknown>,
  next: HttpHandlerFn
) => {
  const authSession = inject(AuthSessionService);
  const router = inject(Router);

  const isGatewayCall = req.url.startsWith(environment.gatewayBaseUrl);

  // Attach JWT only for calls going to the API Gateway.
  let outgoing = req;
  if (isGatewayCall) {
    const token = authSession.getToken();
    if (token) {
      outgoing = req.clone({
        setHeaders: { Authorization: `Bearer ${token}` },
      });
    }
  }

  return next(outgoing).pipe(
    catchError((err: unknown) => {
      const isEmbedCall = outgoing.headers.has('X-Embed-Key');
      // Embed widgets handle 401 in-page — do not clear session or bounce to login.
      if (
        isGatewayCall &&
        !isEmbedCall &&
        err instanceof HttpErrorResponse &&
        err.status === 401
      ) {
        try {
          localStorage.removeItem(AuthSessionService.TOKEN_STORAGE_KEY);
        } catch {
          // localStorage may be unavailable in some contexts — ignore.
        }
        authSession.refresh();
        router.navigate(['/login']);
      }
      return throwError(() => err);
    })
  );
};
