import {Component, computed, inject, OnInit, signal,} from '@angular/core';
import {CommonModule} from '@angular/common';
import {ActivatedRoute} from '@angular/router';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {MatIconModule} from '@angular/material/icon';
import {catchError, of} from 'rxjs';

import {CandlestickChartComponent, type OhlcvBar,} from '../../shared/chart-library';
import {EmbedChartApiService} from './embed-chart-api.service';

/**
 * Detachable chart host for third-party iframes / apps.
 *
 * Redesigned per Prompt 14 to be truly chrome-free: no sidebar, no marquee,
 * no toolbar — just the candlestick chart filling the viewport, with a tiny
 * unobtrusive attribution mark tucked into a corner (small enough not to
 * compete with the chart, present enough to identify the platform).
 * The public `/embed/chart` route is intentionally outside the app shell
 * in `app.routes.ts` — this component owns 100% of its own layout.
 *
 * Query params:
 *   - key | embedKey  — secret embed API key (required)
 *   - symbol          — instrument (default BTCUSDT)
 *   - timeframe       — candle interval (default 1h)
 *   - theme           — dark | light (default dark)
 *   - limit           — bar count (default 200)
 *
 * Example iframe:
 * ```html
 * <iframe
 *   src="https://app.example.com/embed/chart?key=YOUR_SECRET&symbol=ETHUSDT&timeframe=1h"
 *   width="100%" height="480" frameborder="0"></iframe>
 * ```
 *
 * A missing/invalid embed key, or a symbol the key isn't permitted to read,
 * renders as a small, centered, plain-language error inside the frame
 * ("This chart link is no longer valid") — whoever embedded this has no
 * console access to debug it, so the message has to be self-explanatory
 * from the rendered output alone.
 */
@Component({
  selector: 'app-chart-embed-page',
  standalone: true,
  providers: [EmbedChartApiService],
  imports: [CommonModule, MatProgressSpinnerModule, MatIconModule, CandlestickChartComponent],
  templateUrl: './chart-embed-page.component.html',
  styleUrls: ['./chart-embed-page.component.scss'],
})
export class ChartEmbedPageComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(EmbedChartApiService);

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly bars = signal<OhlcvBar[]>([]);
  readonly symbol = signal('BTCUSDT');
  readonly theme = signal<'dark' | 'light'>('dark');
  readonly timeframe = signal('1h');

  readonly title = computed(
    () => `${this.symbol()} · ${this.timeframe()}`
  );

  ngOnInit(): void {
    const q = this.route.snapshot.queryParamMap;
    const key = q.get('key') ?? q.get('embedKey') ?? '';
    const symbolParam = (q.get('symbol') ?? '').trim();
    const symbol = (symbolParam || 'BTCUSDT').toUpperCase();
    const timeframe = q.get('timeframe') ?? '1h';
    const theme = (q.get('theme') === 'light' ? 'light' : 'dark') as 'dark' | 'light';
    const limit = Number(q.get('limit') ?? '200') || 200;

    this.symbol.set(symbol);
    this.timeframe.set(timeframe);
    this.theme.set(theme);

    // Missing embed key — self-explanatory, plain-language error; the
    // person who embedded this frame has no console access to debug it.
    if (!key) {
      this.error.set('This chart link is no longer valid.');
      this.loading.set(false);
      return;
    }

    this.api
      .getOhlcv(key, symbol, timeframe, limit)
      .pipe(
        catchError((err) => {
          // Invalid/expired key or a symbol this key isn't permitted to
          // read both surface as the same plain-language sentence — the
          // embedder can't distinguish (or fix) the technical cause anyway.
          const msg =
            err?.status === 401 || err?.status === 403 || err?.status === 404
              ? 'This chart link is no longer valid.'
              : 'Unable to load chart data right now.';
          this.error.set(msg);
          return of([] as OhlcvBar[]);
        })
      )
      .subscribe((data) => {
        const bars = this.normalizeBars(data);
        this.bars.set(bars);
        this.loading.set(false);
      });
  }

  /** Map gateway OHLCV JSON into the presentation-only chart model. */
  private normalizeBars(raw: unknown[]): OhlcvBar[] {
    if (!Array.isArray(raw)) return [];
    return raw.map((row: any) => ({
      timestamp: row.timestamp ?? row.openTime ?? row.open_time ?? row.t ?? '',
      open: Number(row.open ?? row.o ?? 0),
      high: Number(row.high ?? row.h ?? 0),
      low: Number(row.low ?? row.l ?? 0),
      close: Number(row.close ?? row.c ?? 0),
      volume: Number(row.volume ?? row.v ?? 0),
    }));
  }
}
