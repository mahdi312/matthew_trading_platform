import {Component, computed, inject, OnInit, signal,} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {RouterModule} from '@angular/router';
import {MatButtonModule} from '@angular/material/button';
import {MatIconModule} from '@angular/material/icon';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatSelectModule} from '@angular/material/select';
import {MatChipsModule} from '@angular/material/chips';
import {MatTooltipModule} from '@angular/material/tooltip';
import {MatSnackBar, MatSnackBarModule} from '@angular/material/snack-bar';
import {MatDialog, MatDialogModule} from '@angular/material/dialog';

import {AdminApiService} from './admin-api.service';
import {AdminUser, UpdateUserRequest, UserRole, UserStatus} from './admin.models';
import {EmptyStateComponent} from '../../shared/empty-state/empty-state.component';
import {LoadingStateComponent} from '../../shared/loading-state/loading-state.component';
import {ConfirmDialogComponent, ConfirmDialogData} from '../../shared/confirm-dialog/confirm-dialog.component';

type AdminStatusFilter = 'ALL' | UserStatus;

/**
 * AdminModule — straightforward, low-drama user-management table.
 *
 * Accessible ONLY to users with ROLE_ADMIN in their JWT. Route-level access
 * is enforced by `adminGuard` on the `/admin` route in `app.routes.ts`
 * (checked BEFORE this component even loads — verified by navigating
 * directly to /admin as a non-admin user, not just by hiding the nav link).
 *
 * Redesigned per Prompt 13:
 *  1. Username / email / display-name search + a status filter — this
 *     table is expected to grow, so it never assumes it fits on one screen.
 *  2. Role badges rendered as a chip group (a user can hold multiple roles).
 *  3. Status chips use a neutral traffic-light set (brass/teal = active,
 *     muted = pending, purple = suspended) — NOT the bull/bear P&L pair,
 *     since account status is not a gain/loss signal.
 *  4. `lastLoginAt` renders as relative time ("3 days ago") with the exact
 *     timestamp available on hover via the native `title` attribute.
 *  5. Every mutating action (role edit, status change, delete) opens
 *     {@link ConfirmDialogComponent} with copy stating exactly what will
 *     change — never a generic "Are you sure?" — since these affect other
 *     people's accounts.
 *
 * Route: /admin  (behind authGuard + adminGuard)
 */
@Component({
  selector: 'app-admin-page',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatChipsModule,
    MatTooltipModule,
    MatSnackBarModule,
    MatDialogModule,
    EmptyStateComponent,
    LoadingStateComponent,
  ],
  templateUrl: './admin-page.component.html',
  styleUrls: ['./admin-page.component.scss'],
})
export class AdminPageComponent implements OnInit {
  readonly statusFilter = signal<AdminStatusFilter>('ALL');
  private readonly snack = inject(MatSnackBar);
  readonly mutatingId = signal<string | null>(null);

  // ── State ─────────────────────────────────────────────────────────────────

  readonly loading   = signal(true);
  readonly error     = signal<string | null>(null);
  readonly usersRaw  = signal<AdminUser[]>([]);
  readonly search    = signal('');
  /** Whether any search term or status filter is currently narrowing the table. */
  readonly hasActiveFilter = computed(() => !!this.search().trim() || this.statusFilter() !== 'ALL');
  readonly editingId = signal<string | null>(null);
  /** Client-side filtered user list — search + status filter combined. */
  readonly users = computed(() => {
    const q = this.search().toLowerCase().trim();
    const status = this.statusFilter();
    return this.usersRaw().filter((u) => {
      const matchesQuery = !q
        || u.username.toLowerCase().includes(q)
        || u.email.toLowerCase().includes(q)
        || (u.displayName ?? '').toLowerCase().includes(q);
      const matchesStatus = status === 'ALL' || u.status === status;
      return matchesQuery && matchesStatus;
    });
  });
  readonly statusFilterOptions: AdminStatusFilter[] = ['ALL', 'ACTIVE', 'SUSPENDED', 'PENDING'];
  readonly columns = ['username', 'email', 'roles', 'status', 'createdAt', 'lastLoginAt', 'actions'];

