import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

/**
 * HTTP client for broker account linking.
 *
 * Wraps identity-service's BrokerLinkController:
 *
 *   POST   /auth/brokers/{brokerType}/connect  — submit API key + secret
 *   GET    /auth/brokers                        — list linked brokers
 *   DELETE /auth/brokers/{brokerType}           — revoke a connection
 *
 * NOTE: The backend is currently stubbed (returns 501); the UI is intentionally
 * written to handle both 501 "NOT_IMPLEMENTED" and future real responses
 * gracefully so it is drop-in-ready when the backend is fully implemented.
 *
 * Security contract:
 *  - apiSecret is NEVER logged, printed, or stored client-side after submission.
 *  - The form field for apiSecret uses type="password" regardless of what the
 *    backend expects — the value must be treated as a secret.
 */
@Injectable({ providedIn: 'root' })
export class BrokerLinkApiService {
  private readonly http = inject(HttpClient);
  /** identity-service is reached through the gateway, same base URL. */
  private readonly base = environment.gatewayBaseUrl;

  /**
   * Submit API key + secret for a specific broker.
   * POST /auth/brokers/{brokerType}/connect
   */
  connectBroker(brokerType: string, apiKey: string, apiSecret: string, label?: string): Observable<BrokerLinkResponse> {
    const body: BrokerConnectPayload = { apiKey, apiSecret };
    if (label?.trim()) body.label = label.trim();
    return this.http.post<BrokerLinkResponse>(
      `${this.base}/auth/brokers/${brokerType}/connect`,
      body
    );
  }

  /**
   * List all broker connections for the current user.
   * GET /auth/brokers
   */
  listConnections(): Observable<BrokerConnectionStatus[]> {
    return this.http.get<BrokerConnectionStatus[]>(`${this.base}/auth/brokers`);
  }

  /**
   * Revoke a specific broker connection.
   * DELETE /auth/brokers/{brokerType}
   */
  revokeConnection(brokerType: string): Observable<BrokerLinkResponse> {
    return this.http.delete<BrokerLinkResponse>(`${this.base}/auth/brokers/${brokerType}`);
  }
}

// ── Payload / response shapes ─────────────────────────────────────────────────

/** Sent in the POST body — mirrors BrokerConnectRequest on the backend. */
export interface BrokerConnectPayload {
  apiKey:    string;
  apiSecret: string;
  label?:    string;
}

/**
 * Generic broker link/revoke response.
 * When the backend is stubbed the shape is { status, broker?, message }.
 * When fully implemented it will carry a richer object — keep consumption loose.
 */
export interface BrokerLinkResponse {
  status:   string;
  broker?:  string;
  message?: string;
}

/**
 * Per-broker connection status returned by GET /auth/brokers.
 * Shape will be defined by the full implementation; kept flexible for now.
 */
export interface BrokerConnectionStatus {
  brokerType: string;
  label?:     string;
  connected:  boolean;
  linkedAt?:  string; // ISO-8601
}
