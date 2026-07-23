/**
 * Domain models for SettingsModule.
 *
 * Mirrors the DTOs from identity-service's ProfileController:
 *
 *   GET  /api/profile              → UserProfile
 *   PUT  /api/profile              → UserProfile
 *   GET  /api/profile/preferences  → NotificationPreferences
 *   PUT  /api/profile/preferences  → NotificationPreferences
 */

export interface UserProfile {
  id:           string;
  username:     string;
  email:        string;
  displayName:  string | null;
  avatarUrl:    string | null;
  timezone:     string | null;
  currency:     string | null;      // ISO 4217, e.g. "USD"
  createdAt:    string;             // ISO-8601
}

/** Subset of fields the user can update — id/email/createdAt are read-only. */
export type UpdateProfileRequest = Partial<
  Pick<UserProfile, 'displayName' | 'avatarUrl' | 'timezone' | 'currency'>
>;

/**
 * Cross-service notification preferences — mirrors
 * {@code shared/contracts/.../UserPreferencesDto.java} exactly.
 */
export interface NotificationPreferences {
  userId:           number | null;
  inAppEnabled:     boolean;
  emailEnabled:     boolean;
  email:            string | null;
  telegramEnabled:  boolean;
  telegramChatId:   string | null;
}

/** Body for PUT /api/profile/preferences (userId is assigned server-side). */
export type UpdateNotificationPreferencesRequest = Omit<NotificationPreferences, 'userId'>;
