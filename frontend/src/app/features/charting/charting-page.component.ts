import {
  Component,
  OnInit,
  OnDestroy,
  inject,
  signal,
  computed,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatDividerModule } from '@angular/material/divider';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { Subscription } from 'rxjs';

import { CandlestickChartComponent } from '../../shared/chart-library/candlestick-chart/candlestick-chart.component';
import { SymbolSearchComponent, SymbolSearchResult } from '../../shared/symbol-search';
import { DrawingChangedEvent, ChartDrawing } from '../../shared/chart-library/models/drawing.model';
import { IndicatorSeries } from '../../shared/chart-library/models/indicator-series.model';
import { OhlcvBar } from '../../shared/chart-library/models/ohlcv.model';

import { ChartingApiService } from './charting-api.service';
import { MarketDataSocketService } from '../../core/api/market-data-socket.service';
import {
  ApiChartDrawing,
  DrawingLayout,
  IndicatorConfig,
  IndicatorResult,
  SaveDrawingRequest,
} from './charting.models';

/**
 * Full-screen charting workspace.
 *
 * Features:
 *  1. Symbol selector — switches the active chart, subscriptions, and persisted state.
 *  2. CandlestickChartComponent — receives OHLCV from live WebSocket feed,
 *     persisted drawings from market-service, and computed indicator series.
 *  3. Drawing persistence — on `(drawingChanged)`, creates/updates/deletes drawings
 *     via ChartingApiService (/api/charts/drawings).
 *  4. Layout management — save/load named layouts; updates GlobalDrawingSettings.
 *  5. Indicator panel — lists enabled indicator configs; toggle, add, remove.
 *     Triggers /api/indicators/{symbol}/compute to refresh computed series.
 *
 * Route: /charting  (behind authGuard)
 */
@Component({
  selector: 'app-charting-page',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatSidenavModule,
    MatListModule,
    MatDividerModule,
    MatChipsModule,
    MatTooltipModule,
    MatSlideToggleModule,
    CandlestickChartComponent,
    SymbolSearchComponent,
  ],
  templateUrl: './charting-page.component.html',
  styleUrls: ['./charting-page.component.scss'],
})
export class ChartingPageComponent implements OnInit, OnDestroy {
  private readonly chartingApi  = inject(ChartingApiService);
  private readonly marketSocket = inject(MarketDataSocketService);
  private readonly snack        = inject(MatSnackBar);
  private readonly route        = inject(ActivatedRoute);

  // ── State ─────────────────────────────────────────────────────────────────

  readonly activeSymbol     = signal<string>('BTCUSDT');
  readonly activeLayoutId   = signal<string | null>(null);
  readonly chartTheme       = signal<'dark' | 'light'>('dark');
  readonly sidenavOpen      = signal(true);

  /** Live OHLCV bars from the market WebSocket. */
  readonly chartBars        = signal<OhlcvBar[]>([]);
  readonly livePrice        = signal<number | null>(null);

  /** Drawings loaded from /api/charts/drawings, then kept in sync locally. */
  readonly drawings         = signal<ChartDrawing[]>([]);

  /** Indicator series computed by market-service. */
  readonly indicatorSeries  = signal<IndicatorSeries[]>([]);

  /** Saved layouts for the active symbol. */
  readonly layouts          = signal<DrawingLayout[]>([]);

  /** Indicator configs for the active symbol. */
  readonly indicatorConfigs = signal<IndicatorConfig[]>([]);

  // ── Loading flags ─────────────────────────────────────────────────────────

  readonly loadingDrawings    = signal(false);
  readonly loadingIndicators  = signal(false);
  readonly savingLayout       = signal(false);

  // ── Available indicator types ─────────────────────────────────────────────

