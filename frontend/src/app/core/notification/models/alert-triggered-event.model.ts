/**
 * Mirrors `com.mst.matt.contracts.dto.AlertTriggeredEventDto`
 * (shared/contracts) — the payload `alert-service`'s
 * `InAppNotificationChannel` broadcasts to `/topic/alerts/{userId}` via
 * STOMP. Field names/casing match the DTO's Jackson (camelCase) JSON
 * serialization exactly; enum values are the Java enum constant names.
 */
export type AlertConditionType = 'ABOVE' | 'BELOW' | 'PERCENT_CHANGE';

export type AssetClassType = 'STOCK' | 'CRYPTO' | 'FOREX' | 'NFT';

export type BrokerTypeType =
  | 'BITUNIX'
  | 'BINANCE'
  | 'COINBASE'
  | 'KRAKEN'
  | 'ALPACA'
  | 'PAPER';

export interface AlertTriggeredEvent {
  eventId: string;
  alertId: number;
  userId: number;
  symbol: string;
  assetClass: AssetClassType;
  brokerType: BrokerTypeType | null;
  condition: AlertConditionType;
  targetValue: number;
  observedValue: number;
  message: string | null;
  /** ISO-8601 instant string (Jackson's default Instant serialization). */
  triggeredAt: string;
}
