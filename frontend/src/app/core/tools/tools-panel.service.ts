import { Injectable, signal, computed, effect } from '@angular/core';

export type ToolsPanelTab = 'watchlist';

const STORAGE_KEY = 'mtp_tools_panel_open';
const TAB_KEY = 'mtp_tools_panel_tab';

/**
 * Global right-hand tools panel (watchlist, future tools).
 * State persists across routes and browser reloads.
 */
@Injectable({ providedIn: 'root' })
export class ToolsPanelService {
  private readonly _open = signal(this.readOpen());
  private readonly _activeTab = signal<ToolsPanelTab>(this.readTab());

  readonly open = this._open.asReadonly();
  readonly activeTab = this._activeTab.asReadonly();
  readonly isWatchlist = computed(() => this._activeTab() === 'watchlist');

  constructor() {
    effect(() => {
      localStorage.setItem(STORAGE_KEY, this._open() ? '1' : '0');
    });
    effect(() => {
      localStorage.setItem(TAB_KEY, this._activeTab());
    });
  }

  toggle(): void {
    this._open.update((v) => !v);
  }

  openPanel(tab?: ToolsPanelTab): void {
    if (tab) this._activeTab.set(tab);
    this._open.set(true);
  }

  closePanel(): void {
    this._open.set(false);
  }

  setTab(tab: ToolsPanelTab): void {
    this._activeTab.set(tab);
    this._open.set(true);
  }

  private readOpen(): boolean {
    try {
      const v = localStorage.getItem(STORAGE_KEY);
      if (v === null) return true;
      return v === '1';
    } catch {
      return true;
    }
  }

  private readTab(): ToolsPanelTab {
    try {
      const v = localStorage.getItem(TAB_KEY);
      return v === 'watchlist' ? v : 'watchlist';
    } catch {
      return 'watchlist';
    }
  }
}
