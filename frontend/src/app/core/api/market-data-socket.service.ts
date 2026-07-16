import { Injectable, OnDestroy, inject } from '@angular/core';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { Subject, Observable } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { AuthSessionService } from '../auth/auth-session.service';
import { WS_ENDPOINT } from './api-paths';

/**
 * Tick payload emitted by market-service's MarketStreamController.
 *
 * The topic naming convention matches what the backend publishes:
 *   /topic/market/{symbol}
 *
 * Each message carries the latest trade price so consumers can update
 * CandlestickChartComponent's `livePrice` input.
 */
export interface MarketTick {
  symbol: string;
  price: number;
  volume: number;
  /** ISO-8601 timestamp from the exchange */
  timestamp: string;
}

/**
 * STOMP/SockJS client for market-service live price feeds.
 *
 * Pattern mirrors NotificationService exactly — uses the same Gateway
 * WebSocket endpoint (`{gatewayBaseUrl}/ws`) with `Authorization: Bearer`
 * as a STOMP connect header, and subscribes to per-symbol topics.
 *
 * ## Usage (from a feature component/service):
 * ```ts
 * constructor(private mds: MarketDataSocketService) {}
 *
 * ngOnInit() {
 *   this.mds.connect();
 *   this.mds.ticks$('BTCUSDT').subscribe(tick => this.livePrice.set(tick.price));
 * }
 *
 * ngOnDestroy() {
 *   this.mds.unsubscribeSymbol('BTCUSDT');
 * }
 * ```
 *
 * The service is *not* responsible for unsubscribing on route change —
 * the consuming module must call `unsubscribeSymbol` or `disconnect`
 * in `ngOnDestroy`.
 */
@Injectable({ providedIn: 'root' })
export class MarketDataSocketService implements OnDestroy {
  private readonly authSession = inject(AuthSessionService);

  /** All raw tick messages land here; `ticks$()` filters by symbol. */
  private readonly tickSubject = new Subject<MarketTick>();

  private client: Client | null = null;
  /** symbol → StompSubscription map — allows per-symbol unsubscribe. */
  private readonly subscriptions = new Map<string, StompSubscription>();

  // ── Public API ────────────────────────────────────────────────────────────

  /**
   * Observable of live ticks for a single symbol.
   * Emits only after `subscribe(symbol)` is called.
   */
  ticks$(symbol: string): Observable<MarketTick> {
    return this.tickSubject.asObservable().pipe(
      filter((t) => t.symbol === symbol)
    );
  }

  /**
   * Establishes the STOMP connection (idempotent).
   * Must be called before `subscribeSymbol`.
   */
  connect(): void {
    if (this.client?.active) return;

    const wsUrl = `${environment.gatewayBaseUrl}${WS_ENDPOINT}`;
    const token = this.authSession.getToken();

    this.client = new Client({
      webSocketFactory: () => new SockJS(wsUrl) as unknown as WebSocket,
      connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        // Re-subscribe to all symbols if reconnected.
        for (const symbol of this.subscriptions.keys()) {
          this.doSubscribe(symbol);
        }
      },
      onStompError: (frame) => {
        console.warn('[MarketDataSocket] STOMP error', frame.headers?.['message']);
      },
    });

    this.client.activate();
  }

  /**
   * Subscribes to live ticks for `symbol`.
   * Calls `connect()` if not already connected.
   */
  subscribeSymbol(symbol: string): void {
    if (!this.client?.active) {
      this.connect();
      // doSubscribe will be called in onConnect callback.
      // We still register the symbol so reconnect knows about it.
      this.subscriptions.set(symbol, null as unknown as StompSubscription);
      return;
    }
    if (!this.subscriptions.has(symbol)) {
      this.doSubscribe(symbol);
    }
  }

  /** Cancels the STOMP subscription for `symbol`. */
  unsubscribeSymbol(symbol: string): void {
    const sub = this.subscriptions.get(symbol);
    sub?.unsubscribe();
    this.subscriptions.delete(symbol);
  }

  /** Tears down the entire STOMP connection. */
  disconnect(): void {
    for (const sub of this.subscriptions.values()) {
      sub?.unsubscribe();
    }
    this.subscriptions.clear();
    this.client?.deactivate();
    this.client = null;
  }

  ngOnDestroy(): void {
    this.disconnect();
  }

  // ── Private ──────────────────────────────────────────────────────────────

  private doSubscribe(symbol: string): void {
    if (!this.client) return;
    const sub = this.client.subscribe(
      `/topic/market/${symbol}`,
      (msg: IMessage) => {
        try {
          const tick = JSON.parse(msg.body) as MarketTick;
          this.tickSubject.next(tick);
        } catch {
          // Malformed payload — ignore.
        }
      }
    );
    this.subscriptions.set(symbol, sub);
  }
}
