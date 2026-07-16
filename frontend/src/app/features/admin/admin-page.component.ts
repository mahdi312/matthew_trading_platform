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
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDividerModule } from '@angular/material/divider';
import { MatBadgeModule } from '@angular/material/badge';

import { AdminApiService } from './admin-api.service';
import { AdminUser, UpdateUserRequest, UserRole, UserStatus } from './admin.models';

/**
 * AdminModule — user management panel.
 *
 * Accessible ONLY to users with ROLE_ADMIN in their JWT.
 * Route-level access is enforced by `adminGuard` (not just a hidden nav link).
 *
 * Features:
 *  1. User list table with username, email, roles, status, last login.
 *  2. Client-side search filter (username / email).
 *  3. Inline role editor — multi-select chip list to change roles.
 *  4. Status toggle (ACTIVE ↔ SUSPENDED).
 *  5. Delete user with confirmation.
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
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    MatChipsModule,
    MatTooltipModule,
    MatSnackBarModule,
    MatDividerModule,
    MatBadgeModule,
  ],
  templateUrl: './admin-page.component.html',
  styleUrls: ['./admin-page.component.scss'],
})
export class AdminPageComponent implements OnInit {
  private readonly api   = inject(AdminApiService);
  private readonly snack = inject(MatSnackBar);

  // ── State ─────────────────────────────────────────────────────────────────

  readonly loading   = signal(true);
  readonly error     = signal<string | null>(null);
  readonly usersRaw  = signal<AdminUser[]>([]);
  readonly search    = signal('');
  readonly editingId = signal<string | null>(null);

  /** Client-side filtered user list */
  readonly users = computed(() => {
    const q = this.search().toLowerCase();
    if (!q) return this.usersRaw();
    return this.usersRaw().filter(u =>
      u.username.toLowerCase().includes(q) ||
      u.email.toLowerCase().includes(q) ||
      (u.displayName ?? '').toLowerCase().includes(q)
    );
  });

  // ── Inline edit state ─────────────────────────────────────────────────────

  editRoles:  UserRole[]   = [];
  editStatus: UserStatus   = 'ACTIVE';

  // ── Options ───────────────────────────────────────────────────────────────

  readonly allRoles:    UserRole[]   = ['ROLE_ADMIN', 'ROLE_MODERATOR', 'ROLE_USER'];
  readonly allStatuses: UserStatus[] = ['ACTIVE', 'SUSPENDED', 'PENDING'];

  // ── Table columns ─────────────────────────────────────────────────────────

  readonly columns = ['username', 'email', 'roles', 'status', 'lastLoginAt', 'actions'];

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
        this.error.set('Failed to load users.');
        this.loading.set(false);
        console.error('[Admin] load error', err);
      },
    });
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
    const req: UpdateUserRequest = {
      roles:  this.editRoles,
      status: this.editStatus,
    };

    this.api.updateUser(user.id, req).subscribe({
      next: (updated) => {
        this.usersRaw.update(list => list.map(u => u.id === updated.id ? updated : u));
        this.editingId.set(null);
        this.snack.open(`User ${updated.username} updated.`, 'OK', { duration: 3000 });
      },
      error: (err) => {
        this.snack.open('Failed to update user.', 'Close', { duration: 4000 });
        console.error('[Admin] update error', err);
      },
    });
  }

  // ── Delete ────────────────────────────────────────────────────────────────

  deleteUser(user: AdminUser): void {
    if (!confirm(`Delete user "${user.username}" permanently?`)) return;

    this.api.deleteUser(user.id).subscribe({
      next: () => {
        this.usersRaw.update(list => list.filter(u => u.id !== user.id));
        this.snack.open(`User ${user.username} deleted.`, 'OK', { duration: 3000 });
      },
      error: (err) => {
        this.snack.open('Failed to delete user.', 'Close', { duration: 4000 });
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

  statusColor(status: UserStatus): string {
    switch (status) {
      case 'ACTIVE':    return 'primary';
      case 'SUSPENDED': return 'warn';
      default:          return '';
    }
  }

  roleLabel(r: UserRole): string {
    return r.replace('ROLE_', '');
  }

  trackById(_: number, item: { id: string }): string { return item.id; }
}