  // ── Inline edit state ─────────────────────────────────────────────────────

  editRoles:  UserRole[]   = [];
  editStatus: UserStatus   = 'ACTIVE';

  // ── Options ───────────────────────────────────────────────────────────────

  readonly allRoles:    UserRole[]   = ['ROLE_ADMIN', 'ROLE_MODERATOR', 'ROLE_USER'];
  readonly allStatuses: UserStatus[] = ['ACTIVE', 'SUSPENDED', 'PENDING'];
  private readonly api = inject(AdminApiService);

  // ── Table columns ─────────────────────────────────────────────────────────
  private readonly dialog = inject(MatDialog);

  // ── Lifecycle ─────────────────────────────────────────────────────────────

  ngOnInit(): void {
    this.loadUsers();
  }

  // ── Data loading ──────────────────────────────────────────────────────────

  loadUsers(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.getUsers().subscribe({
      next:  (data) => { this.usersRaw.set(data); this.loading.set(false); },
      error: (err)  => {
        this.error.set('Failed to load users. Please try again.');
        this.loading.set(false);
        console.error('[Admin] load error', err);
      },
    });
  }

  setStatusFilter(status: AdminStatusFilter): void {
    this.statusFilter.set(status);
  }

  clearFilters(): void {
    this.search.set('');
    this.statusFilter.set('ALL');
  }

  // ── Edit (roles + status) ────────────────────────────────────────────────

  startEdit(user: AdminUser): void {
    this.editingId.set(user.id);
    this.editRoles  = [...user.roles];
    this.editStatus = user.status;
  }

  cancelEdit(): void {
    this.editingId.set(null);
  }

  /**
   * Confirms and saves an inline role/status edit. The confirmation text is
   * built from the actual diff (roles changed? status changed? both?) so it
   * always states exactly what will change, never a generic prompt.
   */
  saveEdit(user: AdminUser): void {
    const rolesChanged = !this.sameRoles(user.roles, this.editRoles);
    const statusChanged = user.status !== this.editStatus;

    if (!rolesChanged && !statusChanged) {
      this.editingId.set(null);
      return;
    }

    const parts: string[] = [];
    if (rolesChanged) {
      parts.push(`change their roles to ${this.editRoles.map(r => this.roleLabel(r)).join(', ') || 'none'}`);
    }
    if (statusChanged) {
      parts.push(this.statusChangeConsequence(this.editStatus));
    }

    const data: ConfirmDialogData = {
      title: rolesChanged && statusChanged ? 'Update roles and status' : rolesChanged ? 'Update roles' : 'Update status',
      message: `This will ${parts.join(' and ')} for "${user.username}".`,
      confirmLabel: 'Save changes',
    };

    this.dialog.open(ConfirmDialogComponent, {data}).afterClosed().subscribe((confirmed) => {
      if (!confirmed) return;
      this.applyEdit(user);
    });
  }

  deleteUser(user: AdminUser): void {
    const data: ConfirmDialogData = {
      title: 'Delete user',
      message: `Permanently delete "${user.username}" (${user.email})? Their trades, alerts, and settings will be removed. This cannot be undone.`,
      confirmLabel: 'Delete permanently',
      danger: true,
      icon: 'delete_forever',
    };

    this.dialog.open(ConfirmDialogComponent, {data}).afterClosed().subscribe((confirmed) => {
      if (!confirmed) return;

      this.mutatingId.set(user.id);
      this.api.deleteUser(user.id).subscribe({
        next: () => {
          this.usersRaw.update(list => list.filter(u => u.id !== user.id));
          this.mutatingId.set(null);
          this.snack.open(`User ${user.username} deleted.`, 'OK', {duration: 3000});
        },
        error: (err) => {
          this.mutatingId.set(null);
          this.snack.open('Failed to delete user.', 'Close', {duration: 4000});
          console.error('[Admin] delete error', err);
        },
      });
    });
  }

