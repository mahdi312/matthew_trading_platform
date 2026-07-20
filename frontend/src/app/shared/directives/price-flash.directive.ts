import { Directive, ElementRef, inject, Input, OnChanges, SimpleChanges } from '@angular/core';

/**
 * PriceFlashDirective
 *
 * Apply `[tpFlash]="value"` to any element displaying a live price or P&L.
 * Whenever the value changes, briefly flashes the element's background
 * bull-green or bear-red depending on the direction of change.
 *
 * Usage:
 *   <span [tpFlash]="livePrice">{{ livePrice | number }}</span>
 *   <td [tpFlash]="pnl" [tpFlashDirection]="'bull'">...</td>
 *
 * The directive honours prefers-reduced-motion via CSS (animation: none).
 */
@Directive({
  selector: '[tpFlash]',
  standalone: true,
})
export class PriceFlashDirective implements OnChanges {
  /** The value to watch — when it changes the flash fires. */
  @Input('tpFlash') value: number | null | undefined;

  /**
   * Force a specific direction ('bull' | 'bear').
   * If omitted, direction is inferred from the sign change.
   */
  @Input('tpFlashDirection') direction?: 'bull' | 'bear';

  private readonly el = inject(ElementRef<HTMLElement>);
  private previousValue: number | null | undefined;

  ngOnChanges(changes: SimpleChanges): void {
    if (!changes['value']) return;
    const prev = changes['value'].previousValue as number | null | undefined;
    const curr = changes['value'].currentValue as number | null | undefined;

    if (prev === undefined || prev === null || curr === undefined || curr === null) {
      this.previousValue = curr;
      return;
    }

    if (curr !== prev) {
      const dir = this.direction ?? (curr >= prev ? 'bull' : 'bear');
      this.flash(dir);
    }

    this.previousValue = curr;
  }

  private flash(direction: 'bull' | 'bear'): void {
    const host = this.el.nativeElement;
    const cls = direction === 'bull' ? 'tp-flash-bull' : 'tp-flash-bear';

    // Remove any active flash class first so re-triggering re-animates
    host.classList.remove('tp-flash-bull', 'tp-flash-bear');

    // Force a reflow so the class removal is painted before re-adding
    void host.offsetWidth;

    host.classList.add(cls);

    // Clean up class after animation completes (~550ms)
    const cleanup = () => host.classList.remove(cls);
    host.addEventListener('animationend', cleanup, { once: true });
    // Safety timeout in case animationend doesn't fire
    setTimeout(() => host.classList.remove(cls), 700);
  }
}
