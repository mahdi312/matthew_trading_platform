import {Component, inject} from '@angular/core';
import {RouterOutlet} from '@angular/router';
import {ThemeService} from './core/theme/theme.service';

/**
 * Root component — always hosts a single {@link RouterOutlet}.
 *
 * Auth pages (`/login`, `/register`, `/auth/callback`) and embed routes load
 * as top-level routes. Protected pages nest under {@code AppShellComponent}
 * (see {@code app.routes.ts}) so the shell's own outlet stays stable.
 */
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  template: `<router-outlet/>`,
  styles: [`:host {
    display: block;
    height: 100%;
  }`],
})
export class AppComponent {
  // Eagerly inject ThemeService so the theme effect runs on startup.
  private readonly _theme = inject(ThemeService);
}
