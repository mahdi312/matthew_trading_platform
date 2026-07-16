import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ADMIN_API } from '../../core/api/api-paths';
import { AdminUser, UpdateUserRequest } from './admin.models';

/**
 * HTTP client for AdminModule — wraps identity-service's admin endpoints.
 *
 * Endpoints:
 *   GET    /api/admin/users       → AdminUser[]
 *   PUT    /api/admin/users/:id   → AdminUser
 *   DELETE /api/admin/users/:id   → void
 *
 * Route-level access is enforced by `adminGuard` (ROLE_ADMIN check).
 * The Gateway also enforces role-based access server-side.
 */
@Injectable({ providedIn: 'root' })
export class AdminApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.gatewayBaseUrl;

  getUsers(): Observable<AdminUser[]> {
    return this.http.get<AdminUser[]>(`${this.base}${ADMIN_API}/users`);
  }

  updateUser(id: string, req: UpdateUserRequest): Observable<AdminUser> {
    return this.http.put<AdminUser>(`${this.base}${ADMIN_API}/users/${id}`, req);
  }

  deleteUser(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}${ADMIN_API}/users/${id}`);
  }
}
