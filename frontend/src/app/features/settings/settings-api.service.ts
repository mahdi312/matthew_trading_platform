import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PROFILE_API } from '../../core/api/api-paths';
import { UserProfile, UpdateProfileRequest } from './settings.models';

/**
 * HTTP client for SettingsModule — wraps identity-service's profile endpoint.
 *
 * Endpoints:
 *   GET /api/profile  → UserProfile
 *   PUT /api/profile  → UserProfile
 */
@Injectable({ providedIn: 'root' })
export class SettingsApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.gatewayBaseUrl;

  getProfile(): Observable<UserProfile> {
    return this.http.get<UserProfile>(`${this.base}${PROFILE_API}`);
  }

  updateProfile(req: UpdateProfileRequest): Observable<UserProfile> {
    return this.http.put<UserProfile>(`${this.base}${PROFILE_API}`, req);
  }
}
