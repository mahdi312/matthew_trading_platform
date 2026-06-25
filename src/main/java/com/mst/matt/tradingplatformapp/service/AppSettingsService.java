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
 * Controls offline mode, timezone override, default timeframe, theme, favorite timeframes,
 * and global drawing tool defaults (color, line width, fill opacity).
 */
@Service
public class AppSettingsService {

    private static final Logger log = LoggerFactory.getLogger(AppSettingsService.class);
    private static final String KEY_API_FETCH         = "api.fetch.enabled";
    private static final String KEY_TIMEZONE          = "user.timezone";
    private static final String KEY_DEFAULT_TF        = "chart.default.timeframe";
    private static final String KEY_THEME             = "ui.theme";
    private static final String KEY_FAV_TIMEFRAMES    = "chart.favorite.timeframes";
    // Global drawing defaults
    private static final String KEY_DRAW_COLOR        = "drawing.default.color";
    private static final String KEY_DRAW_LINE_WIDTH   = "drawing.default.lineWidth";
    private static final String KEY_DRAW_FILL_OPACITY = "drawing.default.fillOpacity";

    private final Path settingsFile =
            Path.of(System.getProperty("user.home"), ".trading-platform", "app-settings.properties");

    private volatile boolean apiFetchEnabled    = true;
    private volatile String  timezoneId         = ZoneId.systemDefault().getId();
    private volatile String  defaultTimeframe   = "1h";
    private volatile String  theme              = "dark";
    private volatile String  favoriteTimeframes = "";
    // Global drawing defaults
    private volatile String  defaultDrawingColor       = "#58a6ff";
    private volatile double  defaultDrawingLineWidth   = 1.5;
    private volatile double  defaultDrawingFillOpacity = 0.12;

    @PostConstruct
    void load() {
        if (!Files.exists(settingsFile)) {
            return;
        }
        var props = new java.util.Properties();
        try (InputStream in = Files.newInputStream(settingsFile)) {
            props.load(in);
            apiFetchEnabled    = Boolean.parseBoolean(props.getProperty(KEY_API_FETCH, "true"));
            timezoneId         = props.getProperty(KEY_TIMEZONE, ZoneId.systemDefault().getId());
            defaultTimeframe   = props.getProperty(KEY_DEFAULT_TF, "1h");
            theme              = props.getProperty(KEY_THEME, "dark");
            favoriteTimeframes = props.getProperty(KEY_FAV_TIMEFRAMES, "");
            // Drawing defaults
            defaultDrawingColor = props.getProperty(KEY_DRAW_COLOR, "#58a6ff");
            try { defaultDrawingLineWidth   = Double.parseDouble(props.getProperty(KEY_DRAW_LINE_WIDTH, "1.5")); }
            catch (NumberFormatException ignored) {}
            try { defaultDrawingFillOpacity = Double.parseDouble(props.getProperty(KEY_DRAW_FILL_OPACITY, "0.12")); }
            catch (NumberFormatException ignored) {}
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

    // ── Global Drawing Defaults ───────────────────────────────

    public String  getDefaultDrawingColor()       { return defaultDrawingColor; }
    public double  getDefaultDrawingLineWidth()   { return defaultDrawingLineWidth; }
    public double  getDefaultDrawingFillOpacity() { return defaultDrawingFillOpacity; }

    public synchronized void setDefaultDrawingColor(String color) {
        this.defaultDrawingColor = (color != null && !color.isBlank()) ? color : "#58a6ff";
        persist();
    }

    public synchronized void setDefaultDrawingLineWidth(double w) {
        this.defaultDrawingLineWidth = Math.max(0.5, Math.min(10, w));
        persist();
    }

    public synchronized void setDefaultDrawingFillOpacity(double opacity) {
        this.defaultDrawingFillOpacity = Math.max(0, Math.min(1, opacity));
        persist();
    }

    // ── Persistence ───────────────────────────────────────────

    private void persist() {
        try {
            Files.createDirectories(settingsFile.getParent());
            var props = new java.util.Properties();
            props.setProperty(KEY_API_FETCH,         String.valueOf(apiFetchEnabled));
            props.setProperty(KEY_TIMEZONE,          timezoneId);
            props.setProperty(KEY_DEFAULT_TF,        defaultTimeframe);
            props.setProperty(KEY_THEME,             theme);
            props.setProperty(KEY_FAV_TIMEFRAMES,    favoriteTimeframes);
            props.setProperty(KEY_DRAW_COLOR,        defaultDrawingColor);
            props.setProperty(KEY_DRAW_LINE_WIDTH,   String.valueOf(defaultDrawingLineWidth));
            props.setProperty(KEY_DRAW_FILL_OPACITY, String.valueOf(defaultDrawingFillOpacity));
            try (OutputStream out = Files.newOutputStream(settingsFile)) {
                props.store(out, "Trading Platform app settings");
            }
        } catch (IOException e) {
            log.warn("Could not save app settings: {}", e.getMessage());
        }
    }
}
