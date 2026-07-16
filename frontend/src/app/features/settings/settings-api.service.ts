import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PROFILE_API, PROFILE_PREFERENCES_API } from '../../core/api/api-paths';
import {
  NotificationPreferences,
  UpdateNotificationPreferencesRequest,
  UpdateProfileRequest,
  UserProfile,
} from './settings.models';

/**
 * HTTP client for SettingsModule — wraps identity-service profile endpoints.
 *
 * Endpoints:
 *   GET /api/profile              → UserProfile
 *   PUT /api/profile              → UserProfile
 *   GET /api/profile/preferences  → NotificationPreferences
 *   PUT /api/profile/preferences  → NotificationPreferences
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

  getNotificationPreferences(): Observable<NotificationPreferences> {
    return this.http.get<NotificationPreferences>(`${this.base}${PROFILE_PREFERENCES_API}`);
  }

  updateNotificationPreferences(
    req: UpdateNotificationPreferencesRequest,
  ): Observable<NotificationPreferences> {
    return this.http.put<NotificationPreferences>(`${this.base}${PROFILE_PREFERENCES_API}`, req);
  }
}
