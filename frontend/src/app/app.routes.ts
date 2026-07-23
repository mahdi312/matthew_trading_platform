import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { adminGuard } from './core/auth/admin.guard';
import { AppShellComponent } from './core/layout/app-shell.component';

/**
 * Root route table.
 *
 * Public auth/embed routes render without the nav shell.
 * Protected feature pages are children of {@link AppShellComponent} so a
 * single stable {@code router-outlet} always exists (fixes blank screen when
 * the shell previously swapped away the root outlet).
 */
export const routes: Routes = [
  // Public routes (no shell, no authGuard).
  {
    path: 'login',
    loadComponent: () =>
      import('./features/auth/login-page/login-page.component').then(
        (m) => m.LoginPageComponent
      ),
  },
  {
    path: 'register',
    loadComponent: () =>
      import('./features/auth/register-page/register-page.component').then(
        (m) => m.RegisterPageComponent
      ),
  },
  {
    path: 'auth/callback',
    loadComponent: () =>
      import('./features/auth/oauth-callback-page/oauth-callback-page.component').then(
        (m) => m.OauthCallbackPageComponent
      ),
  },

  // Detachable chart embed — authenticated via embed API key, not JWT.
  {
    path: 'embed/chart',
    loadComponent: () =>
      import('./features/embed/chart-embed-page.component').then(
        (m) => m.ChartEmbedPageComponent
      ),
  },

  // Authenticated app shell + feature pages.
  {
    path: '',
    component: AppShellComponent,
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },

      {
        path: 'dashboard',
        loadComponent: () =>
          import('./features/dashboard/dashboard-page.component').then(
            (m) => m.DashboardPageComponent
          ),
      },
      {
        path: 'live-trading',
        loadComponent: () =>
          import('./features/live-trading/live-trading-page.component').then(
            (m) => m.LiveTradingPageComponent
          ),
      },
      {
        path: 'journal',
        loadComponent: () =>
          import('./features/journal/journal-page.component').then(
            (m) => m.JournalPageComponent
          ),
      },
      {
        path: 'charting',
        loadComponent: () =>
          import('./features/charting/charting-page.component').then(
            (m) => m.ChartingPageComponent
          ),
      },
      {
        path: 'alerts',
        loadComponent: () =>
          import('./features/alerts/alerts-page.component').then(
            (m) => m.AlertsPageComponent
          ),
      },
      {
        path: 'ai-insights',
        loadComponent: () =>
          import('./features/ai-insights/ai-insights-page.component').then(
            (m) => m.AiInsightsPageComponent
          ),
      },
      {
        path: 'on-chain',
        loadComponent: () =>
          import('./features/on-chain/on-chain-page.component').then(
            (m) => m.OnChainPageComponent
          ),
      },
      {
        path: 'reports',
        loadComponent: () =>
          import('./features/reports/reports-page.component').then(
            (m) => m.ReportsPageComponent
          ),
      },
      {
        path: 'settings',
        loadComponent: () =>
          import('./features/settings/settings-page.component').then(
            (m) => m.SettingsPageComponent
          ),
      },
      {
        path: 'admin',
        canActivate: [adminGuard],
        loadComponent: () =>
          import('./features/admin/admin-page.component').then(
            (m) => m.AdminPageComponent
          ),
      },
    ],
  },

  { path: '**', redirectTo: 'login' },
];