  readonly indicatorTypes = [
    { type: 'SMA',        label: 'SMA',             subPane: false },
    { type: 'EMA',        label: 'EMA',             subPane: false },
    { type: 'BOLLINGER',  label: 'Bollinger Bands', subPane: false },
    { type: 'ICHIMOKU',   label: 'Ichimoku Cloud',  subPane: false },
    { type: 'RSI',        label: 'RSI',             subPane: true  },
    { type: 'MACD',       label: 'MACD',            subPane: true  },
  ];

  /** Color palette for new indicators. */
  private readonly colorPalette = [
    '#42a5f5', '#66bb6a', '#ffa726', '#ef5350',
    '#ab47bc', '#26c6da', '#d4e157', '#ff7043',
  ];
  private colorIndex = 0;

  private tickSub: Subscription | null = null;
  private currentSymbol = '';

  // ── Popular symbols ───────────────────────────────────────────────────────

  readonly popularSymbols = [
    'BTCUSDT', 'ETHUSDT', 'BNBUSDT', 'SOLUSDT', 'XRPUSDT',
  ];

  // ── Lifecycle ─────────────────────────────────────────────────────────────

  ngOnInit(): void {
    // Support ?symbol=XYZ query param (e.g. from watchlist row clicks)
    const qpSym = this.route.snapshot.queryParamMap.get('symbol');
    const initial = qpSym ? qpSym.toUpperCase().trim() : this.activeSymbol();
    this.activateSymbol(initial);
    this.loadGlobalSettings();
  }

  ngOnDestroy(): void {
    this.tickSub?.unsubscribe();
    if (this.currentSymbol) {
      this.marketSocket.unsubscribeSymbol(this.currentSymbol);
    }
  }

  // ── Symbol selection ──────────────────────────────────────────────────────

  onSymbolChange(symbol: string): void {
    symbol = symbol.toUpperCase().trim();
    if (!symbol || symbol === this.currentSymbol) return;
    this.activateSymbol(symbol);
  }

  /** Called by SymbolSearchComponent's (symbolSelected) event. */
  onSymbolSelected(result: SymbolSearchResult): void {
    this.onSymbolChange(result.ticker);
  }

  private activateSymbol(symbol: string): void {
    // Unsubscribe previous symbol.
    if (this.currentSymbol) {
      this.tickSub?.unsubscribe();
      this.marketSocket.unsubscribeSymbol(this.currentSymbol);
    }

    this.activeSymbol.set(symbol);
    this.currentSymbol = symbol;
    this.chartBars.set([]);
    this.livePrice.set(null);
    this.drawings.set([]);
    this.indicatorSeries.set([]);
    this.activeLayoutId.set(null);

    // Subscribe to live feed.
    this.marketSocket.subscribeSymbol(symbol);
    this.tickSub = this.marketSocket.ticks$(symbol).subscribe((tick) => {
      this.livePrice.set(tick.price);
      this.chartBars.update((bars) => {
        const last = bars[bars.length - 1];
        if (last && last.timestamp === tick.timestamp) {
          const updated = bars.slice();
          updated[updated.length - 1] = {
            ...last,
            close:  tick.price,
            high:   Math.max(last.high, tick.price),
            low:    Math.min(last.low,  tick.price),
            volume: last.volume + tick.volume,
          };
          return updated;
        }
        return [
          ...bars.slice(-499),
          { timestamp: tick.timestamp, open: tick.price, high: tick.price, low: tick.price, close: tick.price, volume: tick.volume },
        ];
      });
    });

    // Load server-side state for this symbol.
    this.loadDrawings();
    this.loadLayouts();
    this.loadIndicatorConfigs();
  }

  // ── Drawing persistence ───────────────────────────────────────────────────

  private loadDrawings(): void {
    this.loadingDrawings.set(true);
    this.chartingApi.getDrawings(this.activeSymbol(), this.activeLayoutId() ?? undefined).subscribe({
      next: (apiDrawings) => {
        this.drawings.set(apiDrawings.map(this.apiDrawingToPresentation));
        this.loadingDrawings.set(false);
      },
      error: () => this.loadingDrawings.set(false),
    });
  }

