import {effect, Injectable, signal} from '@angular/core';

export type AppTheme = 'dark' | 'light';

/**
 * ThemeService — dark/light toggle matching the desktop app's existing palette.
 *
 * Desktop palette source:
 *  - dark:  desktop/src/main/resources/css/dark-theme.css
 *  - light: desktop/src/main/resources/css/light-theme.css
 *
 * The theme is persisted to localStorage under 'mtp_theme' so it survives
 * page reloads. The active theme is applied as a CSS class on <html>:
 *   dark  → class="theme-dark"
 *   light → class="theme-light"
 */
@Injectable({providedIn: 'root'})
export class ThemeService {
  private static readonly STORAGE_KEY = 'mtp_theme';

  readonly activeTheme = signal<AppTheme>(this.readPersistedTheme());

  constructor() {
    // Apply the theme to <html> whenever it changes.
    effect(() => {
      const theme = this.activeTheme();
      const html = document.documentElement;
      html.classList.remove('theme-dark', 'theme-light');
      html.classList.add(`theme-${theme}`);
      try {
        localStorage.setItem(ThemeService.STORAGE_KEY, theme);
      } catch { /* ignore */
      }
    });
  }

  toggle(): void {
    this.activeTheme.set(this.activeTheme() === 'dark' ? 'light' : 'dark');
  }

  setTheme(theme: AppTheme): void {
    this.activeTheme.set(theme);
  }

  private readPersistedTheme(): AppTheme {
    try {
      const stored = localStorage.getItem(ThemeService.STORAGE_KEY);
      if (stored === 'light' || stored === 'dark') return stored;
    } catch { /* ignore */
    }
    // Default: dark (matches the trading terminal aesthetic).
    return 'dark';
  }
}
