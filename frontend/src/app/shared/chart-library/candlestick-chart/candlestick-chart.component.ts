import {
  Component,
  Input,
  Output,
  EventEmitter,
  OnChanges,
  OnInit,
  OnDestroy,
  SimpleChanges,
  ElementRef,
  ViewChild,
  AfterViewInit,
  signal,
  computed,
  inject,
  NgZone,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import * as echarts from 'echarts';
import type { ECharts, EChartsOption } from 'echarts';

import { OhlcvBar } from '../models/ohlcv.model';
import { IndicatorSeries } from '../models/indicator-series.model';
import { ChartDrawing, DrawingChangedEvent, DrawingToolType } from '../models/drawing.model';

/**
 * Standalone, presentation-only candlestick chart component backed by ECharts.
 *
 * ## Responsibilities (what this component DOES):
 * - Renders OHLCV candlestick data with volume bars in a sub-pane.
 * - Overlays indicator series (SMA/EMA on main pane; RSI/MACD in sub-panes).
 * - Supports dark/light theme switching.
 * - Provides zoom, pan, and crosshair interactions (built-in ECharts dataZoom).
 * - Drawing tools: trend lines, Fibonacci retracements, rectangles, horizontal
 *   and vertical lines — emits `(drawingChanged)` for the host to persist.
 * - Accepts a `livePrice` input for real-time tick updates without a full redraw.
 *
 * ## Separation of concerns (what this component does NOT do):
 * - Makes NO HTTP calls.
 * - Opens NO WebSocket connections.
 * - The host module feeds OHLCV and indicator data, and handles persistence of
 *   drawings via the `(drawingChanged)` output event.
 *
 * ## Usage example:
 * ```html
 * <app-candlestick-chart
 *   [ohlcvData]="bars"
 *   [indicators]="computedIndicators"
 *   [drawings]="savedDrawings"
 *   [theme]="'dark'"
 *   [livePrice]="latestTickPrice"
 *   (drawingChanged)="onDrawingChanged($event)"
 * ></app-candlestick-chart>
 * ```
 */
@Component({
  selector: 'app-candlestick-chart',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    MatButtonToggleModule,
  ],
  templateUrl: './candlestick-chart.component.html',
  styleUrls: ['./candlestick-chart.component.scss'],
})
export class CandlestickChartComponent implements OnInit, AfterViewInit, OnChanges, OnDestroy {
  // ── Inputs ────────────────────────────────────────────────────────────────

  /** OHLCV bars to render. Defaults to empty array (renders an empty chart). */
  @Input() ohlcvData: OhlcvBar[] = [];

  /**
   * Computed indicator series to overlay.
   * Each item carries its own `subPane` flag so the chart knows whether to
   * place it on the main price pane or a dedicated sub-pane below.
   */
  @Input() indicators: IndicatorSeries[] = [];

  /**
   * Persisted drawings to display on chart load.
   * The host passes these in (e.g. from `/api/charts/drawings`) — this
   * component renders them as ECharts markLines / markAreas.
   */
  @Input() drawings: ChartDrawing[] = [];

  /** Chart colour theme. */
  @Input() theme: 'dark' | 'light' = 'dark';

  /** When false, hides the drawing toolbar (embed / read-only hosts). */
  @Input() showToolbar = true;

  /**
   * Latest live price fed from a WebSocket subscription in the host module.
   * When set, updates only the last candle's close price without a full
   * option redraw (uses ECharts `chart.appendData` / series update).
   */
  @Input() livePrice: number | null = null;

  // ── Outputs ───────────────────────────────────────────────────────────────

  /**
   * Emitted whenever the user creates, moves, or deletes a drawing.
   * The host module (ChartingModule / LiveTradingModule) listens and
   * persists the change via `/api/charts/drawings`.
   */
  @Output() drawingChanged = new EventEmitter<DrawingChangedEvent>();

  // ── View refs ─────────────────────────────────────────────────────────────

  @ViewChild('chartContainer', { static: false })
  private chartContainerRef!: ElementRef<HTMLDivElement>;

  // ── Private state ─────────────────────────────────────────────────────────

  private chart: ECharts | null = null;
  private resizeObserver: ResizeObserver | null = null;
  private readonly zone = inject(NgZone);

