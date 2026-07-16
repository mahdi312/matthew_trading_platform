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
