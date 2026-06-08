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

/**
 * User preferences persisted under {@code ~/.trading-platform/app-settings.properties}.
 * Controls offline mode (skip live API fetches while still reading cached DB data).
 */
@Service
public class AppSettingsService {

    private static final Logger log = LoggerFactory.getLogger(AppSettingsService.class);
    private static final String KEY_API_FETCH = "api.fetch.enabled";

    private final Path settingsFile =
            Path.of(System.getProperty("user.home"), ".trading-platform", "app-settings.properties");

    private volatile boolean apiFetchEnabled = true;

    @PostConstruct
    void load() {
        if (!Files.exists(settingsFile)) {
            return;
        }
        var props = new java.util.Properties();
        try (InputStream in = Files.newInputStream(settingsFile)) {
            props.load(in);
            apiFetchEnabled = Boolean.parseBoolean(
                    props.getProperty(KEY_API_FETCH, "true"));
        } catch (IOException e) {
            log.warn("Could not load app settings: {}", e.getMessage());
        }
    }

    public boolean isApiFetchEnabled() {
        return apiFetchEnabled;
    }

    public boolean isOfflineMode() {
        return !apiFetchEnabled;
    }

    public synchronized void setApiFetchEnabled(boolean enabled) {
        this.apiFetchEnabled = enabled;
        persist();
    }

    private void persist() {
        try {
            Files.createDirectories(settingsFile.getParent());
            var props = new java.util.Properties();
            props.setProperty(KEY_API_FETCH, String.valueOf(apiFetchEnabled));
            try (OutputStream out = Files.newOutputStream(settingsFile)) {
                props.store(out, "Trading Platform app settings");
            }
        } catch (IOException e) {
            log.warn("Could not save app settings: {}", e.getMessage());
        }
    }
}
