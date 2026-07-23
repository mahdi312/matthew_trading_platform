/**
 * Domain models for AdminModule.
 *
 * Mirrors the DTOs from identity-service's AdminController:
 *
 *   GET  /api/admin/users          → AdminUser[]
 *   PUT  /api/admin/users/:id      → AdminUser  (update roles / status)
 *   DELETE /api/admin/users/:id    → void       (delete user)
 */

export type UserRole   = 'ROLE_ADMIN' | 'ROLE_USER' | 'ROLE_MODERATOR';
export type UserStatus = 'ACTIVE' | 'SUSPENDED' | 'PENDING';

export interface AdminUser {
  id:          string;
  username:    string;
  email:       string;
  displayName: string | null;
  roles:       UserRole[];
  status:      UserStatus;
  createdAt:   string;     // ISO-8601
  lastLoginAt: string | null;
}

/** Payload for updating a user's roles or status. */
export interface UpdateUserRequest {
  roles?:  UserRole[];
  status?: UserStatus;
}