  /**
   * Called when CandlestickChartComponent emits `(drawingChanged)`.
   * Maps the presentation-layer event to an API save/delete call.
   */
  onDrawingChanged(event: DrawingChangedEvent): void {
    if (event.action === 'created') {
      const req: SaveDrawingRequest = {
        symbol:    this.activeSymbol(),
        layoutId:  this.activeLayoutId(),
        type:      event.drawing.type,
        points:    event.drawing.points,
        color:     event.drawing.color,
        lineWidth: event.drawing.lineWidth,
        label:     event.drawing.label,
        fibLevels: event.drawing.fibLevels,
      };
      this.chartingApi.createDrawing(req).subscribe({
        next: (saved) => {
          // Replace the locally-generated id with the server-assigned one.
          this.drawings.update((list) =>
            list.map((d) => (d.id === event.drawing.id ? { ...d, id: saved.id } : d))
          );
        },
        error: () => this.snack.open('Could not save drawing.', 'Close', { duration: 3000 }),
      });
    } else if (event.action === 'deleted' && event.drawing.id) {
      this.chartingApi.deleteDrawing(event.drawing.id).subscribe({
        error: () => this.snack.open('Could not delete drawing.', 'Close', { duration: 3000 }),
      });
    }
  }

  // ── Layout management ─────────────────────────────────────────────────────

  private loadLayouts(): void {
    this.chartingApi.getLayouts(this.activeSymbol()).subscribe({
      next: (layouts) => this.layouts.set(layouts),
      error: () => {}, // non-critical
    });
  }

  switchLayout(layoutId: string): void {
    this.activeLayoutId.set(layoutId);
    this.loadDrawings();
  }

  saveNewLayout(): void {
    const name = prompt('Layout name:')?.trim();
    if (!name) return;

    this.savingLayout.set(true);
    this.chartingApi.createLayout({ name, symbol: this.activeSymbol(), isGlobal: false }).subscribe({
      next: (layout) => {
        this.layouts.update((l) => [...l, layout]);
        this.activeLayoutId.set(layout.id);
        this.snack.open(`Layout "${layout.name}" saved.`, 'OK', { duration: 3000 });
        this.savingLayout.set(false);
        // Persist current drawings into the new layout.
        this.persistDrawingsToLayout(layout.id);
      },
      error: () => {
        this.snack.open('Failed to save layout.', 'Close', { duration: 4000 });
        this.savingLayout.set(false);
      },
    });
  }

  setDefaultLayout(): void {
    const id = this.activeLayoutId();
    if (!id) return;
    this.chartingApi.updateGlobalSettings({ defaultLayoutId: id }).subscribe({
      next: () => this.snack.open('Default layout updated.', 'OK', { duration: 2500 }),
      error: () => this.snack.open('Could not update default layout.', 'Close', { duration: 3000 }),
    });
  }

  private loadGlobalSettings(): void {
    this.chartingApi.getGlobalSettings().subscribe({
      next: (settings) => {
        if (settings.theme) this.chartTheme.set(settings.theme);
        if (settings.defaultLayoutId) {
          this.activeLayoutId.set(settings.defaultLayoutId);
        }
      },
      error: () => {},
    });
  }

  /** Re-saves all currently displayed drawings under a new layout id. */
  private persistDrawingsToLayout(layoutId: string): void {
    for (const drawing of this.drawings()) {
      const req: SaveDrawingRequest = {
        symbol:    this.activeSymbol(),
        layoutId,
        type:      drawing.type,
        points:    drawing.points,
        color:     drawing.color,
        lineWidth: drawing.lineWidth,
        label:     drawing.label,
        fibLevels: drawing.fibLevels,
      };
      this.chartingApi.createDrawing(req).subscribe();
    }
  }

  // ── Indicator management ──────────────────────────────────────────────────

