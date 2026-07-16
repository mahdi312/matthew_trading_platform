import {Component, computed, inject, signal} from '@angular/core';
import {RouterLink, RouterLinkActive, RouterOutlet} from '@angular/router';
import {CommonModule} from '@angular/common';
import {MatIconModule} from '@angular/material/icon';
import {MatButtonModule} from '@angular/material/button';
import {MatTooltipModule} from '@angular/material/tooltip';
import {ThemeService} from '../theme/theme.service';
import {AuthSessionService} from '../auth/auth-session.service';
import {NotificationBellComponent} from '../../shared/notification-bell/notification-bell.component';

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
  ],
  templateUrl: './app-shell.component.html',
  styleUrl: './app-shell.component.scss',
})
export class AppShellComponent {
  /** Sidebar collapsed/expanded state */
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
  /** Reactive theme value */
  readonly activeTheme = this.themeService.activeTheme;
  private readonly authSession = inject(AuthSessionService);
  /** True when logged-in user has ROLE_ADMIN */
  readonly isAdmin = computed(() => this.authSession.hasRole('ROLE_ADMIN'));

  /** Returns only items visible to the current user */
  get visibleNavItems(): NavItem[] {
    return this.navItems.filter(item => !item.adminOnly || this.isAdmin());
  }

  toggleSidebar(): void {
    this.sidebarExpanded.update(v => !v);
  }

  toggleTheme(): void {
    this.themeService.toggle();
  }
}
