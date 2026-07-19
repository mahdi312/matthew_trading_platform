import {Component, computed, inject, signal} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
import {NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet} from '@angular/router';
import {CommonModule} from '@angular/common';
import {MatIconModule} from '@angular/material/icon';
import {MatButtonModule} from '@angular/material/button';
import {MatTooltipModule} from '@angular/material/tooltip';
import {filter, map, startWith} from 'rxjs';
import {ThemeService} from '../theme/theme.service';
import {AuthSessionService} from '../auth/auth-session.service';
import {AuthService} from '../../features/auth/auth.service';
import {NotificationBellComponent} from '../../shared/notification-bell/notification-bell.component';
import {NotificationService} from '../notification/notification.service';
import {ToolsPanelService, ToolsPanelTab} from '../tools/tools-panel.service';
import {WatchlistWidgetComponent} from '../../features/dashboard/watchlist-widget/watchlist-widget.component';

interface NavItem {
  path: string;
  icon: string;
  label: string;
  adminOnly?: boolean;
}

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    RouterLinkActive,
    RouterOutlet,
    MatIconModule,
    MatButtonModule,
    MatTooltipModule,
    NotificationBellComponent,
    WatchlistWidgetComponent,
  ],
  templateUrl: './app-shell.component.html',
  styleUrl: './app-shell.component.scss',
})
export class AppShellComponent {
  readonly sidebarExpanded = signal(false);
  readonly navItems: NavItem[] = [
    {path: '/dashboard', icon: 'dashboard', label: 'Dashboard'},
    {path: '/live-trading', icon: 'candlestick_chart', label: 'Live Trading'},
    {path: '/journal', icon: 'book', label: 'Journal'},
    {path: '/charting', icon: 'show_chart', label: 'Charting'},
    {path: '/alerts', icon: 'notifications', label: 'Alerts'},
    {path: '/ai-insights', icon: 'psychology', label: 'AI Insights'},
    {path: '/on-chain', icon: 'link', label: 'On-Chain'},
    {path: '/reports', icon: 'bar_chart', label: 'Reports'},
    {path: '/settings', icon: 'settings', label: 'Settings'},
    {path: '/admin', icon: 'admin_panel_settings', label: 'Admin', adminOnly: true},
  ];

  private readonly themeService = inject(ThemeService);
  readonly activeTheme = this.themeService.activeTheme;
  private readonly authSession = inject(AuthSessionService);
  private readonly authService = inject(AuthService);
  private readonly notification = inject(NotificationService);
  private readonly router = inject(Router);
  readonly tools = inject(ToolsPanelService);

  readonly isAdmin = computed(() => this.authSession.hasRole('ROLE_ADMIN'));

  /** Full-bleed layouts (chart terminal) drop page padding. */
  readonly flushContent = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map(e => e.urlAfterRedirects.startsWith('/charting')),
      startWith(this.router.url.startsWith('/charting')),
    ),
    {initialValue: this.router.url.startsWith('/charting')},
  );

  get visibleNavItems(): NavItem[] {
    return this.navItems.filter(item => !item.adminOnly || this.isAdmin());
  }

  toggleSidebar(): void {
    this.sidebarExpanded.update(v => !v);
  }

  toggleTheme(): void {
    this.themeService.toggle();
  }

  toggleTools(): void {
    this.tools.toggle();
  }

  selectTool(tab: ToolsPanelTab): void {
    this.tools.setTab(tab);
  }

  logout(): void {
    try {
      this.notification.disconnect();
    } catch {
      // optional disconnect
    }
    this.authService.logout();
    void this.router.navigate(['/login']);
  }
}