  /** Currently active drawing tool; null = pointer / select mode. */
  readonly activeDrawingTool = signal<DrawingToolType | null>(null);

  /** Mutable list of drawings managed locally before emit. */
  private localDrawings: ChartDrawing[] = [];

  /** Track pending drawing in progress (first click placed, awaiting second). */
  private pendingDrawing: Partial<ChartDrawing> | null = null;

  // ── Lifecycle ─────────────────────────────────────────────────────────────

  ngOnInit(): void {
    this.localDrawings = [...this.drawings];
  }

  ngAfterViewInit(): void {
    this.initChart();
    this.setupResizeObserver();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (!this.chart) return;

    if (changes['ohlcvData'] || changes['indicators'] || changes['drawings'] || changes['theme']) {
      if (changes['drawings']) {
        this.localDrawings = [...this.drawings];
      }
      this.renderChart();
    }

    if (changes['livePrice'] && this.livePrice !== null) {
      this.updateLivePrice(this.livePrice);
    }
  }

  ngOnDestroy(): void {
    this.resizeObserver?.disconnect();
    this.chart?.dispose();
    this.chart = null;
  }

  // ── Public drawing toolbar API ─────────────────────────────────────────────

  selectDrawingTool(tool: DrawingToolType | null): void {
    this.activeDrawingTool.set(tool);
    this.pendingDrawing = null;
  }

  clearAllDrawings(): void {
    this.localDrawings = [];
    this.renderChart();
    // Notify host that all drawings were removed.
    // (host can batch-delete via the API)
  }

  // ── Chart initialisation ───────────────────────────────────────────────────

  private initChart(): void {
    if (!this.chartContainerRef?.nativeElement) return;

    this.zone.runOutsideAngular(() => {
      this.chart = echarts.init(
        this.chartContainerRef.nativeElement,
        this.theme === 'dark' ? 'dark' : undefined
      );
      this.setupChartClickHandler();
      this.renderChart();
    });
  }

  private setupResizeObserver(): void {
    if (!this.chartContainerRef?.nativeElement) return;
    this.resizeObserver = new ResizeObserver(() => {
      this.zone.runOutsideAngular(() => this.chart?.resize());
    });
    this.resizeObserver.observe(this.chartContainerRef.nativeElement);
  }

  // ── Chart rendering ───────────────────────────────────────────────────────

  private renderChart(): void {
    if (!this.chart) return;

    const option = this.buildOption();
    this.chart.setOption(option, { notMerge: true });
  }

