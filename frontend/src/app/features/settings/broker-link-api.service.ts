import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AUTH_API } from '../../core/api/api-paths';

/**
 * HTTP client for broker account linking via Gateway → identity-service.
 */
@Injectable({ providedIn: 'root' })
export class BrokerLinkApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.gatewayBaseUrl}${AUTH_API}/brokers`;

  connectBroker(brokerType: string, apiKey: string, apiSecret: string, label?: string): Observable<BrokerLinkResponse> {
    const body: BrokerConnectPayload = { apiKey, apiSecret };
    if (label?.trim()) body.label = label.trim();
    return this.http.post<BrokerLinkResponse>(`${this.base}/${brokerType}/connect`, body);
  }

  listConnections(): Observable<BrokerConnectionStatus[]> {
    return this.http.get<BrokerConnectionStatus[]>(this.base);
  }

  revokeConnection(brokerType: string): Observable<BrokerLinkResponse> {
    return this.http.delete<BrokerLinkResponse>(`${this.base}/${brokerType}`);
  }
}

export interface BrokerConnectPayload {
  apiKey: string;
  apiSecret: string;
  label?: string;
}

export interface BrokerLinkResponse {
  status?: string;
  message?: string;
  brokerType?: string;
  implemented?: boolean;
}

export interface BrokerConnectionStatus {
  brokerType: string;
  label?: string;
  connected: boolean;
  maskedApiKey?: string;
}
