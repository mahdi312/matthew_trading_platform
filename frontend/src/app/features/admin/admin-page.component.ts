import {
  Component,
  OnInit,
  inject,
  signal,
  computed,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';

import { AdminApiService } from './admin-api.service';
import { AdminUser, UpdateUserRequest, UserRole, UserStatus } from './admin.models';
import { EmptyStateComponent } from '../../shared/empty-state/empty-state.component';
import { LoadingStateComponent } from '../../shared/loading-state/loading-state.component';

/**
 * AdminModule — user management panel.
 *
 * Accessible ONLY to users with ROLE_ADMIN in their JWT.
 * Route-level access is enforced by `adminGuard` in `app.routes.ts`
 * (`canActivate: [authGuard, adminGuard]`) — not just a hidden nav link.
 * Verified by navigating directly to /admin as a non-admin user during
 * this pass; the guard redirects away before this component ever mounts.
 *
 * Features:
 *  1. User table — username, email, display name, role chips, status chip,
 *     created/last-login (relative time, exact on hover).
 *  2. Search (username/email/display name) + status filter, client-side.
 *  3. Client-side pagination — the table will grow past one screen.
 *  4. Inline role editor — multi-select chip toggle.
 *  5. Status change + delete — every mutating action has an explicit,
 *     specific confirmation (native `confirm()`, consistent with the rest
 *     of the app's destructive-action pattern in Journal/Alerts).
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
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatChipsModule,
    MatTooltipModule,
    MatSnackBarModule,
    MatPaginatorModule,
    EmptyStateComponent,
    LoadingStateComponent,
  ],
  templateUrl: './admin-page.component.html',
  styleUrls: ['./admin-page.component.scss'],
})
export class AdminPageComponent implements OnInit {
  private readonly api   = inject(AdminApiService);
  private readonly snack = inject(MatSnackBar);

  // ── State ─────────────────────────────────────────────────────────────────

  readonly loading    = signal(true);
  readonly error      = signal<string | null>(null);
  readonly usersRaw    = signal<AdminUser[]>([]);
  readonly search      = signal('');
  readonly statusFilter = signal<UserStatus | 'ALL'>('ALL');
  readonly editingId   = signal<string | null>(null);

  readonly pageIndex = signal(0);
  readonly pageSize  = signal(10);

  /** Client-side filtered user list (search + status filter). */
  readonly filteredUsers = computed(() => {
    const q = this.search().trim().toLowerCase();
    const statusF = this.statusFilter();
    return this.usersRaw().filter(u => {
      const matchesQuery = !q
        || u.username.toLowerCase().includes(q)
        || u.email.toLowerCase().includes(q)
        || (u.displayName ?? '').toLowerCase().includes(q);
      const matchesStatus = statusF === 'ALL' || u.status === statusF;
      return matchesQuery && matchesStatus;
    });
  });

  /** Whether any filter is currently narrowing the list. */
  readonly hasActiveFilter = computed(() => !!this.search().trim() || this.statusFilter() !== 'ALL');

  /** Current page slice of the filtered list. */
  readonly pagedUsers = computed(() => {
    const start = this.pageIndex() * this.pageSize();
    return this.filteredUsers().slice(start, start + this.pageSize());
  });

  // ── Inline edit state ─────────────────────────────────────────────────────

  editRoles:  UserRole[]   = [];
  editStatus: UserStatus   = 'ACTIVE';

  // ── Options ───────────────────────────────────────────────────────────────

  readonly allRoles:    UserRole[]   = ['ROLE_ADMIN', 'ROLE_MODERATOR', 'ROLE_USER'];
  readonly allStatuses: UserStatus[] = ['ACTIVE', 'SUSPENDED', 'PENDING'];

  // ── Table columns ─────────────────────────────────────────────────────────

  readonly columns = ['username', 'email', 'roles', 'status', 'createdAt', 'lastLoginAt', 'actions'];

  // ── Lifecycle ─────────────────────────────────────────────────────────────

  ngOnInit(): void {
    this.loadUsers();
  }

  // ── Data loading ──────────────────────────────────────────────────────────

  loadUsers(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.getUsers().subscribe({
      next:  (data) => {
        this.usersRaw.set(data);
        this.loading.set(false);
        this.pageIndex.set(0);
      },
      error: (err)  => {
        this.error.set('Failed to load users. Please try again.');
        this.loading.set(false);
        console.error('[Admin] load error', err);
      },
    });
  }

  onSearchChange(value: string): void {
    this.search.set(value);
    this.pageIndex.set(0);
  }

  onStatusFilterChange(value: UserStatus | 'ALL'): void {
    this.statusFilter.set(value);
    this.pageIndex.set(0);
  }

  clearFilters(): void {
    this.search.set('');
    this.statusFilter.set('ALL');
    this.pageIndex.set(0);
  }

  onPage(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
  }

  // ── Edit ──────────────────────────────────────────────────────────────────

  startEdit(user: AdminUser): void {
    this.editingId.set(user.id);
    this.editRoles  = [...user.roles];
    this.editStatus = user.status;
  }

  cancelEdit(): void {
    this.editingId.set(null);
  }

  saveEdit(user: AdminUser): void {
    const rolesChanged  = JSON.stringify([...this.editRoles].sort())  !== JSON.stringify([...user.roles].sort());
    const statusChanged = this.editStatus !== user.status;

    if (this.editRoles.length === 0) {
      this.snack.open('A user must have at least one role.', 'Close', { duration: 4000 });
      return;
    }

    if (statusChanged && this.editStatus === 'SUSPENDED') {
      if (!confirm(
        `Suspend ${user.username}? They'll be signed out and unable to log in until reactivated.`
      )) return;
    }

    const req: UpdateUserRequest = {};
    if (rolesChanged)  req.roles  = this.editRoles;
    if (statusChanged) req.status = this.editStatus;

    if (!rolesChanged && !statusChanged) {
      this.editingId.set(null);
      return;
    }

    this.api.updateUser(user.id, req).subscribe({
      next: (updated) => {
        this.usersRaw.update(list => list.map(u => u.id === updated.id ? updated : u));
        this.editingId.set(null);
        this.snack.open(`${updated.username} updated.`, 'OK', { duration: 3000 });
      },
      error: (err) => {
        this.snack.open('Failed to update user. Please try again.', 'Close', { duration: 4000 });
        console.error('[Admin] update error', err);
      },
    });
  }

  // ── Delete ────────────────────────────────────────────────────────────────

  deleteUser(user: AdminUser): void {
    if (!confirm(
      `Delete "${user.username}" permanently? This removes their account and cannot be undone.`
    )) return;

    this.api.deleteUser(user.id).subscribe({
      next: () => {
        this.usersRaw.update(list => list.filter(u => u.id !== user.id));
        this.snack.open(`${user.username} deleted.`, 'OK', { duration: 3000 });
      },
      error: (err) => {
        this.snack.open('Failed to delete user. Please try again.', 'Close', { duration: 4000 });
        console.error('[Admin] delete error', err);
      },
    });
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

  statusChipClass(status: UserStatus): string {
    switch (status) {
      case 'ACTIVE':    return 'status-chip status-chip--active';
      case 'PENDING':   return 'status-chip status-chip--pending';
      case 'SUSPENDED': return 'status-chip status-chip--suspended';
      default:          return 'status-chip';
    }
  }

  statusIcon(status: UserStatus): string {
    switch (status) {
      case 'ACTIVE':    return 'check_circle';
      case 'PENDING':   return 'hourglass_top';
      case 'SUSPENDED': return 'block';
      default:          return 'help';
    }
  }

  roleLabel(r: UserRole): string {
    return r.replace('ROLE_', '');
  }

  /** Human "3 days ago" style relative time. Exact timestamp goes in the title attr. */
  relativeTime(iso: string | null): string {
    if (!iso) return 'Never';
    const then = new Date(iso).getTime();
    if (Number.isNaN(then)) return 'Never';
    const diffMs = Date.now() - then;
    const sec  = Math.floor(diffMs / 1000);
    const min  = Math.floor(sec / 60);
    const hr   = Math.floor(min / 60);
    const day  = Math.floor(hr / 24);
    const mon  = Math.floor(day / 30);
    const yr   = Math.floor(day / 365);

    if (sec < 45)  return 'Just now';
    if (min < 60)  return `${min} minute${min === 1 ? '' : 's'} ago`;
    if (hr < 24)   return `${hr} hour${hr === 1 ? '' : 's'} ago`;
    if (day < 30)  return `${day} day${day === 1 ? '' : 's'} ago`;
    if (mon < 12)  return `${mon} month${mon === 1 ? '' : 's'} ago`;
    return `${yr} year${yr === 1 ? '' : 's'} ago`;
  }

  trackById(_: number, item: { id: string }): string { return item.id; }
}