  private buildOption(): EChartsOption {
    const isDark = this.theme === 'dark';
    const textColor = isDark ? '#e0e0e0' : '#333333';
    const lineColor = isDark ? '#333333' : '#dddddd';
    const upColor = '#26a69a';
    const downColor = '#ef5350';

    // Split indicators into main-pane overlays vs sub-pane series.
    const mainOverlays = this.indicators.filter((i) => !i.subPane);
    const subPaneSeries = this.indicators.filter((i) => i.subPane);

    // Build grid layout: main price grid + volume grid + one grid per sub-pane indicator.
    const grids = this.buildGrids(subPaneSeries.length);
    const xAxes = this.buildXAxes(grids.length, textColor, lineColor);
    const yAxes = this.buildYAxes(grids.length, textColor, lineColor);

    const candleSeries = this.buildCandleSeries(upColor, downColor);
    const volumeSeries = this.buildVolumeSeries(upColor, downColor, grids.length);
    const overlaySeries = this.buildOverlaySeries(mainOverlays);
    const subPaneSeriesList = this.buildSubPaneSeries(subPaneSeries, grids.length);
    const drawingMarkLines = this.buildDrawingMarkLines();

    return {
      backgroundColor: isDark ? '#1a1a2e' : '#ffffff',
      animation: false,
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'cross', crossStyle: { color: isDark ? '#888' : '#666' } },
        backgroundColor: isDark ? '#1e1e2e' : '#ffffff',
        borderColor: isDark ? '#444' : '#ddd',
        textStyle: { color: textColor },
        formatter: (params) => this.tooltipFormatter(params),
      },
      legend: {
        top: 4,
        right: 16,
        textStyle: { color: textColor },
        data: [
          'OHLCV',
          ...mainOverlays.map((i) => i.name),
          ...subPaneSeries.map((i) => i.name),
        ],
      },
      axisPointer: { link: [{ xAxisIndex: 'all' }] },
      dataZoom: [
        {
          type: 'inside',
          xAxisIndex: Array.from({ length: grids.length }, (_, i) => i),
          start: 70,
          end: 100,
        },
        {
          type: 'slider',
          xAxisIndex: Array.from({ length: grids.length }, (_, i) => i),
          bottom: 4,
          height: 20,
          start: 70,
          end: 100,
          handleStyle: { color: isDark ? '#555' : '#aaa' },
          textStyle: { color: textColor },
          borderColor: lineColor,
        },
      ],
      grid: grids,
      xAxis: xAxes,
      yAxis: yAxes,
      series: [
        candleSeries,
        volumeSeries,
        ...overlaySeries,
        ...subPaneSeriesList,
        ...drawingMarkLines,
      ] as EChartsOption['series'],
    };
  }

  // ── Grid / axis builders ─────────────────────────────────────────────────

  private buildGrids(subPaneCount: number): object[] {
    const grids: object[] = [];
    // Main price pane
    grids.push({ left: 60, right: 16, top: 36, bottom: subPaneCount > 0 ? '45%' : '14%' });
    // Volume pane
    grids.push({ left: 60, right: 16, top: subPaneCount > 0 ? '58%' : '70%', bottom: subPaneCount > 0 ? '36%' : '10%' });
    // One pane per sub-pane indicator
    const subPaneHeight = subPaneCount > 0 ? Math.floor(28 / subPaneCount) : 0;
    for (let i = 0; i < subPaneCount; i++) {
      grids.push({
        left: 60,
        right: 16,
        top: `${72 + i * subPaneHeight}%`,
        bottom: `${8 + (subPaneCount - 1 - i) * subPaneHeight}%`,
      });
    }
    return grids;
  }

  private buildXAxes(count: number, textColor: string, lineColor: string): object[] {
    return Array.from({ length: count }, (_, i) => ({
      type: 'category',
      data: this.ohlcvData.map((b) => b.timestamp),
      gridIndex: i,
      axisLine: { lineStyle: { color: lineColor } },
      axisTick: { lineStyle: { color: lineColor } },
      axisLabel: { color: textColor, show: i === 0 || i === count - 1 },
      splitLine: { show: false },
      scale: true,
      boundaryGap: false,
      axisPointer: { show: true },
    }));
  }

  private buildYAxes(count: number, textColor: string, lineColor: string): object[] {
    return Array.from({ length: count }, (_, i) => ({
      scale: true,
      gridIndex: i,
      splitNumber: i === 1 ? 2 : 4,
      axisLine: { lineStyle: { color: lineColor } },
      axisTick: { lineStyle: { color: lineColor } },
      axisLabel: { color: textColor },
      splitLine: { lineStyle: { color: lineColor, type: 'dashed', opacity: 0.4 } },
    }));
  }

  // ── Series builders ────────────────────────────────────────────────────────

  private buildCandleSeries(upColor: string, downColor: string): object {
    return {
      name: 'OHLCV',
      type: 'candlestick',
      xAxisIndex: 0,
      yAxisIndex: 0,
      data: this.ohlcvData.map((b) => [b.open, b.close, b.low, b.high]),
      itemStyle: {
        color: upColor,
        color0: downColor,
        borderColor: upColor,
        borderColor0: downColor,
      },
    };
  }

  private buildVolumeSeries(upColor: string, downColor: string, gridCount: number): object {
    return {
      name: 'Volume',
      type: 'bar',
      xAxisIndex: 1,
      yAxisIndex: 1,
      data: this.ohlcvData.map((b, i) => ({
        value: b.volume,
        itemStyle: { color: b.close >= b.open ? upColor : downColor, opacity: 0.6 },
      })),
      barMaxWidth: 6,
    };
  }

  private buildOverlaySeries(overlays: IndicatorSeries[]): object[] {
    return overlays.map((ind) => ({
      name: ind.name,
      type: 'line',
      xAxisIndex: 0,
      yAxisIndex: 0,
      data: ind.data,
      smooth: true,
      showSymbol: false,
      lineStyle: { color: ind.color, width: 1.5 },
      itemStyle: { color: ind.color },
    }));
  }

  private buildSubPaneSeries(subSeries: IndicatorSeries[], gridCount: number): object[] {
    // First two grids are main + volume; sub-pane indicators start at index 2.
    return subSeries.map((ind, i) => ({
      name: ind.name,
      type: 'line',
      xAxisIndex: 2 + i,
      yAxisIndex: 2 + i,
      data: ind.data,
      smooth: true,
      showSymbol: false,
      lineStyle: { color: ind.color, width: 1.5 },
      itemStyle: { color: ind.color },
      areaStyle: { opacity: 0.08 },
    }));
  }

  // ── Drawing overlay builders ──────────────────────────────────────────────

  private buildDrawingMarkLines(): object[] {
    return this.localDrawings.map((drawing) => {
      switch (drawing.type) {
        case 'TREND_LINE':
        case 'FIBONACCI':
          return this.drawingToMarkLine(drawing);
        case 'HORIZONTAL_LINE':
          return this.horizontalLineToMarkLine(drawing);
        case 'VERTICAL_LINE':
          return this.verticalLineToMarkLine(drawing);
        case 'RECTANGLE':
          return this.rectangleToMarkArea(drawing);
        default:
          return {};
      }
    });
  }

  private drawingToMarkLine(d: ChartDrawing): object {
    const [p1, p2] = d.points;
    return {
      type: 'line',
      xAxisIndex: 0,
      yAxisIndex: 0,
      data: [],
      markLine: {
        silent: false,
        lineStyle: { color: d.color ?? '#ffd700', width: d.lineWidth ?? 1.5 },
        label: { show: !!d.label, formatter: d.label ?? '' },
        data: [[
          { coord: [p1.timestamp, p1.price] },
          { coord: [p2.timestamp, p2.price] },
        ]],
      },
    };
  }

  private horizontalLineToMarkLine(d: ChartDrawing): object {
    const price = d.points[0]?.price ?? 0;
    return {
      type: 'line',
      xAxisIndex: 0,
      yAxisIndex: 0,
      data: [],
      markLine: {
        silent: false,
        lineStyle: { color: d.color ?? '#90caf9', width: d.lineWidth ?? 1, type: 'dashed' },
        label: { show: true, formatter: `${price.toFixed(2)}` },
        data: [{ yAxis: price }],
      },
    };
  }

  private verticalLineToMarkLine(d: ChartDrawing): object {
    const ts = d.points[0]?.timestamp ?? '';
    return {
      type: 'line',
      xAxisIndex: 0,
      yAxisIndex: 0,
      data: [],
      markLine: {
        silent: false,
        lineStyle: { color: d.color ?? '#ce93d8', width: d.lineWidth ?? 1, type: 'dashed' },
        data: [{ xAxis: ts }],
      },
    };
  }

  private rectangleToMarkArea(d: ChartDrawing): object {
    const [p1, , p3] = d.points; // top-left and bottom-right
    return {
      type: 'line',
      xAxisIndex: 0,
      yAxisIndex: 0,
      data: [],
      markArea: {
        silent: false,
        itemStyle: { color: d.color ?? 'rgba(255, 215, 0, 0.08)', borderColor: d.color ?? '#ffd700', borderWidth: 1 },
        data: [[
          { coord: [p1.timestamp, p1.price] },
          { coord: [p3.timestamp, p3.price] },
        ]],
      },
    };
  }

  // ── Live price update ─────────────────────────────────────────────────────

  private updateLivePrice(price: number): void {
    if (!this.chart || this.ohlcvData.length === 0) return;

    // Update the last candle's close to reflect the live price.
    const lastBar = this.ohlcvData[this.ohlcvData.length - 1];
    const updatedData = this.ohlcvData.map((b, i) =>
      i === this.ohlcvData.length - 1
        ? { ...b, close: price, high: Math.max(b.high, price), low: Math.min(b.low, price) }
        : b
    );

    this.zone.runOutsideAngular(() => {
      this.chart?.setOption({
        series: [
          {
            data: updatedData.map((b) => [b.open, b.close, b.low, b.high]),
          },
        ],
      });
    });
  }

  // ── Click-to-draw handler ─────────────────────────────────────────────────

  private setupChartClickHandler(): void {
    if (!this.chart) return;

    this.chart.on('click', (params: any) => {
      const tool = this.activeDrawingTool();
      if (!tool) return;

      const timestamp = params.name ?? params.data?.[0] ?? params.value?.[0];
      const price = params.value?.[1] ?? params.value?.[2] ?? 0;

      this.zone.run(() => this.handleDrawingClick(tool, { timestamp, price }));
    });
  }

  private handleDrawingClick(
    tool: DrawingToolType,
    point: { timestamp: string | number; price: number }
  ): void {
    if (tool === 'HORIZONTAL_LINE' || tool === 'VERTICAL_LINE') {
      // Single-click tools.
      const drawing: ChartDrawing = {
        id: this.generateId(),
        type: tool,
        points: [{ timestamp: point.timestamp, price: point.price }],
      };
      this.localDrawings = [...this.localDrawings, drawing];
      this.renderChart();
      this.drawingChanged.emit({ action: 'created', drawing });
      return;
    }

    if (!this.pendingDrawing) {
      // First click — store the starting point.
      this.pendingDrawing = {
        id: this.generateId(),
        type: tool,
        points: [{ timestamp: point.timestamp, price: point.price }],
      };
    } else {
      // Second click — complete the drawing.
      const startPoint = this.pendingDrawing.points![0];
      let points = [startPoint, { timestamp: point.timestamp, price: point.price }];

      // Rectangles need 4 corner points (derive from two diagonals).
      if (tool === 'RECTANGLE') {
        points = [
          startPoint,
          { timestamp: point.timestamp, price: startPoint.price },
          { timestamp: point.timestamp, price: point.price },
          { timestamp: startPoint.timestamp, price: point.price },
        ];
      }

      // Fibonacci: default levels 0, 0.236, 0.382, 0.5, 0.618, 0.786, 1.
      const fibLevels =
        tool === 'FIBONACCI'
          ? [0, 0.236, 0.382, 0.5, 0.618, 0.786, 1]
          : undefined;

      const drawing: ChartDrawing = {
        ...(this.pendingDrawing as ChartDrawing),
        points,
        fibLevels,
      };

      this.pendingDrawing = null;
      this.localDrawings = [...this.localDrawings, drawing];
      this.renderChart();
      this.drawingChanged.emit({ action: 'created', drawing });
    }
  }

  // ── Tooltip formatter ──────────────────────────────────────────────────────

  private tooltipFormatter(params: unknown): string {
    type TooltipItem = {
      seriesName?: string;
      axisValue?: string | number;
      data?: number[];
      value?: number;
      color?: string;
    };
    const items: TooltipItem[] = Array.isArray(params)
      ? (params as TooltipItem[])
      : [params as TooltipItem];
    if (!items.length) return '';
    const candle = items.find((p) => p.seriesName === 'OHLCV');
    if (!candle) return String(items[0]?.axisValue ?? '');

    const [open, close, low, high] = candle.data as number[];
    const color = close >= open ? '#26a69a' : '#ef5350';
    const ts = candle.axisValue;

    let html = `
      <div style="font-size:12px;line-height:1.8">
        <div style="color:#aaa">${ts}</div>
        <div>O: <b style="color:${color}">${open?.toFixed(2)}</b></div>
        <div>H: <b style="color:${color}">${high?.toFixed(2)}</b></div>
        <div>L: <b style="color:${color}">${low?.toFixed(2)}</b></div>
        <div>C: <b style="color:${color}">${close?.toFixed(2)}</b></div>
    `;

    const vol = items.find((p) => p.seriesName === 'Volume');
    if (vol) {
      html += `<div>Vol: <b>${this.formatVolume(vol.value as number)}</b></div>`;
    }

    for (const ind of items.filter(
      (p) => p.seriesName !== 'OHLCV' && p.seriesName !== 'Volume'
    )) {
      html += `<div>${ind.seriesName}: <b style="color:${ind.color}">${Number(ind.value)?.toFixed(4)}</b></div>`;
    }

    html += '</div>';
    return html;
  }

  private formatVolume(v: number): string {
    if (v >= 1_000_000) return (v / 1_000_000).toFixed(2) + 'M';
    if (v >= 1_000) return (v / 1_000).toFixed(1) + 'K';
    return v?.toFixed(0) ?? '0';
  }

  // ── Utility ────────────────────────────────────────────────────────────────

  private generateId(): string {
    return `drawing-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;
  }
}
