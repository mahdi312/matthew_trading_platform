import { Injectable, OnDestroy, signal } from '@angular/core';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { Subject } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthSessionService } from '../auth/auth-session.service';
import { AlertTriggeredEvent } from './models/alert-triggered-event.model';

/**
 * Global, app-wide connection to `alert-service`'s in-app notification
 * channel (Step 4.75's `InAppNotificationChannel`).
 *
 * <h3>Transport</h3>
 * Connects through the API Gateway — never directly to `alert-service` —
 * at `{gatewayBaseUrl}/ws` (SockJS, with WebSocket upgrade), matching:
 *   - `WebSocketConfig` in `alert-service` (STOMP endpoint registered at `/ws`)
 *   - the Gateway route `Path=/alerts/**,/ws/**` -> `lb://alert-service`
 *     (infra/gateway-service/src/main/resources/application.yml)
 *
 * <h3>Auth</h3>
 * The JWT is sent as a STOMP CONNECT header (`Authorization: Bearer ...`).
 * `GatewayJwtAuthFilter` runs before the route is matched for the initial
 * SockJS HTTP handshake, so an invalid/missing token simply fails the
 * handshake — this service degrades silently (no toast/badge updates)
 * rather than throwing, since the notification bell must never block the
 * rest of the app from loading.
 *
 * <h3>Subscription topic</h3>
 * Subscribes to `/topic/alerts/{userId}` where `userId` is decoded from
 * the JWT's `userId` claim (see `AuthSessionService`) — this is the exact
 * destination `InAppNotificationChannel.send()` broadcasts to.
 *
 * This service intentionally has no knowledge of alert CRUD — that is
 * Step 13's full `AlertsModule`. It only relays fired-alert events for
 * the global bell/toast UI.
 */
@Injectable({ providedIn: 'root' })
export class NotificationService implements OnDestroy {
  /** Emits every AlertTriggeredEvent received while connected. */
  readonly events$ = new Subject<AlertTriggeredEvent>();

  /** Reactive connection state, for the bell component to show a subtle indicator if desired. */
  readonly connected = signal(false);

  /** Running count of alerts fired since the badge was last cleared. */
  readonly unreadCount = signal(0);

  private client: Client | null = null;
  private subscription: StompSubscription | null = null;
  private subscribedUserId: string | null = null;

  constructor(private readonly authSession: AuthSessionService) {}

  /**
   * Establishes the STOMP connection (idempotent — safe to call from
   * multiple components; a second call while already connected/connecting
   * for the same user is a no-op). Call once, near app bootstrap (see
   * `AppComponent`).
   */
  connect(): void {
    const userId = this.authSession.getCurrentUserId();
    if (!userId) {
      // No authenticated session yet (login flow lands in Step 13/14) —
      // nothing to subscribe to. The bell stays silently disconnected.
      return;
    }
    if (this.client?.active && this.subscribedUserId === userId) {
      return;
    }

    this.disconnect();
    this.subscribedUserId = userId;

    const wsBaseUrl = `${environment.gatewayBaseUrl}/ws`;
    const token = this.authSession.getToken();

    this.client = new Client({
      webSocketFactory: () => new SockJS(wsBaseUrl) as unknown as WebSocket,
      connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        this.connected.set(true);
        this.subscription = this.client!.subscribe(
          `/topic/alerts/${userId}`,
          (message: IMessage) => this.handleMessage(message)
        );
      },
      onDisconnect: () => this.connected.set(false),
      onWebSocketClose: () => this.connected.set(false),
      onStompError: () => this.connected.set(false),
    });

    this.client.activate();
  }

  /** Tears down the STOMP connection. Safe to call even if never connected. */
  disconnect(): void {
    this.subscription?.unsubscribe();
    this.subscription = null;
    this.client?.deactivate();
    this.client = null;
    this.connected.set(false);
  }

  /** Resets the unread badge count (call when the user opens the bell dropdown). */
  clearUnread(): void {
    this.unreadCount.set(0);
  }

  private handleMessage(message: IMessage): void {
    try {
      const event = JSON.parse(message.body) as AlertTriggeredEvent;
      this.unreadCount.update((count) => count + 1);
      this.events$.next(event);
    } catch {
      // Malformed payload — ignore rather than crash the socket handler.
    }
  }

  ngOnDestroy(): void {
    this.disconnect();
  }
}
