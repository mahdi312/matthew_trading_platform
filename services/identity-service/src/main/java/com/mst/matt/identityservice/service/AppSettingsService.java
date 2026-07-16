package com.mst.matt.identityservice.service;

import com.mst.matt.identityservice.model.AppSetting;
import com.mst.matt.identityservice.repository.AppSettingRepository;
import com.mst.matt.identityservice.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Per-user application settings persistence service.
 *
 * <p>Ported from the desktop monolith's {@code AppSettingsService}, which persisted settings
 * in a local {@code .properties} file.  In the microservice environment each user's settings
 * are stored in the {@code app_settings} table (one row per key), making them accessible from
 * any instance and safe for concurrent reads.</p>
 *
 * <p>All public write methods are {@code @Transactional}.  Read methods are {@code readOnly}.
 * The service is stateless — no in-memory volatile fields; every call reads or writes the DB.</p>
 *
 * <h3>Well-known keys</h3>
 * <ul>
 *   <li>{@code api.fetch.enabled} — boolean string</li>
 *   <li>{@code data.fetch.mode} — {@code FULL_ONLINE | OFFLINE_ON_FAIL | OFFLINE_ONLY}</li>
 *   <li>{@code user.timezone} — IANA zone ID string</li>
 *   <li>{@code chart.default.timeframe} — e.g. "1h"</li>
 *   <li>{@code ui.theme} — e.g. "dark", "light", "amazon_green", "light_blue"</li>
 *   <li>{@code chart.favorite.timeframes} — comma-separated</li>
 *   <li>{@code ticker.symbols.disabled} — comma-separated upper-case symbols</li>
 *   <li>{@code ticker.poll.interval.seconds} — integer string</li>
 *   <li>Any other key — arbitrary extension setting</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppSettingsService {

    // ── Well-known key constants ──────────────────────────────────────────────
    public static final String KEY_API_FETCH        = "api.fetch.enabled";
    public static final String KEY_DATA_FETCH_MODE  = "data.fetch.mode";
    public static final String KEY_TIMEZONE         = "user.timezone";
    public static final String KEY_DEFAULT_TF       = "chart.default.timeframe";
    public static final String KEY_THEME            = "ui.theme";
    public static final String KEY_FAV_TIMEFRAMES   = "chart.favorite.timeframes";
    public static final String KEY_TICKER_DISABLED  = "ticker.symbols.disabled";
    public static final String KEY_TICKER_INTERVAL  = "ticker.poll.interval.seconds";

    public static final String KEY_NOTIF_INAPP_ENABLED    = "notification.inApp.enabled";
    public static final String KEY_NOTIF_EMAIL_ENABLED     = "notification.email.enabled";
    public static final String KEY_NOTIF_EMAIL_ADDRESS     = "notification.email.address";
    public static final String KEY_NOTIF_TELEGRAM_ENABLED  = "notification.telegram.enabled";
    public static final String KEY_NOTIF_TELEGRAM_CHAT_ID  = "notification.telegram.chatId";

    private final AppSettingRepository repository;
    private final AppUserRepository appUserRepository;


    // ── Generic read/write ────────────────────────────────────────────────────

    /**
     * Read a setting value for the given user; returns {@code null} if not set.
     */
    @Transactional(readOnly = true)
    public String get(Long userId, String key) {
        return repository.findByAppUserIdAndKey(userId, key)
                .map(AppSetting::getValue)
                .orElse(null);
    }

    /**
     * Read a setting with a fallback default.
     */
    @Transactional(readOnly = true)
    public String get(Long userId, String key, String defaultValue) {
        return repository.findByAppUserIdAndKey(userId, key)
                .map(AppSetting::getValue)
                .filter(v -> v != null && !v.isBlank())
                .orElse(defaultValue);
    }

    /**
     * Write (upsert) a setting for the given user and persist immediately.
     */
    @Transactional
    public void set(Long userId, String key, String value) {
        Optional<AppSetting> existing = repository.findByAppUserIdAndKey(userId, key);
        AppSetting setting = existing.orElseGet(() ->
                AppSetting.builder().appUserId(userId).key(key).build());
        setting.setValue(value);
        repository.save(setting);
        log.debug("AppSettings set userId={} key={}", userId, key);
    }

    /**
     * Returns this user's notification preferences as the cross-service DTO consumed
     * by {@code notification-service}. Email defaults to the account's login email if
     * no override has been set; every "enabled" flag defaults to {@code true} so a
     * user who has never touched notification settings still receives alerts.
     */
    @Transactional(readOnly = true)
    public com.mst.matt.contracts.dto.UserPreferencesDto getNotificationPreferences(Long userId) {
        String accountEmail = appUserRepository.findById(userId)
                .map(com.mst.matt.identityservice.model.AppUser::getEmail)
                .orElse(null);

        boolean inAppEnabled    = Boolean.parseBoolean(get(userId, KEY_NOTIF_INAPP_ENABLED, "true"));
        boolean emailEnabled    = Boolean.parseBoolean(get(userId, KEY_NOTIF_EMAIL_ENABLED, "true"));
        String  email           = get(userId, KEY_NOTIF_EMAIL_ADDRESS, accountEmail);
        boolean telegramEnabled = Boolean.parseBoolean(get(userId, KEY_NOTIF_TELEGRAM_ENABLED, "false"));
        String  telegramChatId  = get(userId, KEY_NOTIF_TELEGRAM_CHAT_ID, null);

        return com.mst.matt.contracts.dto.UserPreferencesDto.builder()
                .userId(userId)
                .inAppEnabled(inAppEnabled)
                .emailEnabled(emailEnabled)
                .email(email)
                .telegramEnabled(telegramEnabled)
                .telegramChatId(telegramChatId)
                .build();
    }

    /** Upserts notification preferences in one call — used by the PUT endpoint. */
    @Transactional
    public void setNotificationPreferences(Long userId, com.mst.matt.contracts.dto.UserPreferencesDto prefs) {
        set(userId, KEY_NOTIF_INAPP_ENABLED,    String.valueOf(prefs.isInAppEnabled()));
        set(userId, KEY_NOTIF_EMAIL_ENABLED,    String.valueOf(prefs.isEmailEnabled()));
        set(userId, KEY_NOTIF_EMAIL_ADDRESS,    prefs.getEmail() != null ? prefs.getEmail() : "");
        set(userId, KEY_NOTIF_TELEGRAM_ENABLED, String.valueOf(prefs.isTelegramEnabled()));
        set(userId, KEY_NOTIF_TELEGRAM_CHAT_ID, prefs.getTelegramChatId() != null ? prefs.getTelegramChatId() : "");
    }

    /**
     * Delete a setting for the given user.
     */
    @Transactional
    public void delete(Long userId, String key) {
        repository.deleteByAppUserIdAndKey(userId, key);
    }

    /**
     * Returns all settings for a user as a key→value map (for bulk read in REST response).
     */
    @Transactional(readOnly = true)
    public Map<String, String> getAll(Long userId) {
        return repository.findByAppUserId(userId).stream()
                .collect(Collectors.toMap(AppSetting::getKey, AppSetting::getValue));
    }

    // ── Typed convenience helpers ─────────────────────────────────────────────

    /** Returns the data-fetch mode string, defaulting to {@code OFFLINE_ON_FAIL}. */
    @Transactional(readOnly = true)
    public String getDataFetchMode(Long userId) {
        return get(userId, KEY_DATA_FETCH_MODE, "OFFLINE_ON_FAIL");
    }

    @Transactional
    public void setDataFetchMode(Long userId, String mode) {
        set(userId, KEY_DATA_FETCH_MODE, mode != null ? mode : "OFFLINE_ON_FAIL");
    }

    /** Returns the stored timezone ID, validated and falling back to UTC. */
    @Transactional(readOnly = true)
    public String getTimezoneId(Long userId) {
        String tz = get(userId, KEY_TIMEZONE);
        if (tz == null || tz.isBlank()) return "UTC";
        try {
            ZoneId.of(tz);  // validate
            return tz;
        } catch (Exception e) {
            return "UTC";
        }
    }

    @Transactional
    public void setTimezoneId(Long userId, String tzId) {
        try {
            ZoneId.of(tzId);  // validate before saving
            set(userId, KEY_TIMEZONE, tzId);
        } catch (Exception e) {
            log.warn("Invalid timezone '{}' for userId={} — ignored", tzId, userId);
        }
    }

    /** Returns the default chart timeframe, defaulting to "1h". */
    @Transactional(readOnly = true)
    public String getDefaultTimeframe(Long userId) {
        return get(userId, KEY_DEFAULT_TF, "1h");
    }

    @Transactional
    public void setDefaultTimeframe(Long userId, String tf) {
        set(userId, KEY_DEFAULT_TF, tf != null ? tf : "1h");
    }

    /** Returns the current theme ID, defaulting to "dark". */
    @Transactional(readOnly = true)
    public String getTheme(Long userId) {
        return get(userId, KEY_THEME, "dark");
    }

    @Transactional
    public void setTheme(Long userId, String theme) {
        set(userId, KEY_THEME, theme != null && !theme.isBlank() ? theme : "dark");
    }

    /** Returns the favorite timeframes CSV, defaulting to empty string. */
    @Transactional(readOnly = true)
    public String getFavoriteTimeframesRaw(Long userId) {
        return get(userId, KEY_FAV_TIMEFRAMES, "");
    }

    @Transactional
    public void setFavoriteTimeframes(Long userId, String csv) {
        set(userId, KEY_FAV_TIMEFRAMES, csv != null ? csv : "");
    }

    /** Returns disabled ticker symbols CSV, defaulting to empty. */
    @Transactional(readOnly = true)
    public String getTickerDisabledSymbols(Long userId) {
        return get(userId, KEY_TICKER_DISABLED, "");
    }

    @Transactional
    public void setTickerDisabledSymbols(Long userId, String csv) {
        set(userId, KEY_TICKER_DISABLED, csv != null ? csv : "");
    }

    /** Returns ticker poll interval in seconds, defaulting to 15. */
    @Transactional(readOnly = true)
    public int getTickerPollIntervalSeconds(Long userId) {
        try {
            return Integer.parseInt(get(userId, KEY_TICKER_INTERVAL, "15"));
        } catch (NumberFormatException e) {
            return 15;
        }
    }

    @Transactional
    public void setTickerPollIntervalSeconds(Long userId, int seconds) {
        int clamped = Math.max(5, Math.min(300, seconds));
        set(userId, KEY_TICKER_INTERVAL, String.valueOf(clamped));
    }
}
