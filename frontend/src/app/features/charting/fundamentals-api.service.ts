import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { REFERENCE_API } from '../../core/api/api-paths';

/**
 * HTTP client for GET /api/reference/fundamentals/{symbol}.
 *
 * The endpoint returns one of three distinct DTO shapes depending on assetClass:
 *   STOCK  → CompanyFundamentalsDto
 *   CRYPTO → CryptoTokenomicsDto
 *   FOREX  → ForexMacroIndicatorsDto
 *
 * The service returns the raw object as `FundamentalsPayload` (a discriminated
 * union resolved at the component level by inspecting which fields are present).
 */
@Injectable({ providedIn: 'root' })
export class FundamentalsApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.gatewayBaseUrl;

  /**
   * Fetch fundamentals for a symbol.
   *
   * @param symbol    Canonical ticker (e.g. "AAPL", "BTC", "EURUSD").
   * @param assetClass  "STOCK" | "CRYPTO" | "FOREX" (default "STOCK").
   */
  getFundamentals(symbol: string, assetClass: AssetClass = 'STOCK'): Observable<FundamentalsPayload> {
    const params = new HttpParams().set('assetClass', assetClass);
    return this.http.get<FundamentalsPayload>(
      `${this.base}${REFERENCE_API}/fundamentals/${symbol}`,
      { params }
    );
  }
}

// ── Asset class ───────────────────────────────────────────────────────────────

export type AssetClass = 'STOCK' | 'CRYPTO' | 'FOREX';

// ── Response shapes — mirrors backend DTOs exactly ────────────────────────────

/** Stock / equity fundamentals (assetClass=STOCK). */
export interface CompanyFundamentalsDto {
  symbol:               string;
  companyName:          string;
  exchange:             string | null;
  sector:               string | null;
  industry:             string | null;
  country:              string | null;
  currency:             string | null;
  providerName:         string | null;
  fetchDate:            string | null;

  // Valuation
  marketCap:            number | null;
  enterpriseValue:      number | null;
  peRatioTtm:           number | null;
  peRatioForward:       number | null;
  priceToBook:          number | null;
  priceToSalesTtm:      number | null;
  evToEbitda:           number | null;

  // Income
  revenueAnnual:        number | null;
  revenueGrowthYoy:     number | null;
  grossMargin:          number | null;
  operatingMargin:      number | null;
  netMargin:            number | null;
  epsDilutedTtm:        number | null;
  epsGrowthYoy:         number | null;

  // Balance sheet
  totalAssets:          number | null;
  totalDebt:            number | null;
  cashAndEquivalents:   number | null;
  debtToEquity:         number | null;
  currentRatio:         number | null;

  // Cash flow
  freeCashFlowTtm:      number | null;
  freeCashFlowPerShare: number | null;

  // Dividends
  dividendYield:        number | null;
  dividendPerShare:     number | null;
  payoutRatio:          number | null;

  // Analyst
  analystCount:             number | null;
  targetPriceMean:          number | null;
  analystRecommendation:    string | null;
}

/** Crypto tokenomics (assetClass=CRYPTO). */
export interface CryptoTokenomicsDto {
  symbol:               string;
  name:                 string;
  description:          string | null;
  blockchains:          string[] | null;
  categories:           string[] | null;
  providerName:         string | null;
  fetchDate:            string | null;

  // Market
  marketCapUsd:             number | null;
  fullyDilutedValuationUsd: number | null;
  volume24hUsd:             number | null;
  marketCapRank:            number | null;

  // Supply
  circulatingSupply:    number | null;
  totalSupply:          number | null;
  maxSupply:            number | null;
  inflationRateYearly:  number | null;

  // On-chain
  activeAddresses24h:   number | null;
  transactions24h:      number | null;
  avgTransactionFee:    number | null;
  hashRate:             number | null;
  stakeRatio:           number | null;

  // Vesting
  teamAllocationPct:    number | null;
  nextUnlockDate:       string | null;
  nextUnlockAmount:     number | null;

  // DeFi
  tvlUsd:                    number | null;
  protocol30dRevenueUsd:     number | null;

  // Dev activity
  githubStars:          number | null;
  githubCommits30d:     number | null;
  githubOpenIssues:     number | null;

  // Sentiment / ATH
  sentimentLabel:       string | null;
  athUsd:               number | null;
  athDate:              string | null;
  athChangePercent:     number | null;
}

/** Forex macro indicators (assetClass=FOREX). */
export interface ForexMacroIndicatorsDto {
  symbol:               string;
  baseCurrency:         string | null;
  quoteCurrency:        string | null;
  country:              string | null;
  providerName:         string | null;
  fetchDate:            string | null;

  // Interest rates
  centralBankRate:           number | null;
  previousCentralBankRate:   number | null;
  lastRateDecisionDate:      string | null;

  // Inflation
  cpiYoy:               number | null;
  coreCpiYoy:           number | null;
  ppiYoy:               number | null;

  // Growth
  gdpGrowthQoq:         number | null;
  gdpGrowthYoy:         number | null;
  unemploymentRate:     number | null;

  // Trade
  tradeBalanceBln:      number | null;
  currentAccountPctGdp: number | null;

  // FX-specific
  interestRateDifferential: number | null;
  spotRate:             number | null;
  weekHigh52:           number | null;
  weekLow52:            number | null;

  // PPP
  pppFairValue:         number | null;
  pppDeviation:         number | null;
}

/** Discriminated union — whichever the backend returns for the given assetClass. */
export type FundamentalsPayload =
  | CompanyFundamentalsDto
  | CryptoTokenomicsDto
  | ForexMacroIndicatorsDto;

// ── Type guards ───────────────────────────────────────────────────────────────

export function isStockFundamentals(p: FundamentalsPayload): p is CompanyFundamentalsDto {
  return 'companyName' in p;
}

export function isCryptoTokenomics(p: FundamentalsPayload): p is CryptoTokenomicsDto {
  return 'marketCapUsd' in p;
}

export function isForexMacro(p: FundamentalsPayload): p is ForexMacroIndicatorsDto {
  return 'centralBankRate' in p;
}
