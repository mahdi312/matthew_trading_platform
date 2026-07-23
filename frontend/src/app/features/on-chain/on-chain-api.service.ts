import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { REFERENCE_API } from '../../core/api/api-paths';
import { NftCollection, DefiPool } from './on-chain.models';

/**
 * HTTP client for OnChainModule — wraps reference-data-service endpoints.
 *
 * Endpoints consumed (all Gateway-routed via environment.gatewayBaseUrl):
 *
 *   GET /api/reference/nft/collections  → NftCollection[]
 *   GET /api/reference/defi/pools       → DefiPool[]
 *
 * The auth interceptor attaches the JWT automatically.
 */
@Injectable({ providedIn: 'root' })
export class OnChainApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.gatewayBaseUrl;

  /** Fetches the list of tracked NFT collections. */
  getNftCollections(): Observable<NftCollection[]> {
    return this.http.get<NftCollection[]>(`${this.base}${REFERENCE_API}/nft/collections`);
  }

  /** Fetches the list of tracked DeFi liquidity pools. */
  getDefiPools(): Observable<DefiPool[]> {
    return this.http.get<DefiPool[]>(`${this.base}${REFERENCE_API}/defi/pools`);
  }
}
