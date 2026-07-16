import { Component, OnDestroy, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subscription } from 'rxjs';
import { NotificationService } from '../../core/notification/notification.service';
import { AlertTriggeredEvent } from '../../core/notification/models/alert-triggered-event.model';

/** A single toast rendered on screen, auto-dismissed after {@link TOAST_LIFETIME_MS}. */
interface Toast {
  id: string;
  headline: string;
  detail: string;
}

/**
 * Lightweight, global notification bell + toast component (Step 4.75,
 * item 3 of the migration guide).
 *
 * <p>This is intentionally NOT the full `AlertsModule` feature (that's
 * Step 13) — it only:</p>
 * <ul>
 *   <li>shows a badge with the count of unread fired alerts;</li>
 *   <li>pops a short-lived toast for each `AlertTriggeredEvent` received
 *       over the in-app WebSocket channel (`NotificationService`);</li>
 *   <li>lets the user open a small dropdown listing recent fired alerts
 *       and clear the unread badge.</li>
 * </ul>
 *
 * <p>Intended usage: mount once, globally, in `AppComponent`'s template
 * (e.g., in a persistent header/toolbar) — see `app.component.html`.</p>
 */
@Component({
  selector: 'app-notification-bell',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './notification-bell.component.html',
  styleUrl: './notification-bell.component.scss',
})
export class NotificationBellComponent implements OnInit, OnDestroy {
  private static readonly TOAST_LIFETIME_MS = 6000;
  private static readonly MAX_RECENT = 20;

  readonly isOpen = signal(false);
  readonly toasts = signal<Toast[]>([]);
  readonly recent = signal<AlertTriggeredEvent[]>([]);

  private subscription: Subscription | null = null;
  private toastTimers = new Map<string, ReturnType<typeof setTimeout>>();

  constructor(readonly notificationService: NotificationService) {}

  ngOnInit(): void {
    // Establish (or reuse) the global WebSocket connection. Safe to call
    // even if the connection was already opened elsewhere.
    this.notificationService.connect();

    this.subscription = this.notificationService.events$.subscribe((event) =>
      this.onAlertTriggered(event)
    );
  }

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
    this.toastTimers.forEach((timer) => clearTimeout(timer));
    this.toastTimers.clear();
    // Intentionally do NOT call notificationService.disconnect() here —
    // this is a global, app-wide connection shared by other consumers
    // (e.g., a future full AlertsModule in Step 13); it should stay alive
    // for the lifetime of the app, not just this component.
  }

  toggleOpen(): void {
    this.isOpen.update((open) => !open);
    if (this.isOpen()) {
      this.notificationService.clearUnread();
    }
  }

  dismissToast(id: string): void {
    this.toasts.update((list) => list.filter((t) => t.id !== id));
    const timer = this.toastTimers.get(id);
    if (timer) {
      clearTimeout(timer);
      this.toastTimers.delete(id);
    }
  }

  private onAlertTriggered(event: AlertTriggeredEvent): void {
    this.recent.update((list) => [event, ...list].slice(0, NotificationBellComponent.MAX_RECENT));

    const toast: Toast = {
      id: event.eventId,
      headline: `${event.symbol} ${this.conditionLabel(event.condition)}`,
      detail: event.message?.trim()
        ? event.message
        : `Observed ${event.observedValue} vs target ${event.targetValue}`,
    };
    this.toasts.update((list) => [...list, toast]);

    const timer = setTimeout(
      () => this.dismissToast(toast.id),
      NotificationBellComponent.TOAST_LIFETIME_MS
    );
    this.toastTimers.set(toast.id, timer);
  }

  conditionLabel(condition: AlertTriggeredEvent['condition']): string {
    switch (condition) {
      case 'ABOVE':
        return 'rose above target';
      case 'BELOW':
        return 'fell below target';
      case 'PERCENT_CHANGE':
        return 'moved past target %';
      default:
        return 'alert triggered';
    }
  }
}
