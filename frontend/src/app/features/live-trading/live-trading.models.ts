/**
 * Local models for the LiveTradingModule.
 * The Trade DTO is imported from TradeApiService (shared with JournalModule).
 */

/** BitUnix order types supported at the moment. */
export type OrderSide = 'BUY' | 'SELL';
export type OrderType = 'SPOT' | 'FUTURES';

/** Available brokers — only BitUnix is integrated right now. */
export const AVAILABLE_BROKERS = [
  { id: 'BITUNIX', label: 'BitUnix' },
  // TODO: add more brokers as trading-service exposes them via GET /api/trades/brokers
] as const;

export type BrokerId = (typeof AVAILABLE_BROKERS)[number]['id'];
