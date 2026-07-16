import { ApplicationConfig } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimations } from '@angular/platform-browser/animations';

import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';

/**
 * Root application configuration.
 *
 * Additions over the Step 4.75 baseline (per Migration_Guide_frontend.md Step 1):
 * - `provideHttpClient(withInterceptors([authInterceptor]))` — enables
 *   HttpClient app-wide and registers the functional JWT + 401 interceptor.
 * - `provideAnimations()` — required by Angular Material components added
 *   to `package.json` in this same step.
 *
 * Nothing else changes here — feature modules register their own providers
 * locally (lazy-loaded routes, per Angular 17 standalone conventions).
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideAnimations(),
  ],
};
