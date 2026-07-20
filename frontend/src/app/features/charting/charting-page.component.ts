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
import { MatExpansionModule } from '@angular/material/expansion';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatMenuModule } from '@angular/material/menu';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { Subscription } from 'rxjs';

import { CandlestickChartComponent } from '../../shared/chart-library/candlestick-chart/candlestick-chart.component';
import { SymbolSearchComponent, SymbolSearchResult } from '../../shared/symbol-search';
import { DrawingChangedEvent, ChartDrawing, DrawingToolType } from '../../shared/chart-library/models/drawing.model';
import { IndicatorSeries } from '../../shared/chart-library/models/indicator-series.model';
import { OhlcvBar } from '../../shared/chart-library/models/ohlcv.model';
import { PriceFlashDirective } from '../../shared/directives/price-flash.directive';
import { LoadingStateComponent } from '../../shared/loading-state/loading-state.component';
import { EmptyStateComponent } from '../../shared/empty-state/empty-state.component';

import { ChartingApiService } from './charting-api.service';
import { MarketDataSocketService } from '../../core/api/market-data-socket.service';
import { WatchlistApiService } from '../dashboard/watchlist-widget/watchlist-api.service';
import {
  FundamentalsApiService,
  FundamentalsPayload,
  AssetClass,
  isStockFundamentals,
  isCryptoTokenomics,
  isForexMacro,
} from './fundamentals-api.service';
import {
  ApiChartDrawing,
  DrawingLayout,
  IndicatorConfig,
  IndicatorResult,
  SaveDrawingRequest,
} from './charting.models';

/** Available timeframes for the candlestick chart. */
export interface Timeframe {
  value: string;
  label: string;
}

/** Available bar-count presets. */
export interface BarCountOption {
  value: number;
  label: string;
}

/** Drawing tool descriptor for the left rail. */
export interface DrawingToolItem {
  type: DrawingToolType | null;
  icon: string;
  tooltip: string;
}

