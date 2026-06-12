package com.mst.matt.tradingplatformapp.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;

/**
 * User preferences persisted under {@code ~/.trading-platform/app-settings.properties}.
 * Controls offline mode, timezone override, default timeframe, theme, and favorite timeframes.
 */
@Service
public class AppSettingsService {

    private static final Logger log = LoggerFactory.getLogger(AppSettingsService.class);
    private static final String KEY_API_FETCH        = "api.fetch.enabled";
    private static final String KEY_TIMEZONE         = "user.timezone";
    private static final String KEY_DEFAULT_TF       = "chart.default.timeframe";
    private static final String KEY_THEME            = "ui.theme";
    private static final String KEY_FAV_TIMEFRAMES   = "chart.favorite.timeframes";

    private final Path settingsFile =
            Path.of(System.getProperty("user.home"), ".trading-platform", "app-settings.properties");

    private volatile boolean apiFetchEnabled   = true;
    private volatile String  timezoneId        = ZoneId.systemDefault().getId();
    private volatile String  defaultTimeframe  = "1h";
    private volatile String  theme             = "dark";
    private volatile String  favoriteTimeframes = "";

    @PostConstruct
    void load() {
        if (!Files.exists(settingsFile)) {
            return;
        }
        var props = new java.util.Properties();
        try (InputStream in = Files.newInputStream(settingsFile)) {
            props.load(in);
            apiFetchEnabled  = Boolean.parseBoolean(props.getProperty(KEY_API_FETCH, "true"));
            timezoneId       = props.getProperty(KEY_TIMEZONE, ZoneId.systemDefault().getId());
            defaultTimeframe = props.getProperty(KEY_DEFAULT_TF, "1h");
            theme            = props.getProperty(KEY_THEME, "dark");
            favoriteTimeframes = props.getProperty(KEY_FAV_TIMEFRAMES, "");
        } catch (IOException e) {
            log.warn("Could not load app settings: {}", e.getMessage());
        }
    }

    // ── API fetch / offline mode ─────────────────────────────

    public boolean isApiFetchEnabled()  { return apiFetchEnabled; }
    public boolean isOfflineMode()      { return !apiFetchEnabled; }

    public synchronized void setApiFetchEnabled(boolean enabled) {
        this.apiFetchEnabled = enabled;
        persist();
    }

    // ── Timezone ─────────────────────────────────────────────

    /** Returns the stored timezone ID, validated and falling back to system default. */
    public ZoneId getUserTimezone() {
        try {
            return ZoneId.of(timezoneId);
        } catch (Exception e) {
            return ZoneId.systemDefault();
        }
    }

    public String getTimezoneId() { return timezoneId; }

    public synchronized void setTimezone(String tzId) {
        try {
            ZoneId.of(tzId); // validate
            this.timezoneId = tzId;
        } catch (Exception e) {
            this.timezoneId = ZoneId.systemDefault().getId();
        }
        persist();
    }

    // ── Default timeframe ────────────────────────────────────

    public String getDefaultTimeframe()  { return defaultTimeframe; }

    public synchronized void setDefaultTimeframe(String tf) {
        this.defaultTimeframe = tf != null ? tf : "1h";
        persist();
    }

    // ── Theme ─────────────────────────────────────────────────

    public String getTheme()            { return theme; }
    public boolean isDarkTheme()        { return !"light".equals(theme); }

    public synchronized void setTheme(String t) {
        this.theme = t != null ? t : "dark";
        persist();
    }

    // ── Favorite timeframes ───────────────────────────────────

    public String getFavoriteTimeframesRaw() { return favoriteTimeframes; }

    public synchronized void setFavoriteTimeframes(String csv) {
        this.favoriteTimeframes = csv != null ? csv : "";
        persist();
    }

    // ── Persistence ───────────────────────────────────────────

    private void persist() {
        try {
            Files.createDirectories(settingsFile.getParent());
            var props = new java.util.Properties();
            props.setProperty(KEY_API_FETCH,      String.valueOf(apiFetchEnabled));
            props.setProperty(KEY_TIMEZONE,       timezoneId);
            props.setProperty(KEY_DEFAULT_TF,     defaultTimeframe);
            props.setProperty(KEY_THEME,          theme);
            props.setProperty(KEY_FAV_TIMEFRAMES, favoriteTimeframes);
            try (OutputStream out = Files.newOutputStream(settingsFile)) {
                props.store(out, "Trading Platform app settings");
            }
        } catch (IOException e) {
            log.warn("Could not save app settings: {}", e.getMessage());
        }
    }
}
