/**
 * Domain models for OnChainModule.
 *
 * These mirror the DTOs that reference-data-service's endpoints produce:
 *
 *   GET /api/reference/nft/collections   → NftCollection[]
 *   GET /api/reference/defi/pools        → DefiPool[]
 *
 * Pagination / filtering params may be added as query params by the API service.
 */

/** Represents a single NFT collection. */
export interface NftCollection {
  id:           string;
  name:         string;
  symbol:       string;
  contractAddress: string;
  chain:        string;              // e.g. "Ethereum", "Polygon"
  floorPrice:   number | null;       // in ETH or native token
  volume24h:    number | null;
  itemCount:    number | null;
  ownerCount:   number | null;
  imageUrl:     string | null;
  marketCap:    number | null;
}

/** Represents a single DeFi liquidity pool. */
export interface DefiPool {
  id:           string;
  protocol:     string;              // e.g. "Uniswap V3"
  chain:        string;
  token0Symbol: string;
  token1Symbol: string;
  feeTier:      number | null;       // basis points, e.g. 30 = 0.3%
  tvl:          number | null;       // Total Value Locked in USD
  volume24h:    number | null;       // 24h volume in USD
  apy:          number | null;       // annualised yield, 0–1 float
  poolAddress:  string;
}