  /** Neutral traffic-light chip class — deliberately distinct from bull/bear. */
  statusChipClass(status: UserStatus): string {
    switch (status) {
      case 'ACTIVE':
        return 'chip-status-active';
      case 'SUSPENDED':
        return 'chip-status-suspended';
      case 'PENDING':
        return 'chip-status-pending';
    }
  }

  statusIcon(status: UserStatus): string {
    switch (status) {
      case 'ACTIVE':
        return 'check_circle';
      case 'SUSPENDED':
        return 'block';
      case 'PENDING':
        return 'hourglass_empty';
    }
  }

  // ── Delete ────────────────────────────────────────────────────────────────

  /** Relative time for lastLoginAt/createdAt, e.g. "3 days ago" — exact timestamp is the hover title. */
  relativeTime(iso: string | null): string {
    if (!iso) return 'Never';
    const diffMs = Date.now() - new Date(iso).getTime();
    if (diffMs < 0) return 'just now';
    const diffMin = Math.floor(diffMs / 60_000);
    if (diffMin < 1) return 'just now';
    if (diffMin < 60) return `${diffMin} minute${diffMin === 1 ? '' : 's'} ago`;
    const diffH = Math.floor(diffMin / 60);
    if (diffH < 24) return `${diffH} hour${diffH === 1 ? '' : 's'} ago`;
    const diffD = Math.floor(diffH / 24);
    if (diffD < 30) return `${diffD} day${diffD === 1 ? '' : 's'} ago`;
    const diffMo = Math.floor(diffD / 30);
    if (diffMo < 12) return `${diffMo} month${diffMo === 1 ? '' : 's'} ago`;
    const diffY = Math.floor(diffMo / 12);
    return `${diffY} year${diffY === 1 ? '' : 's'} ago`;
  }

  // ── UI helpers ────────────────────────────────────────────────────────────

  isRoleSelected(role: UserRole): boolean {
    return this.editRoles.includes(role);
  }

  toggleEditRole(role: UserRole): void {
    if (this.isRoleSelected(role)) {
      this.editRoles = this.editRoles.filter(r => r !== role);
    } else {
      this.editRoles = [...this.editRoles, role];
    }
  }

  /** Exact timestamp for the `title` hover tooltip. */
  exactTime(iso: string | null): string {
    if (!iso) return 'Never logged in';
    return new Date(iso).toLocaleString('en-US', {
      year: 'numeric', month: 'short', day: 'numeric',
      hour: '2-digit', minute: '2-digit',
    });
  }

  private applyEdit(user: AdminUser): void {
    const req: UpdateUserRequest = {
      roles:  this.editRoles,
      status: this.editStatus,
    };

    this.mutatingId.set(user.id);
    this.api.updateUser(user.id, req).subscribe({
      next: (updated) => {
        this.usersRaw.update(list => list.map(u => u.id === updated.id ? updated : u));
        this.editingId.set(null);
        this.mutatingId.set(null);
        this.snack.open(`User ${updated.username} updated.`, 'OK', { duration: 3000 });
      },
      error: (err) => {
        this.mutatingId.set(null);
        this.snack.open('Failed to update user.', 'Close', { duration: 4000 });
        console.error('[Admin] update error', err);
      },
    });
  }

  roleLabel(r: UserRole): string {
    return r.replace('ROLE_', '');
  }

  /** Human-readable consequence of a status change, per Prompt 13's exact wording. */
  private statusChangeConsequence(next: UserStatus): string {
    switch (next) {
      case 'SUSPENDED':
        return "suspend this user — they'll be signed out and unable to log in until reactivated";
      case 'ACTIVE':
        return 'reactivate this user, restoring their ability to log in';
      case 'PENDING':
        return 'mark this user as pending, revoking active access until approved again';
    }
  }

  private sameRoles(a: UserRole[], b: UserRole[]): boolean {
    if (a.length !== b.length) return false;
    const setA = new Set(a);
    return b.every(r => setA.has(r));
  }

  trackById(_: number, item: { id: string }): string { return item.id; }
}
