import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { adminGuard } from './core/auth/admin.guard';

/**
 * Root route table.
 *
 * Auth routes (`/login`, `/register`) are eager-loaded — they must be
 * accessible without a JWT and are tiny enough that lazy-loading adds no
 * benefit.
 *
 * All protected routes use `canActivate: [authGuard]` and are lazy-loaded
 * (feature modules will be added here in Steps 4–11).
 */
export const routes: Routes = [
  // Default redirect — after login, land on the dashboard.
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },

  // Public routes (no authGuard).
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

  // Protected routes (authGuard applied).

  // Step 4: DashboardModule — landing screen after login.
  {
    path: 'dashboard',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/dashboard/dashboard-page.component').then(
        (m) => m.DashboardPageComponent
      ),
  },

  // Step 5: LiveTradingModule
  {
    path: 'live-trading',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/live-trading/live-trading-page.component').then(
        (m) => m.LiveTradingPageComponent
      ),
  },

  // Step 5: JournalModule
  {
    path: 'journal',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/journal/journal-page.component').then(
        (m) => m.JournalPageComponent
      ),
  },

  // Step 6: ChartingModule — full-screen charting workspace.
  {
    path: 'charting',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/charting/charting-page.component').then(
        (m) => m.ChartingPageComponent
      ),
  },

  // Step 7: AlertsModule — price alert CRUD management.
  {
    path: 'alerts',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/alerts/alerts-page.component').then(
        (m) => m.AlertsPageComponent
      ),
  },

  // Step 8: AiInsightsModule — AI market summary, signals, journal critique.
  {
    path: 'ai-insights',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/ai-insights/ai-insights-page.component').then(
        (m) => m.AiInsightsPageComponent
      ),
  },

  // Step 9: OnChainModule — NFT collections + DeFi pools views.
  {
    path: 'on-chain',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/on-chain/on-chain-page.component').then(
        (m) => m.OnChainPageComponent
      ),
  },

  // Step 10: ReportsModule — yearly P&L report + Excel export.
  {
    path: 'reports',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/reports/reports-page.component').then(
        (m) => m.ReportsPageComponent
      ),
  },

  // Step 11: SettingsModule — user profile + app preferences.
  {
    path: 'settings',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/settings/settings-page.component').then(
        (m) => m.SettingsPageComponent
      ),
  },

  // Step 11: AdminModule — user list + role management (ROLE_ADMIN only).
  // Uses both authGuard (token required) and adminGuard (ROLE_ADMIN claim required).
  // The route itself is inaccessible to non-admins — not just visually hidden.
  {
    path: 'admin',
    canActivate: [authGuard, adminGuard],
    loadComponent: () =>
      import('./features/admin/admin-page.component').then(
        (m) => m.AdminPageComponent
      ),
  },

  // Catch-all fallback — send unknown paths to login for now.
  { path: '**', redirectTo: 'login' },
];