/**
 * Full-screen charting workspace — Prompt 7.
 *
 * Layout: chart canvas fills the entire viewport.
 * - Slim top strip: symbol picker, timeframe selector, bar-count control.
 * - Collapsible left tool rail: drawing tools as icon buttons (not a dropdown).
 * - Collapsible right indicator panel: active indicator configs with toggle +
 *   color swatch; add-indicator buttons; layout switcher dropdown.
 * - GlobalDrawingSettings.theme drives CandlestickChartComponent's theme input.
 * - Every drawing action emits (drawingChanged) → persisted via ChartingApiService.
 * - Layout save/load: save-as-new-layout + set-as-default via GlobalDrawingSettings.
 * - flash directive applied to live price readout.
 *
 * Route: /charting  (behind authGuard, full-bleed — no shell padding/marquee)
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
    MatExpansionModule,
    MatDialogModule,
    MatMenuModule,
    MatButtonToggleModule,
    CandlestickChartComponent,
    SymbolSearchComponent,
    PriceFlashDirective,
    LoadingStateComponent,
    EmptyStateComponent,
  ],
  templateUrl: './charting-page.component.html',
  styleUrls: ['./charting-page.component.scss'],
})
export class ChartingPageComponent implements OnInit, OnDestroy {
  private readonly chartingApi      = inject(ChartingApiService);
  private readonly marketSocket     = inject(MarketDataSocketService);
  private readonly snack            = inject(MatSnackBar);
  private readonly route            = inject(ActivatedRoute);
  private readonly watchlistApi     = inject(WatchlistApiService);
  private readonly fundamentalsApi  = inject(FundamentalsApiService);
  private readonly dialog           = inject(MatDialog);

  // Expose type guards to the template
  readonly isStockFundamentals = isStockFundamentals;
  readonly isCryptoTokenomics  = isCryptoTokenomics;
  readonly isForexMacro        = isForexMacro;

  // ── Timeframes ────────────────────────────────────────────────────────────

  readonly timeframes: Timeframe[] = [
    { value: '1m',  label: '1m'  },
    { value: '5m',  label: '5m'  },
    { value: '15m', label: '15m' },
    { value: '30m', label: '30m' },
    { value: '1h',  label: '1H'  },
    { value: '4h',  label: '4H'  },
    { value: '1d',  label: '1D'  },
    { value: '1w',  label: '1W'  },
  ];

  readonly barCountOptions: BarCountOption[] = [
    { value: 50,   label: '50'   },
    { value: 100,  label: '100'  },
    { value: 200,  label: '200'  },
    { value: 500,  label: '500'  },
  ];

  readonly activeTimeframe  = signal<string>('1h');
  readonly activeBarCount   = signal<number>(200);

  // ── Drawing tool rail ─────────────────────────────────────────────────────

  readonly drawingTools: DrawingToolItem[] = [
    { type: null,              icon: 'mouse',                tooltip: 'Pointer / select'           },
    { type: 'TREND_LINE',      icon: 'show_chart',           tooltip: 'Trend line'                 },
    { type: 'FIBONACCI',       icon: 'format_list_numbered', tooltip: 'Fibonacci retracement'      },
    { type: 'RECTANGLE',       icon: 'crop_square',          tooltip: 'Rectangle'                  },
    { type: 'HORIZONTAL_LINE', icon: 'horizontal_rule',      tooltip: 'Horizontal line'            },
    { type: 'VERTICAL_LINE',   icon: 'vertical_distribute',  tooltip: 'Vertical line'              },
  ];

  /** Active drawing tool driven by the left rail — bound to CandlestickChartComponent via selectDrawingTool(). */
  readonly activeDrawingTool = signal<DrawingToolType | null>(null);

  /** Whether the left drawing tool rail is collapsed (icon-only when expanded; hidden when collapsed). */
  readonly toolRailOpen = signal(true);

  // ── Right panel state ─────────────────────────────────────────────────────

  readonly sidenavOpen        = signal(true);
  readonly addingToWatchlist  = signal(false);

  // ── Symbol & layout ───────────────────────────────────────────────────────

  readonly activeSymbol       = signal<string>('BTCUSDT');
  readonly activeLayoutId     = signal<string | null>(null);
  readonly chartTheme         = signal<'dark' | 'light'>('dark');

  // ── Fundamentals panel ────────────────────────────────────────────────────

  readonly fundamentalsAssetClass = signal<AssetClass>('CRYPTO');
  readonly fundamentalsPanelOpen  = signal(false);
  readonly fundamentals           = signal<FundamentalsPayload | null>(null);
  readonly loadingFundamentals    = signal(false);
  readonly fundamentalsError      = signal<string | null>(null);

  // ── Chart data ────────────────────────────────────────────────────────────

  readonly chartBars        = signal<OhlcvBar[]>([]);
  readonly livePrice        = signal<number | null>(null);
  readonly drawings         = signal<ChartDrawing[]>([]);
  readonly indicatorSeries  = signal<IndicatorSeries[]>([]);
  readonly layouts          = signal<DrawingLayout[]>([]);
  readonly indicatorConfigs = signal<IndicatorConfig[]>([]);

  // ── Loading / error flags ─────────────────────────────────────────────────

  readonly loadingBars        = signal(false);
  readonly loadingDrawings    = signal(false);
  readonly loadingIndicators  = signal(false);
  readonly savingLayout       = signal(false);
  readonly errorBars          = signal<string | null>(null);

  // ── New layout dialog state ───────────────────────────────────────────────

  readonly showLayoutDialog   = signal(false);
  readonly newLayoutName      = signal('');

  // ── Computed helpers ──────────────────────────────────────────────────────

  /** Label for the active layout button in the toolbar. */
  readonly activeLayoutLabel = computed(() => {
    const id = this.activeLayoutId();
    if (!id) return 'No layout';
    return this.layouts().find((l) => l.id === id)?.name ?? 'Layout';
  });

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

  onSymbolSelected(result: SymbolSearchResult): void {
    this.onSymbolChange(result.ticker);
  }

  addToWatchlist(): void {
    if (this.addingToWatchlist()) return;
    this.addingToWatchlist.set(true);
    this.watchlistApi.addSymbol({ symbol: this.activeSymbol() }).subscribe({
      next: () => {
        this.snack.open(`${this.activeSymbol()} added to watchlist.`, 'OK', { duration: 2500 });
        this.addingToWatchlist.set(false);
      },
      error: () => {
        this.snack.open('Could not add to watchlist.', 'Close', { duration: 3000 });
        this.addingToWatchlist.set(false);
      },
    });
  }

  // ── Timeframe / bar-count ─────────────────────────────────────────────────

  onTimeframeChange(tf: string): void {
    this.activeTimeframe.set(tf);
    this.loadHistoricalBars();
  }

  onBarCountChange(count: number): void {
    this.activeBarCount.set(count);
    this.loadHistoricalBars();
  }

  // ── Drawing tool rail ─────────────────────────────────────────────────────

  setDrawingTool(tool: DrawingToolType | null): void {
    this.activeDrawingTool.set(tool);
  }

  toggleToolRail(): void {
    this.toolRailOpen.update((v) => !v);
  }

  // ── Core activation ───────────────────────────────────────────────────────

  private activateSymbol(symbol: string): void {
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
    this.errorBars.set(null);

    // Reset fundamentals panel when symbol changes.
    this.fundamentals.set(null);
    this.fundamentalsError.set(null);
    if (this.fundamentalsPanelOpen()) {
      this.loadFundamentals();
    }

    // 1. Fetch historical bars first, then subscribe to live ticks.
    this.loadHistoricalBars();

    // 2. Subscribe to live WebSocket ticks.
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
            low:    Math.min(last.low, tick.price),
            volume: last.volume + tick.volume,
          };
          return updated;
        }
        return [
          ...bars.slice(-499),
          {
            timestamp: tick.timestamp,
            open: tick.price, high: tick.price,
            low:  tick.price, close: tick.price,
            volume: tick.volume,
          },
        ];
      });
    });

    // 3. Load server-side state.
    this.loadDrawings();
    this.loadLayouts();
    this.loadIndicatorConfigs();
  }

  // ── Historical bar loading ────────────────────────────────────────────────

  private loadHistoricalBars(): void {
    this.loadingBars.set(true);
    this.errorBars.set(null);

    this.chartingApi.getOhlcv(
      this.activeSymbol(),
      this.activeTimeframe(),
      this.activeBarCount()
    ).subscribe({
      next: (bars) => {
        this.chartBars.set(bars);
        this.loadingBars.set(false);
      },
      error: () => {
        // Historical bars are best-effort; live ticks still populate the chart.
        this.loadingBars.set(false);
        this.errorBars.set('Could not load historical bars — live feed active.');
      },
    });
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
      error: () => {},
    });
  }

  switchLayout(layoutId: string): void {
    this.activeLayoutId.set(layoutId);
    this.loadDrawings();
  }

  /** Opens the inline "save as new layout" mini-form. */
  openLayoutDialog(): void {
    this.newLayoutName.set('');
    this.showLayoutDialog.set(true);
  }

  cancelLayoutDialog(): void {
    this.showLayoutDialog.set(false);
    this.newLayoutName.set('');
  }

  confirmSaveLayout(): void {
    const name = this.newLayoutName().trim();
    if (!name) return;

    this.savingLayout.set(true);
    this.showLayoutDialog.set(false);

    this.chartingApi.createLayout({ name, symbol: this.activeSymbol(), isGlobal: false }).subscribe({
      next: (layout) => {
        this.layouts.update((l) => [...l, layout]);
        this.activeLayoutId.set(layout.id);
        this.snack.open(`Layout "${layout.name}" saved.`, 'OK', { duration: 3000 });
        this.savingLayout.set(false);
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
            type:    r.type as IndicatorSeries['type'],
            data:    r.data.filter((v): v is number => v !== null),
            color:   r.color,
            subPane: r.subPane,
          }))
        );
      },
      error: () => {},
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
        this.snack.open(`${type} added.`, 'OK', { duration: 2000 });
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

  // ── Fundamentals panel ────────────────────────────────────────────────────

  toggleFundamentalsPanel(): void {
    const opening = !this.fundamentalsPanelOpen();
    this.fundamentalsPanelOpen.set(opening);
    if (opening && !this.fundamentals()) {
      this.loadFundamentals();
    }
  }

  onFundamentalsAssetClassChange(cls: AssetClass): void {
    this.fundamentalsAssetClass.set(cls);
    this.loadFundamentals();
  }

  loadFundamentals(): void {
    this.loadingFundamentals.set(true);
    this.fundamentalsError.set(null);
    this.fundamentals.set(null);

    this.fundamentalsApi
      .getFundamentals(this.activeSymbol(), this.fundamentalsAssetClass())
      .subscribe({
        next: (data) => {
          this.fundamentals.set(data);
          this.loadingFundamentals.set(false);
        },
        error: (err) => {
          this.fundamentalsError.set(
            err.status === 404
              ? `No fundamentals found for ${this.activeSymbol()}.`
              : 'Failed to load fundamentals data.'
          );
          this.loadingFundamentals.set(false);
          console.error('[Charting] fundamentals error', err);
        },
      });
  }

  // ── Theme ─────────────────────────────────────────────────────────────────

  toggleTheme(): void {
    const next = this.chartTheme() === 'dark' ? 'light' : 'dark';
    this.chartTheme.set(next);
    this.chartingApi.updateGlobalSettings({ theme: next }).subscribe();
  }

  // ── UI helpers ────────────────────────────────────────────────────────────

  toggleSidenav(): void {
    this.sidenavOpen.update((v) => !v);
  }

  layoutLabel(id: string): string {
    return this.layouts().find((l) => l.id === id)?.name ?? id;
  }

  formatLargeNumber(value: number | null | undefined, prefix = ''): string {
    if (value == null) return '—';
    const abs = Math.abs(value);
    if (abs >= 1e12) return `${prefix}${(value / 1e12).toFixed(2)}T`;
    if (abs >= 1e9)  return `${prefix}${(value / 1e9).toFixed(2)}B`;
    if (abs >= 1e6)  return `${prefix}${(value / 1e6).toFixed(2)}M`;
    if (abs >= 1e3)  return `${prefix}${(value / 1e3).toFixed(2)}K`;
    return `${prefix}${value.toFixed(2)}`;
  }

  formatPct(value: number | null | undefined): string {
    if (value == null) return '—';
    return (value * 100).toFixed(1) + '%';
  }

  orDash(value: string | number | null | undefined): string {
    if (value == null) return '—';
    return String(value);
  }

  trackById(_: number, item: { id: string }): string { return item.id; }
}
