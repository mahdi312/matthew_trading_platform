import {
  Component,
  OnInit,
  inject,
  signal,
  computed,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTabsModule } from '@angular/material/tabs';

import { OnChainApiService } from './on-chain-api.service';
import { NftCollection, DefiPool } from './on-chain.models';
import { EmptyStateComponent } from '../../shared/empty-state/empty-state.component';
import { LoadingStateComponent } from '../../shared/loading-state/loading-state.component';

/**
 * OnChainModule — two views: NFT collections & DeFi liquidity pools.
 *
 * Tab 1 — NFT Collections:
 *   Lists collections with name, chain, floor price, 24h volume, items, owners.
 *   Client-side search filter on name/symbol.
 *
 * Tab 2 — DeFi Pools:
 *   Lists pools with protocol, chain, pair, fee, TVL, 24h volume, APY.
 *   Client-side search filter on protocol/pair.
 *
 * Both tabs reload independently via their own refresh buttons.
 *
 * Route: /on-chain  (behind authGuard)
 */
@Component({
  selector: 'app-on-chain-page',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterModule,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatTooltipModule,
    MatSnackBarModule,
    MatTabsModule,
    EmptyStateComponent,
    LoadingStateComponent,
  ],
  templateUrl: './on-chain-page.component.html',
  styleUrls: ['./on-chain-page.component.scss'],
})
export class OnChainPageComponent implements OnInit {
  private readonly api   = inject(OnChainApiService);
  private readonly snack = inject(MatSnackBar);

  // ── NFT state ─────────────────────────────────────────────────────────────

  readonly loadingNft  = signal(true);
  readonly nftError    = signal<string | null>(null);
  readonly nftRaw      = signal<NftCollection[]>([]);
  readonly nftSearch   = signal('');
  readonly nftChain    = signal<string>('ALL');
  /** Sort key for the NFT card grid — chain (alpha) or floor price (desc). */
  readonly nftSort     = signal<'chain' | 'floor'>('floor');

  readonly nftChains = computed(() =>
    Array.from(new Set(this.nftRaw().map(c => c.chain))).sort()
  );

  readonly nftFiltered = computed(() => {
    const q = this.nftSearch().toLowerCase();
    const chain = this.nftChain();
    let list = this.nftRaw();
    if (chain !== 'ALL') list = list.filter(c => c.chain === chain);
    if (q) {
      list = list.filter(c =>
        c.name.toLowerCase().includes(q) ||
        c.symbol.toLowerCase().includes(q) ||
        c.chain.toLowerCase().includes(q)
      );
    }
    const sorted = [...list];
    if (this.nftSort() === 'chain') {
      sorted.sort((a, b) => a.chain.localeCompare(b.chain) || a.name.localeCompare(b.name));
    } else {
      sorted.sort((a, b) => (b.floorPrice ?? -Infinity) - (a.floorPrice ?? -Infinity));
    }
    return sorted;
  });

  // ── DeFi state ────────────────────────────────────────────────────────────

  readonly loadingDefi = signal(true);
  readonly defiError   = signal<string | null>(null);
  readonly defiRaw     = signal<DefiPool[]>([]);
  readonly defiSearch  = signal('');
  /** Sort key for the DeFi table — defaults to TVL descending per the spec. */
  readonly defiSort    = signal<'tvl' | 'apy'>('tvl');

  readonly defiFiltered = computed(() => {
    const q = this.defiSearch().toLowerCase();
    let list = this.defiRaw();
    if (q) {
      list = list.filter(p =>
        p.protocol.toLowerCase().includes(q) ||
        p.token0Symbol.toLowerCase().includes(q) ||
        p.token1Symbol.toLowerCase().includes(q) ||
        p.chain.toLowerCase().includes(q)
      );
    }
    const sorted = [...list];
    if (this.defiSort() === 'apy') {
      sorted.sort((a, b) => (b.apy ?? -Infinity) - (a.apy ?? -Infinity));
    } else {
      sorted.sort((a, b) => (b.tvl ?? -Infinity) - (a.tvl ?? -Infinity));
    }
    return sorted;
  });

  // ── Table columns (DeFi only — NFT is now a card grid) ─────────────────────

  readonly defiColumns = ['protocol', 'chain', 'pair', 'feeTier', 'tvl', 'volume24h', 'apy'];

  setNftSort(s: 'chain' | 'floor'): void { this.nftSort.set(s); }
  setDefiSort(s: 'tvl' | 'apy'): void { this.defiSort.set(s); }

  // ── Lifecycle ─────────────────────────────────────────────────────────────

  ngOnInit(): void {
    this.loadNft();
    this.loadDefi();
  }

  // ── NFT ───────────────────────────────────────────────────────────────────

  loadNft(): void {
    this.loadingNft.set(true);
    this.nftError.set(null);
    this.api.getNftCollections().subscribe({
      next: (data) => { this.nftRaw.set(data);  this.loadingNft.set(false); },
      error: (err) => {
        this.nftError.set('Failed to load NFT collections.');
        this.loadingNft.set(false);
        console.error('[OnChain] NFT error', err);
      },
    });
  }

  // ── DeFi ──────────────────────────────────────────────────────────────────

  loadDefi(): void {
    this.loadingDefi.set(true);
    this.defiError.set(null);
    this.api.getDefiPools().subscribe({
      next: (data) => { this.defiRaw.set(data); this.loadingDefi.set(false); },
      error: (err) => {
        this.defiError.set('Failed to load DeFi pools.');
        this.loadingDefi.set(false);
        console.error('[OnChain] DeFi error', err);
      },
    });
  }

  // ── UI helpers ────────────────────────────────────────────────────────────

  pairLabel(pool: DefiPool): string {
    if (!pool.token0Symbol || !pool.token1Symbol) return 'Not available';
    return `${pool.token0Symbol} / ${pool.token1Symbol}`;
  }

  feeTierLabel(bp: number | null): string {
    if (bp === null) return '—';
    return (bp / 100).toFixed(2) + '%';
  }

  apyLabel(v: number | null): string {
    if (v === null) return '—';
    return (v * 100).toFixed(2) + '%';
  }

  usdLabel(v: number | null): string {
    if (v === null) return '—';
    if (v >= 1e9)  return '$' + (v / 1e9).toFixed(2) + 'B';
    if (v >= 1e6)  return '$' + (v / 1e6).toFixed(2) + 'M';
    if (v >= 1e3)  return '$' + (v / 1e3).toFixed(2) + 'K';
    return '$' + v.toFixed(2);
  }

  trackById(_: number, item: { id: string }): string { return item.id; }
}
