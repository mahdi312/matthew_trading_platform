import {Component, inject, OnInit} from '@angular/core';
import {NavigationEnd, Router, RouterOutlet} from '@angular/router';
import {CommonModule} from '@angular/common';
import {filter} from 'rxjs/operators';
import {AppShellComponent} from './core/layout/app-shell.component';
import {ThemeService} from './core/theme/theme.service';

/**
 * AppComponent — root component.
 *
 * Renders the persistent AppShellComponent (sidebar + toolbar + router-outlet)
 * for all protected routes. Auth routes (/login, /register) skip the shell
 * and render directly via RouterOutlet on their own full-screen pages.
 */
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, AppShellComponent],
  template: `
    @if (showShell) {
      <app-shell/>
    } @else {
      <router-outlet/>
    }
  `,
  styles: [`:host {
    display: block;
    height: 100%;
  }`],
})
export class AppComponent implements OnInit {
  /** True when the current route should show the persistent nav shell. */
  showShell = false;
  private readonly router = inject(Router);
  // Eagerly inject ThemeService so the theme effect runs on startup.
  private readonly _theme = inject(ThemeService);
  /** Routes that render full-screen (no nav shell). */
  private readonly PUBLIC_ROUTES = new Set(['/login', '/register']);

  ngOnInit(): void {
    this.router.events
      .pipe(filter(e => e instanceof NavigationEnd))
      .subscribe((e: NavigationEnd) => {
        const url = e.urlAfterRedirects.split('?')[0];
        this.showShell = !this.PUBLIC_ROUTES.has(url);
      });
  }
}