  private loadIndicatorConfigs(): void {
    this.loadingIndicators.set(true);
    this.chartingApi.getIndicatorConfigs(this.activeSymbol()).subscribe({
      next: (configs) => {
        this.indicatorConfigs.set(configs);
        this.computeIndicators();
        this.loadingIndicators.set(false);
      },
      error: () => this.loadingIndicators.set(false),
    });
  }

  private computeIndicators(): void {
    this.chartingApi.computeIndicators(this.activeSymbol()).subscribe({
      next: (results) => {
        this.indicatorSeries.set(
          results.map((r) => ({
            name:    r.name,
            data:    r.data.filter((v): v is number => v !== null),
            color:   r.color,
            subPane: r.subPane,
          }))
        );
      },
      error: () => {}, // compute failure is non-critical; chart shows without overlays
    });
  }

  addIndicator(type: string, subPane: boolean): void {
    const next = this.colorPalette[this.colorIndex++ % this.colorPalette.length];
    const config: Omit<IndicatorConfig, 'id'> = {
      symbol:     this.activeSymbol(),
      type,
      parameters: this.defaultParams(type),
      color:      next,
      subPane,
      enabled:    true,
    };
    this.chartingApi.createIndicatorConfig(config).subscribe({
      next: (saved) => {
        this.indicatorConfigs.update((c) => [...c, saved]);
        this.computeIndicators();
        this.snack.open(`${type} indicator added.`, 'OK', { duration: 2000 });
      },
      error: () => this.snack.open('Failed to add indicator.', 'Close', { duration: 3000 }),
    });
  }

  toggleIndicator(config: IndicatorConfig): void {
    this.chartingApi.updateIndicatorConfig(config.id, { enabled: !config.enabled }).subscribe({
      next: (updated) => {
        this.indicatorConfigs.update((list) =>
          list.map((c) => (c.id === updated.id ? updated : c))
        );
        this.computeIndicators();
      },
      error: () => this.snack.open('Failed to toggle indicator.', 'Close', { duration: 3000 }),
    });
  }

  removeIndicator(config: IndicatorConfig): void {
    this.chartingApi.deleteIndicatorConfig(config.id).subscribe({
      next: () => {
        this.indicatorConfigs.update((list) => list.filter((c) => c.id !== config.id));
        this.computeIndicators();
        this.snack.open(`${config.type} removed.`, 'OK', { duration: 2000 });
      },
      error: () => this.snack.open('Failed to remove indicator.', 'Close', { duration: 3000 }),
    });
  }

  private defaultParams(type: string): Record<string, number> {
    switch (type) {
      case 'SMA':       return { period: 20 };
      case 'EMA':       return { period: 20 };
      case 'RSI':       return { period: 14 };
      case 'MACD':      return { fast: 12, slow: 26, signal: 9 };
      case 'BOLLINGER': return { period: 20, stdDev: 2 };
      case 'ICHIMOKU':  return { conversion: 9, base: 26, lagging: 52, displacement: 26 };
      default:          return {};
    }
  }

  // ── Mapping helpers ───────────────────────────────────────────────────────

  private apiDrawingToPresentation(d: ApiChartDrawing): ChartDrawing {
    return {
      id:        d.id,
      type:      d.type as ChartDrawing['type'],
      points:    d.points,
      color:     d.color,
      lineWidth: d.lineWidth,
      label:     d.label,
      fibLevels: d.fibLevels,
    };
  }

  // ── UI helpers ────────────────────────────────────────────────────────────

  toggleSidenav(): void {
    this.sidenavOpen.update((v) => !v);
  }

  toggleTheme(): void {
    const next = this.chartTheme() === 'dark' ? 'light' : 'dark';
    this.chartTheme.set(next);
    this.chartingApi.updateGlobalSettings({ theme: next }).subscribe();
  }

  layoutLabel(id: string): string {
    return this.layouts().find((l) => l.id === id)?.name ?? id;
  }

  trackById(_: number, item: { id: string }): string { return item.id; }
}
