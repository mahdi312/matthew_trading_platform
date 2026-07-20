import {
  Component,
  Input,
  OnChanges,
  OnDestroy,
  SimpleChanges,
  ElementRef,
  ViewChild,
  AfterViewInit,
  inject,
  NgZone,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import * as echarts from 'echarts';
import type { ECharts, EChartsOption } from 'echarts';

import { BarChartDatum } from '../models/bar-chart-datum.model';

/**
 * Standalone, presentation-only categorical bar chart backed by ECharts.
 *
 * ## Responsibilities (what this component DOES):
 * - Renders one bar per `BarChartDatum`, colored bull/bear by the sign of
 *   its value when `colorBySign` is true (the default) — used for P&L-style
 *   bar charts (e.g. Reports' monthly net P&L breakdown).
 * - Supports dark/light theme switching via `theme`.
 *
 * ## Separation of concerns (what this component does NOT do):
 * - Makes NO HTTP calls. The host screen fetches and shapes the data.
 * - Does not know anything about "reports" or any other domain — it is a
 *   generic categorical bar chart, kept in `shared/chart-library/` per the
 *   platform-wide rule that every chart on every screen goes through the
 *   Chart Library rather than being one-off reimplemented.
 *
 * ## Usage example:
 * ```html
 * <app-bar-chart
 *   [data]="monthlyNetPnl()"
 *   [theme]="'dark'"
 *   [valuePrefix]="'$'"
 * ></app-bar-chart>
 * ```
 */
@Component({
  selector: 'app-bar-chart',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './bar-chart.component.html',
  styleUrls: ['./bar-chart.component.scss'],
})
export class BarChartComponent implements AfterViewInit, OnChanges, OnDestroy {
  /** Bars to render, in display order. */
  @Input() data: BarChartDatum[] = [];

  /** Chart colour theme. */
  @Input() theme: 'dark' | 'light' = 'dark';

  /** When true (default), colors each bar bull (>=0) / bear (<0) by sign. */
  @Input() colorBySign = true;

  /** Prefix shown before each value in the tooltip (e.g. '$'). */
  @Input() valuePrefix = '';

  @ViewChild('chartContainer', { static: false })
  private chartContainerRef!: ElementRef<HTMLDivElement>;

  private chart: ECharts | null = null;
  private resizeObserver: ResizeObserver | null = null;
  private readonly zone = inject(NgZone);

  ngAfterViewInit(): void {
    this.initChart();
    this.setupResizeObserver();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (!this.chart) return;
    if (changes['data'] || changes['theme'] || changes['colorBySign'] || changes['valuePrefix']) {
      this.renderChart();
    }
  }

  ngOnDestroy(): void {
    this.resizeObserver?.disconnect();
    this.chart?.dispose();
    this.chart = null;
  }

  private initChart(): void {
    if (!this.chartContainerRef?.nativeElement) return;
    this.zone.runOutsideAngular(() => {
      this.chart = echarts.init(
        this.chartContainerRef.nativeElement,
        this.theme === 'dark' ? 'dark' : undefined
      );
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

  private renderChart(): void {
    if (!this.chart) return;
    this.chart.setOption(this.buildOption(), { notMerge: true });
  }

  private buildOption(): EChartsOption {
    const isDark = this.theme === 'dark';
    const textColor = isDark ? '#8A93A6' : '#545C6B';
    const lineColor = isDark ? '#2A303C' : '#dddddd';
    const bullColor = isDark ? '#2FBE7A' : '#1A7F4F';
    const bearColor = isDark ? '#F0555A' : '#C73237';
    const neutralColor = isDark ? '#D4A24C' : '#D4A24C';

    return {
      backgroundColor: 'transparent',
      animation: true,
      animationDuration: 260,
      grid: { left: 48, right: 16, top: 20, bottom: 28 },
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        backgroundColor: isDark ? '#1B2029' : '#ffffff',
        borderColor: isDark ? '#2A303C' : '#dddddd',
        textStyle: { color: isDark ? '#EDEFF3' : '#12151C' },
        formatter: (params: unknown) => this.tooltipFormatter(params),
      },
      xAxis: {
        type: 'category',
        data: this.data.map((d) => d.label),
        axisLine: { lineStyle: { color: lineColor } },
        axisTick: { show: false },
        axisLabel: { color: textColor, fontSize: 11 },
        splitLine: { show: false },
      },
      yAxis: {
        type: 'value',
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: {
          color: textColor,
          fontSize: 11,
          formatter: (v: number) => this.formatCompact(v),
        },
        splitLine: { lineStyle: { color: lineColor, type: 'dashed', opacity: 0.5 } },
      },
      series: [
        {
          type: 'bar',
          data: this.data.map((d) => ({
            value: d.value,
            itemStyle: {
              color: this.colorBySign ? (d.value >= 0 ? bullColor : bearColor) : neutralColor,
              borderRadius: [3, 3, 0, 0],
            },
          })),
          barMaxWidth: 28,
        },
      ],
    };
  }

  private tooltipFormatter(params: unknown): string {
    const items = Array.isArray(params) ? params : [params];
    const p = items[0] as { name?: string; value?: number } | undefined;
    if (!p) return '';
    const v = p.value ?? 0;
    const sign = v > 0 ? '+' : '';
    return `<div style="font-size:12px"><div>${p.name}</div><div><b>${sign}${this.valuePrefix}${v.toLocaleString(undefined, { maximumFractionDigits: 2 })}</b></div></div>`;
  }

  private formatCompact(v: number): string {
    const abs = Math.abs(v);
    if (abs >= 1e6) return (v / 1e6).toFixed(1) + 'M';
    if (abs >= 1e3) return (v / 1e3).toFixed(1) + 'K';
    return String(v);
  }
}
