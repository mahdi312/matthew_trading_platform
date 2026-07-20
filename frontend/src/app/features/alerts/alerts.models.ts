/**
 * Domain models for AlertsModule.
 *
 * These mirror the DTOs that alert-service's AlertController produces/consumes:
 *   GET    /api/alerts          → PriceAlert[]
 *   POST   /api/alerts          → PriceAlert
 *   PUT    /api/alerts/:id      → PriceAlert
 *   DELETE /api/alerts/:id      → void
 *
 * AlertStatus and AlertCondition match the enum values the backend uses.
 */

export type AlertCondition = 'ABOVE' | 'BELOW' | 'CROSSES_ABOVE' | 'CROSSES_BELOW';
export type AlertStatus     = 'ACTIVE' | 'FIRED' | 'DISABLED';

export interface PriceAlert {
  id:          string;
  userId:      string;
  symbol:      string;
  condition:   AlertCondition;
  targetValue: number;
  status:      AlertStatus;
  message:     string | null;
  createdAt:   string;   // ISO-8601
  firedAt:     string | null;
}

/** Payload for creating or updating an alert (id/userId/createdAt omitted). */
export interface SaveAlertRequest {
  symbol:      string;
  condition:   AlertCondition;
  targetValue: number;
  message:     string | null;
}

/**
 * Plain-English labels for each condition, used both in the selector and in
 * the live sentence preview — never show the enum verbatim to the user.
 */
export const CONDITION_OPTIONS: { value: AlertCondition; label: string; phrase: string }[] = [
  { value: 'ABOVE',         label: 'Price rises above',  phrase: 'rises above' },
  { value: 'BELOW',         label: 'Price falls below',  phrase: 'falls below' },
  { value: 'CROSSES_ABOVE', label: 'Crosses above',      phrase: 'crosses above' },
  { value: 'CROSSES_BELOW', label: 'Crosses below',      phrase: 'crosses below' },
];

/** Bullish conditions — a fired alert on one of these is a "bullish" trigger. */
export const BULLISH_CONDITIONS: AlertCondition[] = ['ABOVE', 'CROSSES_ABOVE'];
