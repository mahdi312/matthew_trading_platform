/**
 * Domain models for SettingsModule.
 *
 * Mirrors the DTOs from identity-service's ProfileController:
 *
 *   GET  /api/profile   → UserProfile
 *   PUT  /api/profile   → UserProfile
 */

export interface UserProfile {
  id:           string;
  username:     string;
  email:        string;
  displayName:  string | null;
  avatarUrl:    string | null;
  timezone:     string | null;
  currency:     string | null;      // ISO 4217, e.g. "USD"
  notificationsEnabled: boolean;
  createdAt:    string;             // ISO-8601
}

/** Subset of fields the user can update — id/email/createdAt are read-only. */
export type UpdateProfileRequest = Partial<
  Pick<UserProfile, 'displayName' | 'avatarUrl' | 'timezone' | 'currency' | 'notificationsEnabled'>
>;
