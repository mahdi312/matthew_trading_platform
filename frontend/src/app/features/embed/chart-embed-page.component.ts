import {
  Component,
  OnInit,
  inject,
  signal,
  computed,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { catchError, of } from 'rxjs';

import {
  CandlestickChartComponent,
  type OhlcvBar,
} from '../../shared/chart-library';
import { EmbedChartApiService } from './embed-chart-api.service';

/**
 * Detachable chart host for third-party iframes / apps.
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
    const symbol = (q.get('symbol') ?? 'BTCUSDT').toUpperCase();
    const timeframe = q.get('timeframe') ?? '1h';
    const theme = (q.get('theme') === 'light' ? 'light' : 'dark') as 'dark' | 'light';
    const limit = Number(q.get('limit') ?? '200') || 200;

    this.symbol.set(symbol);
    this.timeframe.set(timeframe);
    this.theme.set(theme);

    if (!key) {
      this.error.set('Missing embed key. Pass ?key=YOUR_SECRET in the URL.');
      this.loading.set(false);
      return;
    }

    this.api
      .getOhlcv(key, symbol, timeframe, limit)
      .pipe(
        catchError((err) => {
          const msg =
            err?.status === 401
              ? 'Invalid embed key or path not permitted.'
              : err?.message ?? 'Failed to load chart data.';
          this.error.set(msg);
          return of([] as OhlcvBar[]);
        })
      )
      .subscribe((data) => {
        this.bars.set(this.normalizeBars(data));
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
