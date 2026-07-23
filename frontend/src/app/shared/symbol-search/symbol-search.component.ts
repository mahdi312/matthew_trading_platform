import {
  Component,
  EventEmitter,
  Input,
  OnDestroy,
  OnInit,
  Output,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { MatAutocompleteModule, MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import {
  Subject,
  Subscription,
  combineLatest,
  of,
} from 'rxjs';
import {
  catchError,
  debounceTime,
  distinctUntilChanged,
  filter,
  map,
  switchMap,
  tap,
} from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { MARKET_API, REFERENCE_API } from '../../core/api/api-paths';

/**
 * Unified result shape emitted to consumers.
 * `ticker`  — normalised upper-case symbol string  (e.g. "BTCUSDT", "AAPL")
 * `name`    — optional human-readable name / description
 * `assetClass` — coarse asset class string from either service
 */
export interface SymbolSearchResult {
  ticker: string;
  name: string;
  assetClass: string;
}

/**
 * Reusable "search as you type" symbol autocomplete.
 *
 * ## Features
 * - 300 ms debounce, minimum 1 character
 * - Calls both:
 *   • GET /api/reference/search?query=...          (SymbolSearchResultDto list)
 *   • GET /api/market/symbols/search?q=...         (SymbolEntry list)
 *   then merges + deduplicates on the ticker (upper-case).
 * - Emits `(symbolSelected)` with the chosen `SymbolSearchResult`.
 * - Optional `assetClass` input to pre-filter reference-service results.
 * - Standalone; presentation-only — no router, no store.
 *
 * ## Usage
 * ```html
 * <app-symbol-search
 *   [assetClass]="'CRYPTO'"
 *   [placeholder]="'Search symbol or company…'"
 *   (symbolSelected)="onSymbolSelected($event)">
 * </app-symbol-search>
 * ```
 */
@Component({
  selector: 'app-symbol-search',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatAutocompleteModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatIconModule,
  ],
  templateUrl: './symbol-search.component.html',
  styleUrls: ['./symbol-search.component.scss'],
})
export class SymbolSearchComponent implements OnInit, OnDestroy {
  private readonly http = inject(HttpClient);

  /** Optional filter — pass 'CRYPTO' | 'STOCK' | 'FOREX' to narrow results. */
  @Input() assetClass: string | null = null;

  /** Placeholder text for the input field. */
  @Input() placeholder = 'Search symbol or company…';

  /** Emitted when the user picks a symbol from the dropdown. */
  @Output() symbolSelected = new EventEmitter<SymbolSearchResult>();

  readonly searchCtrl = new FormControl<string>('');
  readonly results    = signal<SymbolSearchResult[]>([]);
  readonly loading    = signal(false);

  private readonly destroy$ = new Subject<void>();
  private sub: Subscription | null = null;

  ngOnInit(): void {
    this.sub = this.searchCtrl.valueChanges.pipe(
      debounceTime(300),
      map((v) => (v ?? '').trim()),
      distinctUntilChanged(),
      filter((q) => q.length >= 1),
      tap(() => this.loading.set(true)),
      switchMap((query) => this.fetchResults(query)),
    ).subscribe((items) => {
      this.results.set(items);
      this.loading.set(false);
    });
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
    this.destroy$.next();
    this.destroy$.complete();
  }

  /** Called when user selects an option from the autocomplete panel. */
  onOptionSelected(event: MatAutocompleteSelectedEvent): void {
    const result = event.option.value as SymbolSearchResult;
    this.symbolSelected.emit(result);
    // Reset the input text to show just the ticker
    this.searchCtrl.setValue(result.ticker, { emitEvent: false });
  }

  /** Display function for MatAutocomplete — shows ticker only in the input box. */
  displayFn(result: SymbolSearchResult | string | null): string {
    if (!result) return '';
    if (typeof result === 'string') return result;
    return result.ticker;
  }

  // ── Private helpers ────────────────────────────────────────────────────────

  private fetchResults(query: string) {
    const base = environment.gatewayBaseUrl;

    // ── 1. reference-data-service  GET /api/reference/search ─────────────────
    const refParams: Record<string, string> = { query, limit: '10' };
    if (this.assetClass) refParams['assetClass'] = this.assetClass;

    const ref$ = this.http
      .get<RefSearchDto[]>(`${base}${REFERENCE_API}/search`, { params: refParams })
      .pipe(
        map((items) =>
          items.map((i) => ({
            ticker:     (i.ticker ?? i.symbol ?? '').toUpperCase(),
            name:       i.name ?? i.description ?? '',
            assetClass: i.assetClass ?? '',
          }))
        ),
        catchError(() => of([] as SymbolSearchResult[])),
      );

    // ── 2. market-service  GET /api/market/symbols/search ────────────────────
    const mktParams: Record<string, string> = { q: query };
    if (this.assetClass) mktParams['type'] = this.assetClass;

    const mkt$ = this.http
      .get<MktSymbolEntry[]>(`${base}${MARKET_API}/symbols/search`, { params: mktParams })
      .pipe(
        map((items) =>
          items.map((i) => ({
            ticker:     (i.symbol ?? '').toUpperCase(),
            name:       i.name ?? '',
            assetClass: i.assetType ?? '',
          }))
        ),
        catchError(() => of([] as SymbolSearchResult[])),
      );

    // ── Merge + dedupe on ticker ──────────────────────────────────────────────
    return combineLatest([ref$, mkt$]).pipe(
      map(([refResults, mktResults]) => {
        const seen  = new Set<string>();
        const merged: SymbolSearchResult[] = [];
        for (const r of [...refResults, ...mktResults]) {
          if (r.ticker && !seen.has(r.ticker)) {
            seen.add(r.ticker);
            merged.push(r);
          }
        }
        return merged.slice(0, 20);
      }),
    );
  }
}

// ── DTO shapes (local, not shared — each service has different field names) ──

interface RefSearchDto {
  ticker?:      string;
  symbol?:      string;
  name?:        string;
  description?: string;
  assetClass?:  string;
}

interface MktSymbolEntry {
  symbol?:    string;
  name?:      string;
  assetType?: string;
}
